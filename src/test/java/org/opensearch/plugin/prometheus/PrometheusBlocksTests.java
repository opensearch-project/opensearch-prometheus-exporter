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
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.plugin.prometheus.action.BlocksData;
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

/** Tests the cluster and index blocks metrics. */
public class PrometheusBlocksTests extends OpenSearchTestCase {

    private static final String[] INDEX_BLOCKS = {"read_only_allow_delete", "read_only", "write", "read", "metadata"};
    private static final String[] CLUSTER_BLOCKS = {"read_only", "read_only_allow_delete", "create_index"};

    public void testTrackedBlockNames() {
        assertArrayEquals(INDEX_BLOCKS, BlocksData.TRACKED_INDEX_BLOCKS.keySet().toArray(new String[0]));
        assertArrayEquals(CLUSTER_BLOCKS, BlocksData.TRACKED_CLUSTER_BLOCKS.keySet().toArray(new String[0]));
    }

    public void testIndexBlocksAreExtracted() {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addIndexBlock("full", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_READ_ONLY_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_METADATA_BLOCK)
                .addIndexBlock("frozen", IndexMetadata.INDEX_WRITE_BLOCK)
                .addIndexBlock("hidden", IndexMetadata.INDEX_READ_BLOCK)
                .addIndexBlock("scaled-down", IndexMetadata.INDEX_SEARCH_ONLY_BLOCK)
                .build());

        assertIndexBlocks(data, "full", "read_only_allow_delete");
        assertIndexBlocks(data, "archived", "read_only", "metadata");
        assertIndexBlocks(data, "frozen", "write");
        assertIndexBlocks(data, "hidden", "read");
        // Untracked blocks and indices without blocks report nothing.
        assertIndexBlocks(data, "scaled-down");
        assertIndexBlocks(data, "missing");
        for (String block : CLUSTER_BLOCKS) {
            assertFalse(data.hasClusterBlock(block));
        }
    }

    public void testClusterBlocksAreExtracted() {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addGlobalBlock(Metadata.CLUSTER_READ_ONLY_ALLOW_DELETE_BLOCK)
                .addGlobalBlock(Metadata.CLUSTER_CREATE_INDEX_BLOCK)
                .build());

        assertFalse(data.hasClusterBlock("read_only"));
        assertTrue(data.hasClusterBlock("read_only_allow_delete"));
        assertTrue(data.hasClusterBlock("create_index"));
        // Cluster blocks are not reported as index blocks.
        assertIndexBlocks(data, "any");
    }

    public void testSerializationRoundTrip() throws IOException {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addGlobalBlock(Metadata.CLUSTER_READ_ONLY_BLOCK)
                .addIndexBlock("full", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_READ_ONLY_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_METADATA_BLOCK)
                .build());
        BlocksData copy;
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            data.writeTo(out);
            copy = new BlocksData(out.bytes().streamInput());
        }
        for (String block : CLUSTER_BLOCKS) {
            assertEquals(block, data.hasClusterBlock(block), copy.hasClusterBlock(block));
        }
        for (String index : new String[] {"full", "archived", "missing"}) {
            for (String block : INDEX_BLOCKS) {
                assertEquals(index + "/" + block, data.hasIndexBlock(index, block), copy.hasIndexBlock(index, block));
            }
        }
    }

    public void testEveryIndexReportsEveryTrackedBlock() throws IOException {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addIndexBlock("full", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_READ_ONLY_BLOCK)
                .addIndexBlock("archived", IndexMetadata.INDEX_METADATA_BLOCK)
                .addIndexBlock("not-selected", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .build());

        String text = collect(true, data, "full", "archived", "healthy");

        assertIndexSamples(text, "full", "read_only_allow_delete");
        assertIndexSamples(text, "archived", "read_only", "metadata");
        assertIndexSamples(text, "healthy");
        // Only indices selected for index level metrics are reported.
        assertEquals(3 * INDEX_BLOCKS.length, samples(text, "opensearch_index_blocks_bool{").size());
        assertTrue(text.contains("# TYPE opensearch_index_blocks_bool gauge"));
    }

    public void testClusterBlocksAreReported() throws IOException {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addGlobalBlock(Metadata.CLUSTER_READ_ONLY_BLOCK)
                .build());

        String text = collect(true, data);

        assertClusterSample(text, "read_only", 1);
        assertClusterSample(text, "read_only_allow_delete", 0);
        assertClusterSample(text, "create_index", 0);
        assertEquals(CLUSTER_BLOCKS.length, samples(text, "opensearch_cluster_blocks_bool{").size());
        assertTrue(text.contains("# TYPE opensearch_cluster_blocks_bool gauge"));
    }

    public void testClusterBlocksDoNotRequireIndexMetrics() throws IOException {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addGlobalBlock(Metadata.CLUSTER_READ_ONLY_ALLOW_DELETE_BLOCK)
                .addIndexBlock("full", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK)
                .build());

        String text = collect(false, data, "full");

        assertClusterSample(text, "read_only_allow_delete", 1);
        assertTrue(samples(text, "opensearch_index_blocks_bool{").isEmpty());
    }

    public void testIndicesWithoutStatsStillReportBlocks() throws IOException {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addIndexBlock("locked", IndexMetadata.INDEX_METADATA_BLOCK)
                .build(), Set.of("locked"));

        String text = collect(true, data, "healthy");

        assertIndexSamples(text, "locked", "metadata");
        assertIndexSamples(text, "healthy");
        // The skipped index has no other index level metrics.
        assertTrue(samples(text, "opensearch_index_status{").stream().noneMatch(line -> line.contains("index=\"locked\"")));
        assertEquals(1, samples(text, "opensearch_index_status{").size());
    }

    public void testIndicesWithoutStatsWhenAllIndicesAreSkipped() throws IOException {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addIndexBlock("locked", IndexMetadata.INDEX_METADATA_BLOCK)
                .build(), Set.of("locked"));

        String text = collectWithoutIndicesStats(data);

        assertIndexSamples(text, "locked", "metadata");
        assertTrue(samples(text, "opensearch_index_status{").isEmpty());
    }

    public void testIndicesWithoutStatsRequireIndexMetrics() throws IOException {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addIndexBlock("locked", IndexMetadata.INDEX_METADATA_BLOCK)
                .build(), Set.of("locked"));

        assertTrue(samples(collect(false, data), "opensearch_index_blocks_bool{").isEmpty());
    }

    public void testIndicesWithoutStatsSerialization() throws IOException {
        BlocksData data = new BlocksData(ClusterBlocks.builder()
                .addIndexBlock("locked", IndexMetadata.INDEX_METADATA_BLOCK)
                .build(), Set.of("locked"));
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            data.writeTo(out);
            assertEquals(Set.of("locked"), new BlocksData(out.bytes().streamInput()).getIndicesWithoutStats());
        }
        assertTrue(new BlocksData(ClusterBlocks.builder().build()).getIndicesWithoutStats().isEmpty());
    }

    public void testMissingBlocksDataIsIgnored() throws IOException {
        String text = collect(true, null, "full");
        assertTrue(samples(text, "opensearch_index_blocks_bool{").isEmpty());
        assertTrue(samples(text, "opensearch_cluster_blocks_bool{").isEmpty());
    }

    private static void assertIndexBlocks(BlocksData data, String index, String... expected) {
        Set<String> set = Set.of(expected);
        for (String block : INDEX_BLOCKS) {
            assertEquals(index + "/" + block, set.contains(block), data.hasIndexBlock(index, block));
        }
    }

    private static String collect(boolean indices, BlocksData data, String... indexNames) throws IOException {
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

    private static String collectWithoutIndicesStats(BlocksData data) throws IOException {
        ClusterHealthResponse clusterHealth = mock(ClusterHealthResponse.class);
        when(clusterHealth.getIndices()).thenReturn(Map.of());
        when(clusterHealth.getStatus()).thenReturn(ClusterHealthStatus.GREEN);
        when(clusterHealth.getTaskMaxWaitingTime()).thenReturn(TimeValue.ZERO);
        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), true, false);
        collector.registerMetrics();
        collector.updateMetrics("origin", "origin-id", clusterHealth, new NodeStats[0], null, null, data);
        return collector.getTextContent();
    }

    private static Set<String> samples(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).collect(Collectors.toSet());
    }

    private static void assertIndexSamples(String text, String index, String... expected) {
        Set<String> set = Set.of(expected);
        for (String block : INDEX_BLOCKS) {
            assertSample(text, "opensearch_index_blocks_bool{", set.contains(block) ? 1 : 0,
                    "index=\"" + index + "\"", "block=\"" + block + "\"");
        }
    }

    private static void assertClusterSample(String text, String block, double expected) {
        assertSample(text, "opensearch_cluster_blocks_bool{", expected, "block=\"" + block + "\"");
    }

    private static void assertSample(String text, String prefix, double expected, String... labels) {
        Set<String> matches = samples(text, prefix).stream()
                .filter(line -> {
                    for (String label : labels) {
                        if (line.contains(label) == false) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toSet());
        assertEquals("Expected one sample for " + String.join(",", labels) + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0);
    }
}
