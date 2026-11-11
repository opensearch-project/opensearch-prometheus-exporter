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
import org.opensearch.http.HttpStats;
import org.opensearch.index.stats.IndexingPressureStats;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests real OpenSearch indexing pressure stats through the public collector entry point. */
public class PrometheusIndexingPressureTests extends OpenSearchTestCase {

    private static final String PREFIX = "opensearch_indexing_pressure_memory_";

    public void testCurrentBytes() throws IOException {
        String text = collect(node("node-1", stats(1L)));
        assertSample(text, "current_bytes", 20, "stage", "coordinating");
        assertSample(text, "current_bytes", 30, "stage", "primary");
        assertSample(text, "current_bytes", 40, "stage", "replica");
        assertSample(text, "current_bytes", 10, "stage", "combined_coordinating_and_primary");
        assertSample(text, "current_bytes", 50, "stage", "all");
        assertEquals(5, samples(text, PREFIX + "current_bytes{").size());
    }

    public void testTotalBytes() throws IOException {
        String text = collect(node("node-1", stats(1L)));
        assertSample(text, "total_bytes", 2000, "stage", "coordinating");
        assertSample(text, "total_bytes", 3000, "stage", "primary");
        assertSample(text, "total_bytes", 4000, "stage", "replica");
        assertSample(text, "total_bytes", 1000, "stage", "combined_coordinating_and_primary");
        assertSample(text, "total_bytes", 5000, "stage", "all");
        assertEquals(5, samples(text, PREFIX + "total_bytes{").size());
    }

    public void testRejections() throws IOException {
        String text = collect(node("node-1", stats(1L)));
        assertSample(text, "rejections_count", 5, "stage", "coordinating");
        assertSample(text, "rejections_count", 6, "stage", "primary");
        assertSample(text, "rejections_count", 7, "stage", "replica");
        assertEquals(3, samples(text, PREFIX + "rejections_count{").size());
    }

    public void testLimit() throws IOException {
        long limit = 858_993_459L;
        String text = collect(node("node-1", stats(limit)));
        assertSample(text, "limit_bytes", limit);
        assertEquals(1, samples(text, PREFIX + "limit_bytes{").size());
    }

    public void testAbsentStatsAreOmitted() throws IOException {
        String text = collect(node("node-1", null));
        assertTrue(samples(text, PREFIX).isEmpty());
        assertFalse(samples(text, "opensearch_http_open_server_number").isEmpty());
    }

    public void testMetricsKeepNodeIdentity() throws IOException {
        String text = collect(node("node-1", stats(100L)), node("node-2", stats(200L)));
        assertSample(text, "limit_bytes", 100, "nodeid", "node-1");
        assertSample(text, "limit_bytes", 200, "nodeid", "node-2");
        for (String sample : samples(text, PREFIX)) {
            assertTrue(sample, sample.contains("cluster=\"test-cluster\""));
            assertTrue(sample, sample.contains("node=\"test-node-"));
        }
    }

    public void testExistingMetricValuesAreUnchanged() throws IOException {
        String before = collect(node("node-1", null));
        String after = collect(node("node-1", stats(1L)));
        Set<String> expected = existingSamples(before);
        assertFalse(expected.isEmpty());
        assertEquals(expected, existingSamples(after));
    }

    private static IndexingPressureStats stats(long limit) {
        return new IndexingPressureStats.Builder()
                .currentCombinedCoordinatingAndPrimaryBytes(10)
                .currentCoordinatingBytes(20)
                .currentPrimaryBytes(30)
                .currentReplicaBytes(40)
                .totalCombinedCoordinatingAndPrimaryBytes(1000)
                .totalCoordinatingBytes(2000)
                .totalPrimaryBytes(3000)
                .totalReplicaBytes(4000)
                .coordinatingRejections(5)
                .primaryRejections(6)
                .replicaRejections(7)
                .memoryLimit(limit)
                .build();
    }

    private static NodeStats node(String id, IndexingPressureStats stats) {
        DiscoveryNode node = mock(DiscoveryNode.class);
        when(node.getName()).thenReturn("test-" + id);
        when(node.getId()).thenReturn(id);
        when(node.getRoles()).thenReturn(Set.of());
        HttpStats http = mock(HttpStats.class);
        when(http.getServerOpen()).thenReturn(2L);
        when(http.getTotalOpen()).thenReturn(10L);
        NodeStats nodeStats = mock(NodeStats.class);
        when(nodeStats.getNode()).thenReturn(node);
        when(nodeStats.getHttp()).thenReturn(http);
        when(nodeStats.getIndexingPressureStats()).thenReturn(stats);
        return nodeStats;
    }

    private static String collect(NodeStats... nodes) throws IOException {
        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), false, false);
        collector.registerMetrics();
        collector.updateMetrics("origin", "origin-id", null, nodes, null, null);
        return collector.getTextContent();
    }

    private static Set<String> samples(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).collect(Collectors.toSet());
    }

    private static Set<String> existingSamples(String text) {
        return samples(text, "opensearch_").stream()
                .filter(line -> !line.startsWith(PREFIX))
                .filter(line -> !line.startsWith("opensearch_metrics_generate_time_seconds"))
                .collect(Collectors.toSet());
    }

    private static void assertSample(String text, String metric, double expected, String... labels) {
        Set<String> matches = samples(text, PREFIX + metric + "{").stream()
                .filter(line -> matchesLabels(line, labels)).collect(Collectors.toSet());
        assertEquals("Expected one sample for " + metric + " " + Arrays.toString(labels) + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0);
        assertTrue(text.contains("# TYPE " + PREFIX + metric + " gauge"));
    }

    private static boolean matchesLabels(String sample, String... labels) {
        for (int i = 0; i < labels.length; i += 2) {
            if (!sample.contains(labels[i] + "=\"" + labels[i + 1] + "\"")) {
                return false;
            }
        }
        return true;
    }
}
