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
import org.opensearch.action.admin.indices.stats.CommonStats;
import org.opensearch.action.admin.indices.stats.IndicesStatsResponse;
import org.opensearch.action.admin.indices.stats.ShardStats;
import org.opensearch.cluster.routing.ShardRoutingState;
import org.opensearch.cluster.routing.TestShardRouting;
import org.opensearch.index.shard.DocsStats;
import org.opensearch.index.store.StoreStats;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the shard level metrics exported from the indices stats response. */
public class PrometheusShardMetricsTests extends OpenSearchTestCase {

    public void testShardCopiesAreExportedSeparately() throws IOException {
        IndicesStatsResponse response = response(
                shard("logs", 0, "node-a", true, 1_500_000_000L, 10L, 4096L),
                shard("logs", 0, "node-b", false, 1_500_000_001L, 11L, 4097L),
                shard("logs", 1, "node-b", true, 20L, 0L, 128L));

        String text = collect(true, true, response);

        assertSample(text, "doc_number", 1_500_000_000L, "logs", "0", "true", "node-a");
        assertSample(text, "doc_number", 1_500_000_001L, "logs", "0", "false", "node-b");
        assertSample(text, "doc_number", 20L, "logs", "1", "true", "node-b");
        assertSample(text, "doc_deleted_number", 10L, "logs", "0", "true", "node-a");
        assertSample(text, "doc_deleted_number", 11L, "logs", "0", "false", "node-b");
        assertSample(text, "doc_deleted_number", 0L, "logs", "1", "true", "node-b");
        assertSample(text, "store_size_bytes", 4096L, "logs", "0", "true", "node-a");
        assertSample(text, "store_size_bytes", 4097L, "logs", "0", "false", "node-b");
        assertSample(text, "store_size_bytes", 128L, "logs", "1", "true", "node-b");
        for (String metric : new String[] {"doc_number", "doc_deleted_number", "store_size_bytes"}) {
            assertEquals(3, samples(text, "opensearch_index_shard_" + metric + "{").size());
            assertTrue(text.contains("# TYPE opensearch_index_shard_" + metric + " gauge"));
        }
    }

    public void testShardMetricsAreDisabledByDefault() throws IOException {
        IndicesStatsResponse response = response(shard("logs", 0, "node-a", true, 5L, 0L, 64L));
        assertTrue(samples(collect(true, false, response), "opensearch_index_shard_").isEmpty());
    }

    public void testShardMetricsRequireIndexMetrics() throws IOException {
        IndicesStatsResponse response = response(shard("logs", 0, "node-a", true, 5L, 0L, 64L));
        assertTrue(samples(collect(false, true, response), "opensearch_index_shard_").isEmpty());
    }

    public void testMissingStatsAreOmitted() throws IOException {
        ShardStats noStats = mock(ShardStats.class);
        when(noStats.getShardRouting()).thenReturn(
                TestShardRouting.newShardRouting("logs", 0, "node-a", true, ShardRoutingState.STARTED));
        ShardStats docsOnly = shard("logs", 1, "node-a", true, 5L, 1L, 64L);
        docsOnly.getStats().store = null;

        String text = collect(true, true, response(noStats, docsOnly));

        assertEquals(1, samples(text, "opensearch_index_shard_doc_number{").size());
        assertEquals(1, samples(text, "opensearch_index_shard_doc_deleted_number{").size());
        assertTrue(samples(text, "opensearch_index_shard_store_size_bytes{").isEmpty());
        assertSample(text, "doc_number", 5L, "logs", "1", "true", "node-a");
    }

    public void testNullIndicesStatsIsIgnored() throws IOException {
        assertTrue(samples(collect(true, true, null), "opensearch_index_shard_").isEmpty());
    }

    private static ShardStats shard(String index, int id, String nodeId, boolean primary,
                                    long docs, long deleted, long storeBytes) {
        CommonStats stats = new CommonStats();
        stats.docs = new DocsStats.Builder().count(docs).deleted(deleted).totalSizeInBytes(storeBytes).build();
        stats.store = new StoreStats.Builder().sizeInBytes(storeBytes).build();
        ShardStats shardStats = mock(ShardStats.class);
        when(shardStats.getShardRouting()).thenReturn(
                TestShardRouting.newShardRouting(index, id, nodeId, primary, ShardRoutingState.STARTED));
        when(shardStats.getStats()).thenReturn(stats);
        return shardStats;
    }

    private static IndicesStatsResponse response(ShardStats... shards) {
        IndicesStatsResponse response = mock(IndicesStatsResponse.class);
        when(response.getShards()).thenReturn(shards);
        when(response.getIndices()).thenReturn(Map.of());
        return response;
    }

    private static String collect(boolean indices, boolean shards, IndicesStatsResponse response) throws IOException {
        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), indices, shards, false);
        collector.registerMetrics();
        collector.updateMetrics("origin", "origin-id", null, new NodeStats[0], response, null);
        return collector.getTextContent();
    }

    private static Set<String> samples(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).collect(Collectors.toSet());
    }

    private static void assertSample(String text, String metric, long expected,
                                     String index, String shard, String primary, String nodeId) {
        Set<String> matches = samples(text, "opensearch_index_shard_" + metric + "{").stream()
                .filter(line -> line.contains("index=\"" + index + "\""))
                .filter(line -> line.contains("shard=\"" + shard + "\""))
                .filter(line -> line.contains("primary=\"" + primary + "\""))
                .filter(line -> line.contains("nodeid=\"" + nodeId + "\""))
                .collect(Collectors.toSet());
        assertEquals("Expected one sample for " + metric + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, (double) expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0);
    }
}
