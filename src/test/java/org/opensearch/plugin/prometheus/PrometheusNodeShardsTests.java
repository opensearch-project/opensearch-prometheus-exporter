/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus;

import org.opensearch.action.admin.cluster.node.stats.NodeStats;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.routing.RoutingNode;
import org.opensearch.cluster.routing.RoutingNodes;
import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.cluster.routing.ShardRoutingState;
import org.opensearch.cluster.routing.TestShardRouting;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.http.HttpStats;
import org.opensearch.plugin.prometheus.action.NodeShardsData;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the shards per node metric. */
public class PrometheusNodeShardsTests extends OpenSearchTestCase {

    public void testShardsAreCountedByType() {
        NodeShardsData data = new NodeShardsData(routingNodes(), List.of("node-a", "node-b", "node-empty"));

        // node-a: 2 started primaries, 1 started replica, 1 primary relocating away to node-b
        assertShards(data, "node-a", 2, 1, 1, 0);
        // node-b: 1 started replica, 1 initializing replica, 1 initializing relocation target
        assertShards(data, "node-b", 0, 1, 0, 2);
        // node-empty holds no shards but is still reported
        assertShards(data, "node-empty", 0, 0, 0, 0);
        // nodes that were not requested are not reported
        assertNull(data.getShards("node-other", NodeShardsData.PRIMARY));
    }

    public void testSerializationRoundTrip() throws IOException {
        NodeShardsData data = new NodeShardsData(routingNodes(), List.of("node-a", "node-b"));
        NodeShardsData copy;
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            data.writeTo(out);
            copy = new NodeShardsData(out.bytes().streamInput());
        }
        for (String node : new String[] {"node-a", "node-b", "node-other"}) {
            for (String type : NodeShardsData.TYPES) {
                assertEquals(node + "/" + type, data.getShards(node, type), copy.getShards(node, type));
            }
        }
    }

    public void testShardsAreExportedForEachNode() throws IOException {
        NodeShardsData data = new NodeShardsData(routingNodes(), List.of("node-a", "node-b"));

        String text = collect(data, node("node-a"), node("node-b"));

        assertSample(text, "node-a", "primary", 2);
        assertSample(text, "node-a", "replica", 1);
        assertSample(text, "node-a", "relocating", 1);
        assertSample(text, "node-a", "initializing", 0);
        assertSample(text, "node-b", "primary", 0);
        assertSample(text, "node-b", "replica", 1);
        assertSample(text, "node-b", "relocating", 0);
        assertSample(text, "node-b", "initializing", 2);
        assertEquals(8, samples(text, "opensearch_node_shards_number{").size());
        assertTrue(text.contains("# TYPE opensearch_node_shards_number gauge"));
    }

    public void testMissingDataIsIgnored() throws IOException {
        assertTrue(samples(collect(null, node("node-a")), "opensearch_node_shards_number{").isEmpty());
    }

    private static RoutingNodes routingNodes() {
        RoutingNode nodeA = routingNode(
                TestShardRouting.newShardRouting("logs", 0, "node-a", true, ShardRoutingState.STARTED),
                TestShardRouting.newShardRouting("logs", 1, "node-a", true, ShardRoutingState.STARTED),
                TestShardRouting.newShardRouting("metrics", 0, "node-a", false, ShardRoutingState.STARTED),
                TestShardRouting.newShardRouting("traces", 0, "node-a", "node-b", true, ShardRoutingState.RELOCATING));
        RoutingNode nodeB = routingNode(
                TestShardRouting.newShardRouting("logs", 0, "node-b", false, ShardRoutingState.STARTED),
                TestShardRouting.newShardRouting("logs", 1, "node-b", false, ShardRoutingState.INITIALIZING),
                TestShardRouting.newShardRouting("traces", 0, "node-a", "node-b", true, ShardRoutingState.RELOCATING)
                        .getTargetRelocatingShard());
        RoutingNode empty = routingNode();
        RoutingNodes routingNodes = mock(RoutingNodes.class);
        when(routingNodes.node("node-a")).thenReturn(nodeA);
        when(routingNodes.node("node-b")).thenReturn(nodeB);
        when(routingNodes.node("node-empty")).thenReturn(empty);
        return routingNodes;
    }

    private static RoutingNode routingNode(ShardRouting... shards) {
        RoutingNode routingNode = mock(RoutingNode.class);
        when(routingNode.iterator()).thenAnswer(invocation -> List.of(shards).iterator());
        return routingNode;
    }

    private static void assertShards(NodeShardsData data, String node,
                                     int primary, int replica, int relocating, int initializing) {
        assertEquals(Integer.valueOf(primary), data.getShards(node, NodeShardsData.PRIMARY));
        assertEquals(Integer.valueOf(replica), data.getShards(node, NodeShardsData.REPLICA));
        assertEquals(Integer.valueOf(relocating), data.getShards(node, NodeShardsData.RELOCATING));
        assertEquals(Integer.valueOf(initializing), data.getShards(node, NodeShardsData.INITIALIZING));
    }

    private static NodeStats node(String id) {
        DiscoveryNode node = mock(DiscoveryNode.class);
        when(node.getName()).thenReturn("name-" + id);
        when(node.getId()).thenReturn(id);
        when(node.getRoles()).thenReturn(Set.of());
        HttpStats http = mock(HttpStats.class);
        NodeStats nodeStats = mock(NodeStats.class);
        when(nodeStats.getNode()).thenReturn(node);
        when(nodeStats.getHttp()).thenReturn(http);
        return nodeStats;
    }

    private static String collect(NodeShardsData data, NodeStats... nodes) throws IOException {
        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), false, false);
        collector.registerMetrics();
        collector.updateMetrics("origin", "origin-id", null, nodes, null, null, data);
        return collector.getTextContent();
    }

    private static Set<String> samples(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).collect(Collectors.toSet());
    }

    private static void assertSample(String text, String nodeId, String type, double expected) {
        Set<String> matches = samples(text, "opensearch_node_shards_number{").stream()
                .filter(line -> line.contains("nodeid=\"" + nodeId + "\""))
                .filter(line -> line.contains("type=\"" + type + "\""))
                .collect(Collectors.toSet());
        assertEquals("Expected one sample for " + nodeId + "/" + type + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0);
    }
}
