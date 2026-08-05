/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.filter;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.opensearch.ExceptionsHelper;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.bulk.BulkAction;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.support.ActionFilter;
import org.opensearch.action.support.ActionFilterChain;
import org.opensearch.action.support.ActionRequestMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.tasks.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * An {@link ActionFilter} that records the latency distribution and outcome of selected transport actions
 * executed on the local node.
 * <p>
 * Unlike the metrics produced by
 * {@link org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector}, which are point-in-time
 * snapshots derived from the node stats APIs, the metrics here are accumulated in-process as requests
 * complete. That allows quantiles to be computed by the Prometheus server, for example:
 * <pre>{@code
 *   histogram_quantile(0.99, sum by (le) (
 *     rate(opensearch_action_latency_seconds_bucket{action="indices:data/read/search"}[5m])))
 * }</pre>
 * <p>
 * The histogram buckets and the request counter are <em>cumulative</em> counters, as the Prometheus data
 * model requires: they are never reset on scrape. Windowing is the query engine's job, and resetting would
 * be indistinguishable from a process restart to Prometheus' counter-reset detection, would lose data on a
 * failed scrape, and would make the result depend on who scraped last.
 * <p>
 * The action name is carried as a label rather than being embedded in the metric name. Action names contain
 * characters that are not legal in Prometheus metric names (for example the {@code /} in
 * {@code indices:data/read/search}), and a label is what makes aggregation across actions possible.
 */
public class PrometheusActionFilter implements ActionFilter {

    /**
     * Actions whose latency and outcome are recorded. Deliberately limited to coordinator-level entry
     * points: matching is by exact action name, so the shard-level sub-actions these fan out to (such as
     * {@code indices:data/write/bulk[s]}) are not tracked.
     */
    private static final Set<String> TRACKED_ACTIONS = Set.of(
            SearchAction.NAME,
            BulkAction.NAME
    );

    private static final String LATENCY_METRIC = "action_latency_seconds";
    private static final String REQUESTS_METRIC = "action_requests_total";

    /**
     * Latency buckets in seconds, spanning sub-millisecond responses up to requests that are effectively
     * hung. Buckets cannot be changed retroactively for already-scraped data, so the range is deliberately
     * wider than the default buckets offered by the Prometheus client.
     */
    private static final double[] LATENCY_BUCKETS =
            { 0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30 };

    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_CLIENT_ERROR = "client_error";
    private static final String OUTCOME_SERVER_ERROR = "server_error";

    private static final List<String> NODE_LABEL_NAMES = List.of("cluster", "node", "nodeid");

    private final CollectorRegistry registry;
    private final Map<String, ActionMetrics> metricsByAction;

    /**
     * A constructor.
     *
     * @param metricPrefix A value used as a prefix for the registered metric names, configured via
     *                     {@link org.opensearch.plugin.prometheus.rest.RestPrometheusMetricsAction#METRIC_PREFIX}
     */
    public PrometheusActionFilter(String metricPrefix) {
        this.registry = new CollectorRegistry();

        Histogram latency = Histogram.build()
                .name(metricPrefix + LATENCY_METRIC)
                .help("Latency of successful transport actions in seconds. Failed requests are counted in "
                        + metricPrefix + REQUESTS_METRIC + " but their latency is not observed here, because "
                        + "requests rejected by a full queue or a tripped circuit breaker fail almost "
                        + "instantly and would otherwise flatter the reported quantiles.")
                .labelNames("action")
                .buckets(LATENCY_BUCKETS)
                .register(registry);

        Counter requests = Counter.build()
                .name(metricPrefix + REQUESTS_METRIC)
                .help("Count of completed transport actions, by outcome.")
                .labelNames("action", "outcome")
                .register(registry);

        // Resolve the child of each metric once per action up front, so that the request path costs an
        // adder increment and a single map lookup rather than a label lookup per metric.
        Map<String, ActionMetrics> byAction = new HashMap<>();
        for (String action : TRACKED_ACTIONS) {
            byAction.put(action, new ActionMetrics(
                    latency.labels(action),
                    requests.labels(action, OUTCOME_SUCCESS),
                    requests.labels(action, OUTCOME_CLIENT_ERROR),
                    requests.labels(action, OUTCOME_SERVER_ERROR)
            ));
        }
        this.metricsByAction = Collections.unmodifiableMap(byAction);
    }

    @Override
    public <Request extends ActionRequest, Response extends ActionResponse> void apply(
            Task task,
            String action,
            Request request,
            ActionRequestMetadata<Request, Response> actionRequestMetadata,
            ActionListener<Response> listener,
            ActionFilterChain<Request, Response> chain) {

        ActionMetrics metrics = metricsByAction.get(action);
        if (metrics == null) {
            chain.proceed(task, action, request, listener);
            return;
        }

        @SuppressWarnings("resource") Histogram.Timer timer = metrics.latency.startTimer();
        ActionListener<Response> recordingListener = new ActionListener<>() {
            @Override
            public void onResponse(Response response) {
                timer.observeDuration();
                metrics.success.inc();
                listener.onResponse(response);
            }

            @Override
            public void onFailure(Exception e) {
                metrics.failure(e).inc();
                listener.onFailure(e);
            }
        };

        chain.proceed(task, action, request, recordingListener);
    }

    @Override
    public int order() {
        // Filters run in ascending order, so a lower value sits further out and therefore measures more of
        // the stack. Zero keeps this filter early without displacing filters that legitimately need to run
        // first, such as those enforcing authorization.
        return 0;
    }

    /**
     * <p>
     * Returns the accumulated action metrics, with the cluster and node identity of the local node applied
     * as labels so that the output is consistent with the rest of the plugin's metrics.
     * <p>
     * These labels are applied at scrape time rather than being baked into the metrics, because the node
     * identity is not reliably known when this filter is constructed.
     *
     * @param clusterName Name of the OpenSearch cluster
     * @param nodeName    Name of the local node
     * @param nodeId      ID of the local node
     * @return Metric samples suitable for rendering with
     *         {@link io.prometheus.client.exporter.common.TextFormat}
     */
    public Enumeration<Collector.MetricFamilySamples> metricFamilySamples(
            String clusterName,
            String nodeName,
            String nodeId) {

        List<String> nodeLabelValues = List.of(clusterName, nodeName, nodeId);
        List<Collector.MetricFamilySamples> labelled = new ArrayList<>();

        for (Collector.MetricFamilySamples family : Collections.list(registry.metricFamilySamples())) {
            List<Collector.MetricFamilySamples.Sample> samples = new ArrayList<>(family.samples.size());
            for (Collector.MetricFamilySamples.Sample sample : family.samples) {
                List<String> labelNames = new ArrayList<>(NODE_LABEL_NAMES);
                labelNames.addAll(sample.labelNames);
                List<String> labelValues = new ArrayList<>(nodeLabelValues);
                labelValues.addAll(sample.labelValues);
                samples.add(new Collector.MetricFamilySamples.Sample(
                        sample.name, labelNames, labelValues, sample.value, sample.exemplar, sample.timestampMs));
            }
            // The family name has to be preserved verbatim: the text format writer derives the names of the
            // OpenMetrics-only samples (_created and friends) from it.
            labelled.add(new Collector.MetricFamilySamples(
                    family.name, family.unit, family.type, family.help, samples));
        }

        return Collections.enumeration(labelled);
    }

    /**
     * The pre-resolved metric children for a single tracked action.
     */
    private static final class ActionMetrics {
        private final Histogram.Child latency;
        private final Counter.Child success;
        private final Counter.Child clientError;
        private final Counter.Child serverError;

        private ActionMetrics(Histogram.Child latency, Counter.Child success,
                              Counter.Child clientError, Counter.Child serverError) {
            this.latency = latency;
            this.success = success;
            this.clientError = clientError;
            this.serverError = serverError;
        }

        private Counter.Child failure(Exception e) {
            return ExceptionsHelper.status(e).getStatusFamilyCode() == 4 ? clientError : serverError;
        }
    }
}
