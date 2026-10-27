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
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.search.backpressure.settings.SearchBackpressureMode;
import org.opensearch.search.backpressure.stats.SearchBackpressureStats;
import org.opensearch.search.backpressure.stats.SearchShardTaskStats;
import org.opensearch.search.backpressure.stats.SearchTaskStats;
import org.opensearch.search.backpressure.trackers.CpuUsageTracker;
import org.opensearch.search.backpressure.trackers.ElapsedTimeTracker;
import org.opensearch.search.backpressure.trackers.HeapUsageTracker;
import org.opensearch.search.backpressure.trackers.NativeMemoryUsageTracker;
import org.opensearch.search.backpressure.trackers.TaskResourceUsageTrackerType;
import org.opensearch.search.backpressure.trackers.TaskResourceUsageTrackers.TaskResourceUsageTracker;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests real OpenSearch stats serialization through the public collector entry point. */
public class PrometheusSearchBackpressureTests extends OpenSearchTestCase {

    public void testModesAreOneHot() throws IOException {
        for (SearchBackpressureMode mode : SearchBackpressureMode.values()) {
            String text = collect(node("node-1", stats(mode, Map.of())));
            for (SearchBackpressureMode candidate : SearchBackpressureMode.values()) {
                assertSample(text, "mode_bool", mode == candidate ? 1 : 0, "mode", candidate.getName());
            }
            assertEquals(3, samples(text, "opensearch_search_backpressure_mode_bool").size());
        }
    }

    public void testTaskCounts() throws IOException {
        String text = collect(node("node-1", stats(SearchBackpressureMode.MONITOR_ONLY, Map.of())));
        assertSample(text, "cancellation_count", 7, "task", "search_task");
        assertSample(text, "cancellation_limit_reached_count", 2, "task", "search_task");
        assertSample(text, "completion_count", 100, "task", "search_task");
        assertSample(text, "cancellation_count", 11, "task", "search_shard_task");
        assertSample(text, "cancellation_limit_reached_count", 3, "task", "search_shard_task");
        assertSample(text, "completion_count", 200, "task", "search_shard_task");
    }

    public void testResourceTrackersAndUnits() throws IOException {
        Map<TaskResourceUsageTrackerType, TaskResourceUsageTracker.Stats> trackers = Map.of(
                TaskResourceUsageTrackerType.CPU_USAGE_TRACKER,
                new CpuUsageTracker.Stats(4, TimeUnit.MILLISECONDS.toNanos(1500), TimeUnit.MILLISECONDS.toNanos(250)),
                TaskResourceUsageTrackerType.ELAPSED_TIME_TRACKER,
                new ElapsedTimeTracker.Stats(5, TimeUnit.MILLISECONDS.toNanos(2500), TimeUnit.MILLISECONDS.toNanos(750)),
                TaskResourceUsageTrackerType.HEAP_USAGE_TRACKER, new HeapUsageTracker.Stats(6, 8192, 4096, 2048),
                TaskResourceUsageTrackerType.NATIVE_MEMORY_USAGE_TRACKER, new NativeMemoryUsageTracker.Stats(8, 16384, 1024));
        String text = collect(node("node-1", stats(SearchBackpressureMode.ENFORCED, trackers)));

        for (String task : new String[] {"search_task", "search_shard_task"}) {
            assertTracker(text, task, "cpu_usage_tracker", "cancellation_count", 4);
            assertTracker(text, task, "cpu_usage_tracker", "current_max_millis", 1500);
            assertTracker(text, task, "cpu_usage_tracker", "current_avg_millis", 250);
            assertTracker(text, task, "elapsed_time_tracker", "cancellation_count", 5);
            assertTracker(text, task, "elapsed_time_tracker", "current_max_millis", 2500);
            assertTracker(text, task, "elapsed_time_tracker", "current_avg_millis", 750);
            assertTracker(text, task, "heap_usage_tracker", "cancellation_count", 6);
            assertTracker(text, task, "heap_usage_tracker", "current_max_bytes", 8192);
            assertTracker(text, task, "heap_usage_tracker", "current_avg_bytes", 4096);
            assertTracker(text, task, "heap_usage_tracker", "rolling_avg_bytes", 2048);
            assertTracker(text, task, "native_memory_usage_tracker", "cancellation_count", 8);
            assertTracker(text, task, "native_memory_usage_tracker", "current_max_bytes", 16384);
            assertTracker(text, task, "native_memory_usage_tracker", "current_avg_bytes", 1024);
        }
        assertEquals(2, samples(text, "opensearch_search_backpressure_resource_tracker_rolling_avg_bytes").size());
        assertEquals(4, samples(text, "opensearch_search_backpressure_resource_tracker_current_max_millis").size());
        assertEquals(4, samples(text, "opensearch_search_backpressure_resource_tracker_current_max_bytes").size());
    }

    public void testAbsentStatsAreOmitted() throws IOException {
        String text = collect(node("node-1", null));
        assertTrue(samples(text, "opensearch_search_backpressure_").isEmpty());
        assertFalse(samples(text, "opensearch_http_open_server_number").isEmpty());
    }

    public void testAbsentTaskAndNullTrackerAreOmitted() throws IOException {
        Map<TaskResourceUsageTrackerType, TaskResourceUsageTracker.Stats> trackers = new HashMap<>();
        trackers.put(TaskResourceUsageTrackerType.CPU_USAGE_TRACKER, null);
        trackers.put(TaskResourceUsageTrackerType.HEAP_USAGE_TRACKER, new HeapUsageTracker.Stats(0, 0, 0, 0));
        SearchBackpressureStats stats = new SearchBackpressureStats(null,
                new SearchShardTaskStats(0, 0, 0, trackers), SearchBackpressureMode.DISABLED);
        String text = collect(node("node-1", stats));
        assertFalse(text.contains("task=\"search_task\""));
        assertFalse(text.contains("tracker=\"cpu_usage_tracker\""));
        assertTracker(text, "search_shard_task", "heap_usage_tracker", "current_max_bytes", 0);
        assertSample(text, "mode_bool", 1, "mode", "disabled");
    }

    public void testUnavailableCompletionCountIsOmitted() throws IOException {
        SearchBackpressureStats stats = new SearchBackpressureStats(
                new SearchTaskStats(0, 0, -1, Map.of()), new SearchShardTaskStats(0, 0, -1, Map.of()),
                SearchBackpressureMode.MONITOR_ONLY);
        assertTrue(samples(collect(node("node-1", stats)), "opensearch_search_backpressure_completion_count").isEmpty());
    }

    public void testMetricsKeepNodeIdentity() throws IOException {
        String text = collect(node("node-1", stats(SearchBackpressureMode.MONITOR_ONLY, Map.of())),
                node("node-2", stats(SearchBackpressureMode.ENFORCED, Map.of())));
        assertSample(text, "mode_bool", 1, "nodeid", "node-1", "mode", "monitor_only");
        assertSample(text, "mode_bool", 0, "nodeid", "node-2", "mode", "monitor_only");
        assertSample(text, "mode_bool", 1, "nodeid", "node-2", "mode", "enforced");
        for (String sample : samples(text, "opensearch_search_backpressure_")) {
            assertTrue(sample, sample.contains("cluster=\"test-cluster\""));
            assertTrue(sample, sample.contains("node=\"test-node-"));
        }
    }

    public void testExistingMetricValuesAreUnchanged() throws IOException {
        String before = collect(node("node-1", null));
        String after = collect(node("node-1", stats(SearchBackpressureMode.MONITOR_ONLY, Map.of())));
        Set<String> expected = existingSamples(before);
        assertFalse(expected.isEmpty());
        assertEquals(expected, existingSamples(after));
    }

    private static SearchBackpressureStats stats(SearchBackpressureMode mode,
            Map<TaskResourceUsageTrackerType, TaskResourceUsageTracker.Stats> trackers) {
        return new SearchBackpressureStats(new SearchTaskStats(7, 2, 100, trackers),
                new SearchShardTaskStats(11, 3, 200, trackers), mode);
    }

    private static NodeStats node(String id, SearchBackpressureStats stats) {
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
        when(nodeStats.getSearchBackpressureStats()).thenReturn(stats);
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
                .filter(line -> !line.startsWith("opensearch_search_backpressure_"))
                .filter(line -> !line.startsWith("opensearch_metrics_generate_time_seconds"))
                .collect(Collectors.toSet());
    }

    private static void assertTracker(String text, String task, String tracker, String field, double expected) {
        assertSample(text, "resource_tracker_" + field, expected, "task", task, "tracker", tracker);
    }

    private static void assertSample(String text, String metric, double expected, String... labels) {
        Set<String> matches = samples(text, "opensearch_search_backpressure_" + metric + "{").stream()
                .filter(line -> matchesLabels(line, labels)).collect(Collectors.toSet());
        assertEquals("Expected one sample for " + metric + " " + Arrays.toString(labels) + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0);
        assertTrue(text.contains("# TYPE opensearch_search_backpressure_" + metric + " gauge"));
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
