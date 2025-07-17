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

package org.opensearch.action;

import org.opensearch.Version;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.opensearch.action.admin.cluster.node.stats.NodeStats;
import org.opensearch.action.admin.cluster.state.ClusterStateResponse;
import org.opensearch.action.admin.indices.stats.IndicesStatsResponse;
import org.opensearch.action.admin.indices.stats.PackageAccessHelper;
import org.opensearch.common.Nullable;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.search.pipeline.SearchPipelineStats;

import java.io.IOException;

/**
 * Action response class for Prometheus Exporter plugin.
 * This class a container of other responses that are needed to construct list of all required metrics. It knows how to
 * prepare all data for wire transport by writing it into outputStream.
 */
public class NodePrometheusMetricsResponse extends ActionResponse {
    private final ClusterHealthResponse clusterHealth;
    private final NodesInfoResponse nodesInfoResponse;
    private final NodeStats[] nodeStats;
    @Nullable private final IndicesStatsResponse indicesStats;
    private ClusterStatsData clusterStatsData = null;
    @Nullable private final SnapshotsResponse snapshotsResponse;

    /**
     * A constructor that materialize the instance from inputStream.
     * @param in inputStream
     * @throws IOException if there is an exception reading from inputStream
     */
    public NodePrometheusMetricsResponse(StreamInput in) throws IOException {
        super(in);
        clusterHealth = new ClusterHealthResponse(in);
        nodesInfoResponse = new NodesInfoResponse(in);
        nodeStats = in.readArray(NodeStats::new, NodeStats[]::new);
        indicesStats = PackageAccessHelper.createIndicesStatsResponse(in);
        clusterStatsData = new ClusterStatsData(in);
        if (in.getVersion().onOrAfter(Version.V_2_17_1)) {
            snapshotsResponse = new SnapshotsResponse(in);
        } else {
            snapshotsResponse = null;
        }
    }

    /**
     * A constructor.
     * @param clusterHealth ClusterHealthResponse
     * @param localNodesInfoResponse NodesInfoResponse
     * @param nodesStats NodesStats
     * @param indicesStats IndicesStats
     * @param clusterStateResponse ClusterStateResponse
     * @param snapshotsResponse SnapshotsResponse
     * @param settings Settings
     * @param clusterSettings ClusterSettings
     */
    public NodePrometheusMetricsResponse(ClusterHealthResponse clusterHealth,
                                         NodesInfoResponse localNodesInfoResponse,
                                         NodeStats[] nodesStats,
                                         @Nullable IndicesStatsResponse indicesStats,
                                         @Nullable ClusterStateResponse clusterStateResponse,
                                         @Nullable SnapshotsResponse snapshotsResponse,
                                         Settings settings,
                                         ClusterSettings clusterSettings) {
        this.clusterHealth = clusterHealth;
        this.nodesInfoResponse = localNodesInfoResponse;
        this.nodeStats = nodesStats;
        this.indicesStats = indicesStats;
        if (clusterStateResponse != null) {
            this.clusterStatsData = new ClusterStatsData(clusterStateResponse, settings, clusterSettings);
        }
        this.snapshotsResponse = snapshotsResponse;
    }

    /**
     * Get internal {@link ClusterHealthResponse} object.
     * @return ClusterHealthResponse object
     */
    public ClusterHealthResponse getClusterHealth() {
        return this.clusterHealth;
    }

    /**
     * Get internal {@link NodesInfoResponse} object.
     * @return NodesInfoResponse object
     */
    public NodesInfoResponse getLocalNodesInfoResponse() { return this.nodesInfoResponse; }

    /**
     * Get internal {@link NodeStats} array.
     * @return NodeStats array
     */
    public NodeStats[] getNodeStats() {
        return this.nodeStats;
    }

    /**
     * Get internal {@link SnapshotsResponse} object.
     * @return SnapshotsResponse object
     */
    @Nullable
    public SnapshotsResponse getSnapshotsResponse() {
        return this.snapshotsResponse;
    }

    /**
     * Get internal {@link IndicesStatsResponse} object.
     * @return IndicesStatsResponse object
     */
    @Nullable
    public IndicesStatsResponse getIndicesStats() {
        return this.indicesStats;
    }

    /**
     * Get internal {@link ClusterStatsData} object.
     * @return ClusterStatsData object
     */
    @Nullable
    public ClusterStatsData getClusterStatsData() {
        return this.clusterStatsData;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        clusterHealth.writeTo(out);
        nodesInfoResponse.writeTo(out);
        out.writeArray(nodeStats);
        out.writeOptionalWriteable(indicesStats);
        clusterStatsData.writeTo(out);
        if (out.getVersion().onOrAfter(Version.V_2_17_1)) {
            snapshotsResponse.writeTo(out);
        }
    }
}
