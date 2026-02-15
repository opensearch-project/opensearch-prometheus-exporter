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

package org.compuscene.metrics.prometheus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.collect.Tuple;
import org.opensearch.rest.prometheus.RestPrometheusMetricsAction;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Locale;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * A class that describes a Prometheus metrics catalog.
 */
public class PrometheusMetricsCatalog {
    private static final Logger logger = LogManager.getLogger(RestPrometheusMetricsAction.class);

    private String clusterName;
    private String metricPrefix;

    private HashMap<String, Object> metrics;
    private CollectorRegistry registry;

    /**
     *
     * @param clusterName   Name of the OpenSearch cluster
     * @param metricPrefix  A value that is automatically used as a prefix for all registered and set metrics
     */
    public PrometheusMetricsCatalog(String clusterName, String metricPrefix) {
        this.clusterName = clusterName;
        this.metricPrefix = metricPrefix;
        metrics = new HashMap<>();
        registry = new CollectorRegistry();
    }

    private String[] getExtendedClusterLabelNames(String... labelNames) {
        String[] extended = new String[labelNames.length + 1];
        extended[0] = "cluster";

        System.arraycopy(labelNames, 0, extended, 1, labelNames.length);

        return extended;
    }

    private String[] getExtendedClusterLabelValues(String... labelValues) {
        String[] extended = new String[labelValues.length + 1];
        extended[0] = clusterName;

        System.arraycopy(labelValues, 0, extended, 1, labelValues.length);

        return extended;
    }

    private String[] getExtendedNodeLabelNames(String... labelNames) {
        String[] extended = new String[labelNames.length + 3];
        extended[0] = "cluster";
        extended[1] = "node";
        extended[2] = "nodeid";

        System.arraycopy(labelNames, 0, extended, 3, labelNames.length);

        return extended;
    }

    /**
     * @param nodeInfo      A {@link Tuple} holding [nodeName, nodeID]
     * @param labelValues   Prometheus label values
     * @return Prometheus label values extended with cluster and specific node context
     */
    private String[] getExtendedNodeLabelValues(
            Tuple<String, String> nodeInfo,
            String... labelValues) {
        String[] extended = new String[labelValues.length + 3];
        extended[0] = clusterName;
        extended[1] = nodeInfo.v1();
        extended[2] = nodeInfo.v2();

        System.arraycopy(labelValues, 0, extended, 3, labelValues.length);

        return extended;
    }

    /**
     * <p>
     * Register a new metric in the catalog. The metric is registered using the metric name, a help text and optional
     * set of labels. The metric prefix is configured via {@link RestPrometheusMetricsAction#METRIC_PREFIX}.
     * <p>
     * Example:
     * <pre>{@code
     *   // Register new metric for cluster shards:
     *   //   The metric will be called opensearch_cluster_shards_number (the opensearch_ is the default metric prefix),
     *   //   then the help text will be "Number of shards", and finally we expect that the prometheus metric will
     *   //   carry various shard count for different types of shards (initializing, active, ... etc).
     *   catalog.registerClusterGauge("cluster_shards_number", "Number of shards", "type");
     *
     *   // ... later in the code:
     *   private void populateClusterShards(ClusterHealthResponse chr) {
     *     catalog.setClusterGauge("cluster_shards_number", chr.getActiveShards(), "active");
     *     catalog.setClusterGauge("cluster_shards_number", chr.getActivePrimaryShards(), "active_primary");
     *     catalog.setClusterGauge("cluster_shards_number", chr.getDelayedUnassignedShards(), "unassigned");
     *     catalog.setClusterGauge("cluster_shards_number", chr.getInitializingShards(), "initializing");
     *     catalog.setClusterGauge("cluster_shards_number", chr.getRelocatingShards(), "relocating");
     *     catalog.setClusterGauge("cluster_shards_number", chr.getUnassignedShards(), "unassigned");
     *   }
     * }</pre>
     * @param metric Metric name without the metric prefix
     * @param help Help text for the metric
     * @param labels Optional set of labels
     */
    public void registerClusterGauge(String metric, String help, String... labels) {
        Gauge gauge = Gauge.build().
                name(metricPrefix + metric).
                help(help).
                labelNames(getExtendedClusterLabelNames(labels)).
                register(registry);

        metrics.put(metric, gauge);

        logger.debug(String.format(Locale.ENGLISH, "Registered new cluster gauge %s", metric));
    }

    /**
     * Set a value for cluster metric that has been previously registered using {@link #registerClusterGauge(String, String, String...)}.
     * @see #registerClusterGauge(String, String, String...)
     * @param metric Metric name without the metric prefix
     * @param value Value of the metric
     * @param labelValues Optional set of label values
     */
    public void setClusterGauge(String metric, double value, String... labelValues) {
        Gauge gauge = (Gauge) metrics.get(metric);
        gauge.labels(getExtendedClusterLabelValues(labelValues)).set(value);
    }

    /**
     * <p>
     * Register a new metric in the catalog. This is similar to {@link #registerClusterGauge(String, String, String...)}
     * except using this method means that we are registering a metric at the cluster node level.
     * <p>
     * Example:
     * <pre>{@code
     *   // Register new metric for cluster node:
     *   //   The metric will be called opensearch_threadpool_threads_count (the opensearch_ is the default metric prefix),
     *   //   then the help text will be "Number of shards", and finally we expect that the prometheus metric will
     *   //   carry threadpool name and type.
     *   catalog.registerNodeGauge("threadpool_threads_count", "Count of threads in thread pool", "name", "type");
     *
     *   // ... later in the code:
     *   private void updateThreadPoolMetrics(Tuple<String, String> nodeInfo, ThreadPoolStats tps) {
     *     if (tps != null) {
     *       for (ThreadPoolStats.Stats st : tps) {
     *         catalog.setNodeGauge(nodeInfo, "threadpool_threads_count", st.getCompleted(), st.getName(), "completed");
     *         catalog.setNodeGauge(nodeInfo, "threadpool_threads_count", st.getRejected(), st.getName(), "rejected");
     *       }
     *     }
     *   }
     * }</pre>
     * @param metric Metric name without the metric prefix
     * @param help Help text for the metric
     * @param labels Optional set of labels
     */
    public void registerNodeGauge(String metric, String help, String... labels) {
        Gauge gauge = Gauge.build().
                name(metricPrefix + metric).
                help(help).
                labelNames(getExtendedNodeLabelNames(labels)).
                register(registry);

        metrics.put(metric, gauge);

        logger.debug(String.format(Locale.ENGLISH, "Registered new node gauge %s", metric));
    }

    /**
     * Set a value for cluster node metric that has been previously registered using {@link #registerNodeGauge(String, String, String...)}.
     * @see #registerNodeGauge(String, String, String...)
     * @param nodeInfo A {@link Tuple} holding [nodeName, nodeID]
     * @param metric Metric name without the metric prefix
     * @param value Value of the metric
     * @param labelValues Optional set of label values
     */
    public void setNodeGauge(Tuple<String, String> nodeInfo,
                             String metric, double value,
                             String... labelValues) {
        Gauge gauge = (Gauge) metrics.get(metric);
        gauge.labels(getExtendedNodeLabelValues(nodeInfo, labelValues)).set(value);
    }

    /**
     * Registers a new summary metric.
     * @see Summary
     * @param metric Metric name
     * @param help Help text for the metric
     * @param labels Optional set of labels
     */
    public void registerSummaryTimer(String metric, String help, String... labels) {
        Summary summary = Summary.build().
                name(metricPrefix + metric).
                help(help).
                labelNames(getExtendedNodeLabelNames(labels)).
                register(registry);

        metrics.put(metric, summary);

        logger.debug(String.format(Locale.ENGLISH, "Registered new summary %s", metric));
    }

    /**
     * Start specific summary metric.
     * @see Summary
     * @param nodeInfo A {@link Tuple} holding [nodeName, nodeID]
     * @param metric Metric name
     * @param labelValues Optional set of label values
     * @return Summary timer
     */
    public Summary.Timer startSummaryTimer(Tuple<String, String> nodeInfo, String metric,
                                           String... labelValues) {
        Summary summary = (Summary) metrics.get(metric);
        return summary.labels(getExtendedNodeLabelValues(nodeInfo, labelValues)).startTimer();
    }

    /**
     * Returns all the metrics from the catalog formatted in UTF-8 plain/text.
     * More specifically as {@link TextFormat#CONTENT_TYPE_004}.
     * @return Text representation of the metric from the catalog
     * @throws IOException If creating the text representation goes wrong
     */
    public String toTextFormat() throws IOException {
        Writer writer = new StringWriter();
        TextFormat.write004(writer, registry.metricFamilySamples());
        return writer.toString();
    }
}
