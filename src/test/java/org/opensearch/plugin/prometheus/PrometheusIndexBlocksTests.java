/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus;

import org.opensearch.Version;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.admin.cluster.node.stats.NodeStats;
import org.opensearch.action.admin.indices.stats.CommonStats;
import org.opensearch.action.admin.indices.stats.CommonStatsFlags;
import org.opensearch.action.admin.indices.stats.IndexStats;
import org.opensearch.action.admin.indices.stats.IndicesStatsResponse;
import org.opensearch.cluster.block.ClusterBlocks;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.cluster.health.ClusterIndexHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.plugin.prometheus.action.IndexBlocksData;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the index blocks metric. */
public class PrometheusIndexBlocksTests extends OpenSearchTestCase {

    public void testTrackedBlocksAreExtracted() {
        IndexBlocksData data = new IndexBlocksData(ClusterBlocks.builder()
                .addIndexBlock("full", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_READ_ONLY_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_WRITE_BLOCK)
                .addIndexBlock("unreadable", IndexMetadata.INDEX_READ_BLOCK)
                .build());

        assertTrue(data.hasBlock("full", "read_only_allow_delete"));
        assertFalse(data.hasBlock("full", "read_only"));
        assertFalse(data.hasBlock("full", "write"));
        assertTrue(data.hasBlock("archived", "read_only"));
        assertTrue(data.hasBlock("archived", "write"));
        assertFalse(data.hasBlock("archived", "read_only_allow_delete"));
        for (String block : IndexBlocksData.TRACKED_BLOCKS.keySet()) {
            assertFalse(data.hasBlock("unreadable", block));
            assertFalse(data.hasBlock("missing", block));
        }
    }

    public void testSerializationRoundTrip() throws IOException {
        IndexBlocksData data = new IndexBlocksData(ClusterBlocks.builder()
                .addIndexBlock("full", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_READ_ONLY_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_WRITE_BLOCK)
                .build());
        IndexBlocksData copy;
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            data.writeTo(out);
            copy = new IndexBlocksData(out.bytes().streamInput());
        }
        for (String index : new String[] {"full", "archived", "missing"}) {
            for (String block : IndexBlocksData.TRACKED_BLOCKS.keySet()) {
                assertEquals(index + "/" + block, data.hasBlock(index, block), copy.hasBlock(index, block));
            }
        }
    }

    public void testEveryIndexReportsEveryTrackedBlock() throws IOException {
        IndexBlocksData data = new IndexBlocksData(ClusterBlocks.builder()
                .addIndexBlock("full", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .addIndexBlock("not-selected", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .build());

        String text = collect(true, data, "full", "healthy");

        assertSample(text, "full", "read_only_allow_delete", 1);
        assertSample(text, "full", "read_only", 0);
        assertSample(text, "full", "write", 0);
        assertSample(text, "healthy", "read_only_allow_delete", 0);
        assertSample(text, "healthy", "read_only", 0);
        assertSample(text, "healthy", "write", 0);
        // Only indices selected for index level metrics are reported.
        assertEquals(6, samples(text, "opensearch_index_blocks_bool{").size());
        assertTrue(text.contains("# TYPE opensearch_index_blocks_bool gauge"));
    }

    public void testMissingBlocksDataIsIgnored() throws IOException {
        assertTrue(samples(collect(true, null, "full"), "opensearch_index_blocks_bool{").isEmpty());
    }

    public void testBlocksRequireIndexMetrics() throws IOException {
        IndexBlocksData data = new IndexBlocksData(ClusterBlocks.builder()
                .addIndexBlock("full", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .build());
        assertTrue(samples(collect(false, data, "full"), "opensearch_index_blocks_bool{").isEmpty());
    }

    private static String collect(boolean indices, IndexBlocksData data, String... indexNames) throws IOException {
        Map<String, IndexStats> stats = new HashMap<>();
        Map<String, ClusterIndexHealth> health = new HashMap<>();
        for (String name : indexNames) {
            IndexStats indexStats = mock(IndexStats.class);
            when(indexStats.getTotal()).thenReturn(new CommonStats(CommonStatsFlags.ALL));
            when(indexStats.getPrimaries()).thenReturn(new CommonStats(CommonStatsFlags.ALL));
            stats.put(name, indexStats);
            IndexMetadata metadata = IndexMetadata.builder(name)
                    .settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(0).build();
            health.put(name, new ClusterIndexHealth(metadata, IndexRoutingTable.builder(metadata.getIndex()).build()));
        }
        IndicesStatsResponse indicesStats = mock(IndicesStatsResponse.class);
        when(indicesStats.getIndices()).thenReturn(stats);
        ClusterHealthResponse clusterHealth = mock(ClusterHealthResponse.class);
        when(clusterHealth.getIndices()).thenReturn(health);
        when(clusterHealth.getStatus()).thenReturn(ClusterHealthStatus.GREEN);
        when(clusterHealth.getTaskMaxWaitingTime()).thenReturn(TimeValue.ZERO);

        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), indices, false);
        collector.registerMetrics();
        collector.updateMetrics("origin", "origin-id", clusterHealth, new NodeStats[0], indicesStats, null, data);
        return collector.getTextContent();
    }

    private static Set<String> samples(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).collect(Collectors.toSet());
    }

    private static void assertSample(String text, String index, String block, double expected) {
        Set<String> matches = samples(text, "opensearch_index_blocks_bool{").stream()
                .filter(line -> line.contains("index=\"" + index + "\""))
                .filter(line -> line.contains("block=\"" + block + "\""))
                .collect(Collectors.toSet());
        assertEquals("Expected one sample for " + index + "/" + block + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0);
    }
}
