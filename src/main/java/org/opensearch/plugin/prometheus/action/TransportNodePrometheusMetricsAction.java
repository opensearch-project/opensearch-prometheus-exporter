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

package org.opensearch.plugin.prometheus.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.plugin.prometheus.collector.PrometheusSettings;
import org.opensearch.OpenSearchException;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.opensearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.opensearch.action.admin.cluster.node.stats.NodeStats;
import org.opensearch.action.admin.cluster.node.stats.NodesStatsRequest;
import org.opensearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.opensearch.action.admin.cluster.state.ClusterStateRequest;
import org.opensearch.action.admin.cluster.state.ClusterStateResponse;
import org.opensearch.action.admin.indices.stats.CommonStatsFlags;
import org.opensearch.action.admin.indices.stats.IndicesStatsRequest;
import org.opensearch.action.admin.indices.stats.IndicesStatsResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.Requests;
import org.opensearch.common.Nullable;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import java.util.Arrays;
import java.util.List;

/**
 * Transport action class for Prometheus Exporter plugin.
 *
 * It performs several requests within the cluster to gather "cluster health", "local nodes info", "nodes stats", "indices stats"
 * and "cluster state" (i.e., cluster settings) info. Some of those requests are optional depending on plugin
 * settings.
 */
public class TransportNodePrometheusMetricsAction extends HandledTransportAction<NodePrometheusMetricsRequest,
        NodePrometheusMetricsResponse> {
    private final Client client;
    private final Settings settings;
    private final ClusterSettings clusterSettings;
    private final PrometheusSettings prometheusSettings;
    private final Logger logger = LogManager.getLogger(getClass());

    /**
     * A constructor.
     * @param settings Settings
     * @param client Cluster client
     * @param transportService Transport service
     * @param actionFilters Action filters
     * @param clusterSettings Cluster settings
     */
    @Inject
    public TransportNodePrometheusMetricsAction(Settings settings, Client client,
                                                TransportService transportService, ActionFilters actionFilters,
                                                ClusterSettings clusterSettings) {
        super(NodePrometheusMetricsAction.NAME, transportService, actionFilters,
                NodePrometheusMetricsRequest::new);
        this.client = client;
        this.settings = settings;
        this.clusterSettings = clusterSettings;
        this.prometheusSettings = new PrometheusSettings(settings, clusterSettings);
    }

    @Override
    protected void doExecute(Task task, NodePrometheusMetricsRequest request,
                             ActionListener<NodePrometheusMetricsResponse> listener) {
        new AsyncAction(request, listener).start();
    }

    /**
     * Cluster health computed only to the level the metrics read: per-index metrics need index-level detail, never shards.
     * @param perIndex whether per-index metrics are collected
     * @return the health request
     */
    static ClusterHealthRequest healthRequest(boolean perIndex) {
        ClusterHealthRequest request = Requests.clusterHealthRequest().local(true);
        request.level(perIndex ? ClusterHealthRequest.Level.INDICES : ClusterHealthRequest.Level.CLUSTER);
        // Without this, level() only shapes the REST output and health is computed for every shard.
        request.setApplyLevelAtTransportLayer(true);
        return request;
    }

    private class AsyncAction {

        private final ActionListener<NodePrometheusMetricsResponse> listener;

        private final ClusterHealthRequest healthRequest;
        private final NodesInfoRequest localNodesInfoRequest;
        private final NodesStatsRequest nodesStatsRequest;
        private final IndicesStatsRequest indicesStatsRequest;
        private final ClusterStateRequest clusterStateRequest;

        private ClusterHealthResponse clusterHealthResponse = null;
        private NodesInfoResponse localNodesInfoResponse = null;
        private NodesStatsResponse nodesStatsResponse = null;
        private IndicesStatsResponse indicesStatsResponse = null;
        private ClusterStateResponse clusterStateResponse = null;

        // Read the state of prometheus dynamic settings only once at the beginning of the async request.
        // Request include can only narrow what the settings enable; request indices replaces the configured filter.
        private final boolean isPrometheusIndices;
        private final boolean isPrometheusClusterSettings;
        private final boolean isNodesStats;
        private final boolean isClusterHealth;

        // All the requests are executed in sequential non-blocking order.
        // It is implemented by wrapping each individual request with ActionListener
        // and chaining all of them into a sequence. The last member of the chain call method that gathers
        // all the responses from previous requests and passes them to outer listener (i.e., calling client).
        // Optional requests are skipped.
        //
        // In the future we might consider executing all requests in parallel if needed (CountDownLatch?),
        // however, some requests can impact cluster performance (especially if the cluster is already overloaded),
        // and in this situation it is better to execute all requests in predictable order so that collected metrics
        // stay consistent.
        private AsyncAction(NodePrometheusMetricsRequest request, ActionListener<NodePrometheusMetricsResponse> listener) {
            this.listener = listener;
            this.isPrometheusIndices = prometheusSettings.getPrometheusIndices() && request.includes("indices_stats");
            this.isPrometheusClusterSettings = prometheusSettings.getPrometheusClusterSettings() && request.includes("cluster_settings");
            this.isNodesStats = request.includes("nodes_stats");
            // Per-index metrics need health too, for index status and shard counts.
            this.isClusterHealth = request.includes("cluster_health") || isPrometheusIndices;

            this.healthRequest = healthRequest(isPrometheusIndices);

            // We want to get only the most minimal static info from the local node (cluster name, node name and nodeID).
            this.localNodesInfoRequest = Requests.nodesInfoRequest("_local").clear();

            // Request only the sections the collector reads; all() also computes many it ignores.
            List<String> nodesStatsMetrics = request.nodesStatsMetrics();
            this.nodesStatsRequest = Requests.nodesStatsRequest(prometheusSettings.getNodesFilter()).clear()
                    .indices(nodesStatsMetrics.contains("indices"));
            for (String metric : nodesStatsMetrics) {
                if (!metric.equals("indices")) {
                    nodesStatsRequest.addMetric(metric);
                }
            }

            // Indices stats request is not "node-specific", it does not support any "_local" notion
            // it is broad-casted to all cluster nodes.
            if (isPrometheusIndices) {
                IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
                indicesStatsRequest.indices(request.indices() != null
                        ? request.indices() : prometheusSettings.getPrometheusSelectedIndices());
                indicesStatsRequest.indicesOptions(prometheusSettings.getIndicesOptions());
                if (request.indicesStatsMetrics() != null) {
                    CommonStatsFlags flags = new CommonStatsFlags().clear();
                    for (CommonStatsFlags.Flag flag : CommonStatsFlags.Flag.values()) {
                        flags.set(flag, Arrays.asList(request.indicesStatsMetrics()).contains(flag.getRestName()));
                    }
                    indicesStatsRequest.flags(flags);
                }
                this.indicesStatsRequest = indicesStatsRequest;
            } else {
                this.indicesStatsRequest = null;
            }

            // Cluster settings are pulled from the cluster manager node (hence local=false).
            this.clusterStateRequest = isPrometheusClusterSettings ? Requests.clusterStateRequest()
                    .clear().metadata(true).local(false) : null;
        }

        private void gatherRequests() {
            listener.onResponse(buildResponse(clusterHealthResponse, localNodesInfoResponse, nodesStatsResponse, indicesStatsResponse,
                    clusterStateResponse));
        }

        private final ActionListener<ClusterStateResponse> clusterStateResponseActionListener =
            new ActionListener<ClusterStateResponse>() {
                @Override
                public void onResponse(ClusterStateResponse response) {
                    clusterStateResponse = response;
                    gatherRequests();
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(new OpenSearchException("Cluster state request failed", e));
                }
            };

        private final ActionListener<IndicesStatsResponse> indicesStatsResponseActionListener =
            new ActionListener<IndicesStatsResponse>() {
                @Override
                public void onResponse(IndicesStatsResponse response) {
                    indicesStatsResponse = response;
                    if (isPrometheusClusterSettings) {
                        client.admin().cluster().state(clusterStateRequest, clusterStateResponseActionListener);
                    } else {
                        gatherRequests();
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(new OpenSearchException("Indices stats request failed", e));
                }
            };

        private final ActionListener<NodesStatsResponse> nodesStatsResponseActionListener =
            new ActionListener<NodesStatsResponse>() {
                @Override
                public void onResponse(NodesStatsResponse nodeStats) {
                    nodesStatsResponse = nodeStats;
                    if (isPrometheusIndices) {
                        client.admin().indices().stats(indicesStatsRequest, indicesStatsResponseActionListener);
                    } else {
                        indicesStatsResponseActionListener.onResponse(null);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(new OpenSearchException("Nodes stats request failed", e));
                }
            };

        private final ActionListener<NodesInfoResponse> localNodesInfoResponseActionListener =
            new ActionListener<NodesInfoResponse>() {
                @Override
                public void onResponse(NodesInfoResponse nodesInfoResponse) {
                    localNodesInfoResponse = nodesInfoResponse;
                    if (isNodesStats) {
                        client.admin().cluster().nodesStats(nodesStatsRequest, nodesStatsResponseActionListener);
                    } else {
                        nodesStatsResponseActionListener.onResponse(null);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(new OpenSearchException("Nodes info request failed for local node", e));
                }
            };

        private final ActionListener<ClusterHealthResponse> clusterHealthResponseActionListener =
            new ActionListener<ClusterHealthResponse>() {
                @Override
                public void onResponse(ClusterHealthResponse response) {
                    clusterHealthResponse = response;
                    client.admin().cluster().nodesInfo(localNodesInfoRequest, localNodesInfoResponseActionListener);
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(new OpenSearchException("Cluster health request failed", e));
                }
            };

        private void start() {
            if (isClusterHealth) {
                client.admin().cluster().health(healthRequest, clusterHealthResponseActionListener);
            } else {
                clusterHealthResponseActionListener.onResponse(null);
            }
        }

        protected NodePrometheusMetricsResponse buildResponse(@Nullable ClusterHealthResponse clusterHealth,
                                                              NodesInfoResponse localNodesInfoResponse,
                                                              @Nullable NodesStatsResponse nodesStats,
                                                              @Nullable IndicesStatsResponse indicesStats,
                                                              @Nullable ClusterStateResponse clusterStateResponse) {
            NodePrometheusMetricsResponse response = new NodePrometheusMetricsResponse(
                    clusterHealth,
                    localNodesInfoResponse,
                    nodesStats == null ? new NodeStats[0] : nodesStats.getNodes().toArray(new NodeStats[0]),
                    indicesStats, clusterStateResponse,
                    settings, clusterSettings);
            if (logger.isTraceEnabled()) {
                logger.trace("Return response: [{}]", response);
            }
            return response;
        }
    }
}
