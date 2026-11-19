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
import org.opensearch.cluster.routing.WeightedRoutingStats;
import org.opensearch.http.HttpStats;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests weighted-routing node stats through the public collector entry point. */
public class PrometheusWeightedRoutingTests extends OpenSearchTestCase {
    private static final String METRIC = "opensearch_weighted_routing_fail_open_count";

    public void testExportsFailOpenCountAsGauge() throws IOException {
        String text = collect(node("node-1", stats(7)));

        assertSample(text, 7);
        assertTrue(text.contains("# HELP " + METRIC + " Number of times weighted routing failed open for search requests"));
        assertTrue(text.contains("# TYPE " + METRIC + " gauge"));
    }

    public void testPreservesNodeLabels() throws IOException {
        String text = collect(node("node-1", stats(5)));

        assertSample(text, 5,
                "cluster", "test-cluster",
                "node", "test-node-1",
                "nodeid", "node-1");
    }

    public void testAbsentStatsAreOmitted() throws IOException {
        String text = collect(node("node-1", null));

        assertTrue(samples(text, METRIC + "{").isEmpty());
        assertFalse(samples(text, "opensearch_http_open_server_number{").isEmpty());
    }

    public void testMultipleNodesKeepIndependentValues() throws IOException {
        String text = collect(node("node-1", stats(2)), node("node-2", stats(9)));

        assertSample(text, 2, "nodeid", "node-1");
        assertSample(text, 9, "nodeid", "node-2");
        assertEquals(2, samples(text, METRIC + "{").size());
    }

    private static WeightedRoutingStats stats(int failOpenCount) {
        WeightedRoutingStats stats = mock(WeightedRoutingStats.class);
        when(stats.getFailOpenCount()).thenReturn(failOpenCount);
        return stats;
    }

    private static NodeStats node(String id, WeightedRoutingStats stats) {
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
        when(nodeStats.getWeightedRoutingStats()).thenReturn(stats);
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

    private static void assertSample(String text, double expected, String... labels) {
        Set<String> matches = samples(text, METRIC + "{").stream()
                .filter(line -> matchesLabels(line, labels)).collect(Collectors.toSet());
        assertEquals("Expected one sample for " + Arrays.toString(labels) + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0);
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
