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
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.network.NetworkAddress;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.plugin.prometheus.action.NodePrometheusMetricsRequest;
import org.opensearch.plugin.prometheus.action.NodePrometheusMetricsResponse;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.plugin.prometheus.collector.PrometheusSettings;
import org.opensearch.plugin.prometheus.filter.PrometheusActionFilter;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestResponse;
import org.opensearch.rest.action.RestResponseListener;
import org.opensearch.transport.client.node.NodeClient;

import io.prometheus.client.exporter.common.TextFormat;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        return channel -> {
            if (prometheusSettings.getPrometheusIndices()) {
                GetMappingsRequest mappingsRequest = new GetMappingsRequest();
                mappingsRequest.indices(prometheusSettings.getPrometheusSelectedIndices());
                mappingsRequest.indicesOptions(prometheusSettings.getIndicesOptions());
                client.admin().indices().getMappings(mappingsRequest, new ActionListener<GetMappingsResponse>() {
                    @Override
                    public void onResponse(GetMappingsResponse response) {
                        executeMetricsRequest(client, channel, getIndexMappingFieldCounts(response));
                    }

                    @Override
                    public void onFailure(Exception e) {
                        logger.warn("Failed to fetch index mappings stats", e);
                        executeMetricsRequest(client, channel, Map.of());
                    }
                });
            } else {
                executeMetricsRequest(client, channel, Map.of());
            }
        };
    }

    private void executeMetricsRequest(NodeClient client, RestChannel channel, Map<String, Long> indexMappingFieldCounts) {
        client.execute(INSTANCE, new NodePrometheusMetricsRequest(),
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
                            PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog(clusterName, metricPrefix);
                            collector = new PrometheusMetricsCollector(
                                    catalog,
                                    prometheusSettings.getPrometheusIndices(),
                                    prometheusSettings.getPrometheusClusterSettings()
                            );
                            collector.registerMetrics();
                            collector.updateMetrics(
                                    nodeName, nodeId, response.getClusterHealth(), response.getNodeStats(),
                                    response.getIndicesStats(), response.getClusterStatsData(), indexMappingFieldCounts);
                            textContent = collector.getTextContent() + actionMetricsTextContent(clusterName, nodeName, nodeId);
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

    @SuppressWarnings("unchecked")
    private Map<String, Long> getIndexMappingFieldCounts(GetMappingsResponse response) {
        Map<String, Long> fieldCounts = new HashMap<>();
        for (Map.Entry<String, MappingMetadata> entry : response.getMappings().entrySet()) {
            try {
                Object properties = entry.getValue().sourceAsMap().get("properties");
                if (properties instanceof Map) {
                    fieldCounts.put(entry.getKey(), countFieldsRecursive((Map<String, Object>) properties));
                } else {
                    fieldCounts.put(entry.getKey(), 0L);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse mappings for index [{}]", entry.getKey(), e);
            }
        }
        return fieldCounts;
    }

    @SuppressWarnings("unchecked")
    private long countFieldsRecursive(Map<String, Object> properties) {
        long count = 0;
        for (Object propertyValue : properties.values()) {
            if (!(propertyValue instanceof Map)) {
                continue;
            }
            Map<String, Object> property = (Map<String, Object>) propertyValue;
            Object type = property.get("type");
            if (type instanceof String && !"object".equals(type)) {
                count++;
                Object fields = property.get("fields");
                if (fields instanceof Map) {
                    for (Object fieldValue : ((Map<String, Object>) fields).values()) {
                        if (fieldValue instanceof Map && ((Map<String, Object>) fieldValue).get("type") instanceof String) {
                            count++;
                        }
                    }
                }
            }
            Object nestedProperties = property.get("properties");
            if (nestedProperties instanceof Map) {
                count += 1 + countFieldsRecursive((Map<String, Object>) nestedProperties);
            }
        }
        return count;
    }

    // The action filter accumulates its metrics across requests, so it keeps its own long-lived registry
    // rather than the per-request one owned by the catalog. Both are rendered in the same text format
    // response; concatenating is safe because the two registries do not share metric names.
    private String actionMetricsTextContent(String clusterName, String nodeName, String nodeId) throws IOException {
        Writer writer = new StringWriter();
        TextFormat.write004(writer, actionFilter.metricFamilySamples(clusterName, nodeName, nodeId));
        return writer.toString();
    }
}
