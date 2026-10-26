/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Copyright [2016] [Vincent VAN HOLLEBEKE]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.plugin.prometheus.rest;

import static org.opensearch.plugin.prometheus.action.NodePrometheusMetricsAction.INSTANCE;
import static org.opensearch.rest.RestRequest.Method.GET;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.plugin.prometheus.collector.PrometheusSettings;
import org.opensearch.plugin.prometheus.action.NodePrometheusMetricsRequest;
import org.opensearch.plugin.prometheus.action.NodePrometheusMetricsResponse;
import org.opensearch.plugin.prometheus.filter.PrometheusActionFilter;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestResponse;
import org.opensearch.transport.client.node.NodeClient;
import org.opensearch.common.network.NetworkAddress;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.action.RestResponseListener;

import java.util.List;
import java.util.Locale;

/**
 * REST action class for Prometheus Exporter plugin.
 */
public class RestPrometheusMetricsAction extends BaseRestHandler {

    static String METRIC_PREFIX_KEY = "prometheus.metric_name.prefix";
    static Setting.Validator<String> indexPrefixValidator = value -> {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(
                METRIC_PREFIX_KEY + " value ["+value+"] is not valid"
            );
        }
    };

    /**
     * A metric prefix. Can be configured in opensearch.yml file under key {@link #METRIC_PREFIX_KEY}.
     */
    public static final Setting<String> METRIC_PREFIX = Setting.simpleString(METRIC_PREFIX_KEY, "opensearch_", indexPrefixValidator, Setting.Property.NodeScope);


    private final String metricPrefix;
    private final PrometheusSettings prometheusSettings;
    private final PrometheusActionFilter actionFilter;
    private final Logger logger = LogManager.getLogger(getClass());

    /**
     * A constructor.
     * @param settings Settings
     * @param clusterSettings Cluster settings
     * @param actionFilter The filter holding the latency metrics accumulated on this node
     */
    public RestPrometheusMetricsAction(Settings settings, ClusterSettings clusterSettings,
                                       PrometheusActionFilter actionFilter) {
        this.prometheusSettings = new PrometheusSettings(settings, clusterSettings);
        this.metricPrefix = METRIC_PREFIX.get(settings);
        this.actionFilter = actionFilter;
        if (logger.isTraceEnabled()) {
            logger.trace("Prometheus metric prefix set to [{}]", this.metricPrefix);
        }
    }

    @Override
    public List<Route> routes() {
        return List.of(
                new Route(GET, "/_prometheus/metrics"));
    }

    @Override
    public String getName() {
        return "prometheus_metrics_action";
    }

     // This method does not throw any IOException because there are no request parameters to be parsed
     // and processed. This may change in the future.
    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        if (logger.isTraceEnabled()) {
            String remoteAddress = NetworkAddress.format(request.getHttpChannel().getRemoteAddress());
            logger.trace(String.format(Locale.ENGLISH, "Received request for Prometheus metrics from %s",
                    remoteAddress));
        }

        NodePrometheusMetricsRequest metricsRequest = new NodePrometheusMetricsRequest();

        return channel -> client.execute(INSTANCE, metricsRequest,
                new RestResponseListener<NodePrometheusMetricsResponse>(channel) {

                    @Override
                    public RestResponse buildResponse(NodePrometheusMetricsResponse response) throws Exception {

                        String clusterName = response.getLocalNodesInfoResponse().getClusterName().value();
                        assert response.getLocalNodesInfoResponse().getNodes().size() == 1;
                        String nodeName = response.getLocalNodesInfoResponse().getNodes().get(0).getNode().getName();
                        String nodeId = response.getLocalNodesInfoResponse().getNodes().get(0).getNode().getId();

                        if (logger.isTraceEnabled()) {
                            logger.trace("Preparing metrics output on node: [{}], [{}]", nodeName, nodeId);
                        }
                        PrometheusMetricsCollector collector;
                        BytesReference content;
                        try {
                            PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog(clusterName, metricPrefix);
                            collector = new PrometheusMetricsCollector(
                                    catalog,
                                    prometheusSettings.getPrometheusIndices(),
                                    prometheusSettings.getPrometheusClusterSettings()
                            );
                            collector.registerMetrics();
                            collector.updateMetrics(
                                    nodeName, nodeId, response.getClusterHealth(), response.getNodeStats(),
                                    response.getIndicesStats(), response.getClusterStatsData());
                            // The action filter accumulates its metrics across requests, so it keeps its own long-lived
                            // registry rather than the per-request one owned by the catalog. Registering a view of it
                            // with the catalog renders both as one document, and fails if they share a metric name.
                            catalog.registerCollector(actionFilter.collector(clusterName, nodeName, nodeId));
                            content = collector.getTextContentBytes();
                        } catch (Exception ex) {
                            // We use try-catch block to catch exception from Prometheus catalog and collector processing
                            // and dump it into the log, otherwise client needs to know how to configure logging to output
                            // exceptions that are thrown from
                            // "RestResponseListener.buildResponse(Response response) throws Exception".
                            // This is useful when metric_prefix value is not valid or when metric collector fails
                            // generating text content. We may be able to get rid of this try-catch pattern
                            // once we implement robust verification of custom metric prefix.
                            // See https://github.com/opensearch-project/opensearch-prometheus-exporter/issues/11
                            logger.debug("Prometheus metric catalog processing failed", ex);
                            throw ex;
                        }
                        // Prometheus' metrics are exposed similarly the Pushgateway example except no real gateway
                        // is used and the metrics are exposed directly via OpenSearch HTTP API instead.
                        // See https://github.com/prometheus/client_java#exporting-to-a-pushgateway
                        // The transport wraps each page of the content in a Netty composite buffer and writes it out a
                        // slice at a time, so the document is not joined into one contiguous array. Except when the
                        // client accepts gzip, as Prometheus does by default: Netty's compressor then writes its
                        // output, a sixth to a seventh of the document's size, into a single growing buffer.
                        return new BytesRestResponse(RestStatus.OK, BytesRestResponse.TEXT_CONTENT_TYPE, content);
                    }
                });
    }
}
