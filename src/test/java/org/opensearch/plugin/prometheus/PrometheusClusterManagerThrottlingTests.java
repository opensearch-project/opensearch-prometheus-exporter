/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus;

import org.opensearch.action.admin.cluster.node.stats.NodeStats;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterManagerThrottlingStats;
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

public class PrometheusClusterManagerThrottlingTests extends OpenSearchTestCase {

    private static final String TOTAL = "opensearch_cluster_manager_throttling_total_throttled_tasks_count";
    private static final String PER_TASK_TYPE = "opensearch_cluster_manager_throttling_throttled_tasks_per_task_type_count";

    public void testTotalAndPerTaskTypeCounts() throws IOException {
        ClusterManagerThrottlingStats stats = new ClusterManagerThrottlingStats();
        stats.onThrottle("put-mapping", 3);
        stats.onThrottle("create-index", 2);
        stats.onThrottle("put-mapping", 4);

        String text = collect(node("node-1", stats));

        assertSample(text, TOTAL, 9);
        assertSample(text, PER_TASK_TYPE, 7, "task_type", "put-mapping");
        assertSample(text, PER_TASK_TYPE, 2, "task_type", "create-index");
        assertEquals(2, samples(text, PER_TASK_TYPE + "{").size());
    }

    public void testNothingThrottledReportsZeroTotalAndNoTaskTypes() throws IOException {
        String text = collect(node("node-1", new ClusterManagerThrottlingStats()));

        assertSample(text, TOTAL, 0);
        assertTrue(samples(text, PER_TASK_TYPE + "{").isEmpty());
    }

    public void testMissingStatsAreSkipped() throws IOException {
        String text = collect(node("node-1", null));

        assertTrue(samples(text, TOTAL + "{").isEmpty());
        assertTrue(samples(text, PER_TASK_TYPE + "{").isEmpty());
    }

    public void testMetricsAreLabelledPerNode() throws IOException {
        ClusterManagerThrottlingStats first = new ClusterManagerThrottlingStats();
        first.onThrottle("put-mapping", 5);
        ClusterManagerThrottlingStats second = new ClusterManagerThrottlingStats();
        second.onThrottle("put-mapping", 1);

        String text = collect(node("node-1", first), node("node-2", second));

        assertSample(text, TOTAL, 5, "nodeid", "node-1");
        assertSample(text, TOTAL, 1, "nodeid", "node-2");
        assertSample(text, PER_TASK_TYPE, 5, "nodeid", "node-1", "task_type", "put-mapping");
        assertSample(text, PER_TASK_TYPE, 1, "nodeid", "node-2", "task_type", "put-mapping");
        for (String sample : samples(text, "opensearch_cluster_manager_throttling_")) {
            assertTrue(sample, sample.contains("cluster=\"test-cluster\""));
        }
    }

    public void testExistingMetricValuesAreUnchanged() throws IOException {
        ClusterManagerThrottlingStats stats = new ClusterManagerThrottlingStats();
        stats.onThrottle("put-mapping", 3);

        Set<String> expected = existingSamples(collect(node("node-1", null)));
        assertFalse(expected.isEmpty());
        assertEquals(expected, existingSamples(collect(node("node-1", stats))));
    }

    private static NodeStats node(String id, ClusterManagerThrottlingStats stats) {
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
        when(nodeStats.getClusterManagerThrottlingStats()).thenReturn(stats);
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
                .filter(line -> !line.startsWith("opensearch_cluster_manager_throttling_"))
                .filter(line -> !line.startsWith("opensearch_metrics_generate_time_seconds"))
                .collect(Collectors.toSet());
    }

    private static void assertSample(String text, String metric, double expected, String... labels) {
        Set<String> matches = samples(text, metric + "{").stream()
                .filter(line -> matchesLabels(line, labels)).collect(Collectors.toSet());
        assertEquals("Expected one sample for " + metric + " " + Arrays.toString(labels) + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0);
        assertTrue(text.contains("# TYPE " + metric + " gauge"));
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
