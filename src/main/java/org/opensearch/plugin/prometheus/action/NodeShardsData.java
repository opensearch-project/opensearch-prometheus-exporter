/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.action;

import org.opensearch.cluster.routing.RoutingNode;
import org.opensearch.cluster.routing.RoutingNodes;
import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Number of shard copies allocated to each node, extracted from the routing table of the cluster state.
 * The shards of a node are split into the disjoint {@link #TYPES}, so their sum is the total number of
 * shard copies held by the node.
 */
public class NodeShardsData implements Writeable {

    /** Started primary shards. */
    public static final String PRIMARY = "primary";
    /** Started replica shards. */
    public static final String REPLICA = "replica";
    /** Shards being relocated away from the node. */
    public static final String RELOCATING = "relocating";
    /** Shards being initialized on the node, including the targets of relocations. */
    public static final String INITIALIZING = "initializing";

    /** All shard types, used as the metric label values. */
    public static final List<String> TYPES = List.of(PRIMARY, REPLICA, RELOCATING, INITIALIZING);

    // node ID -> shard type -> number of shards
    private final Map<String, Map<String, Integer>> shardsByNode;

    /**
     * Counts the shards of the given nodes.
     * @param routingNodes {@link RoutingNodes} of the cluster state
     * @param nodeIds IDs of the nodes to count the shards of
     */
    public NodeShardsData(RoutingNodes routingNodes, Iterable<String> nodeIds) {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        for (String nodeId : nodeIds) {
            Map<String, Integer> counts = new HashMap<>();
            for (String type : TYPES) {
                counts.put(type, 0);
            }
            RoutingNode routingNode = routingNodes.node(nodeId);
            if (routingNode != null) {
                for (ShardRouting shard : routingNode) {
                    counts.merge(type(shard), 1, Integer::sum);
                }
            }
            result.put(nodeId, counts);
        }
        this.shardsByNode = result;
    }

    /**
     * A constructor that materializes the instance from {@link StreamInput}.
     * @param in A {@link StreamInput} to materialize the instance from
     * @throws IOException if reading from {@link StreamInput} is not successful
     */
    public NodeShardsData(StreamInput in) throws IOException {
        this.shardsByNode = in.readMap(StreamInput::readString, i -> i.readMap(StreamInput::readString, StreamInput::readVInt));
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(shardsByNode, StreamOutput::writeString,
                (o, counts) -> o.writeMap(counts, StreamOutput::writeString, StreamOutput::writeVInt));
    }

    /**
     * Number of shards of the given type on the node.
     * @param nodeId node ID
     * @param type shard type, one of {@link #TYPES}
     * @return number of shards, or null if the node was not counted
     */
    public Integer getShards(String nodeId, String type) {
        Map<String, Integer> counts = shardsByNode.get(nodeId);
        return counts == null ? null : counts.get(type);
    }

    private static String type(ShardRouting shard) {
        if (shard.relocating()) {
            return RELOCATING;
        }
        if (shard.initializing()) {
            return INITIALIZING;
        }
        return shard.primary() ? PRIMARY : REPLICA;
    }
}
