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

package org.opensearch.rest.prometheus;

import static org.opensearch.action.NodePrometheusMetricsAction.INSTANCE;
import static org.opensearch.rest.RestRequest.Method.GET;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.compuscene.metrics.prometheus.PrometheusMetricsCatalog;
import org.compuscene.metrics.prometheus.PrometheusMetricsCollector;
import org.compuscene.metrics.prometheus.PrometheusSettings;
import org.opensearch.action.NodePrometheusMetricsRequest;
import org.opensearch.action.NodePrometheusMetricsResponse;
import org.opensearch.transport.client.node.NodeClient;
import org.opensearch.common.network.NetworkAddress;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.*;
import org.opensearch.rest.action.RestResponseListener;

import java.util.List;
import java.util.Locale;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

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
    private final Logger logger = LogManager.getLogger(getClass());

    /**
     * A constructor.
     * @param settings Settings
     * @param clusterSettings Cluster settings
     */
    public RestPrometheusMetricsAction(Settings settings, ClusterSettings clusterSettings) {
        this.prometheusSettings = new PrometheusSettings(settings, clusterSettings);
        this.metricPrefix = METRIC_PREFIX.get(settings);
        if (logger.isTraceEnabled()) {
            logger.trace("Prometheus metric prefix set to [{}]", this.metricPrefix);
        }
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
            new Route(GET, "/_prometheus/metrics"))
        );
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
                        String textContent;
                        try {
//                            PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog(clusterName, nodeName, nodeId, metricPrefix);
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
                            textContent = collector.getTextContent();
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
                        return new BytesRestResponse(RestStatus.OK, textContent);
                    }
                });
    }
}
