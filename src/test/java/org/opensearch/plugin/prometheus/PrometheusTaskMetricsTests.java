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
import org.opensearch.action.admin.cluster.node.tasks.list.ListTasksResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.tasks.TaskId;
import org.opensearch.http.HttpStats;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.tasks.TaskInfo;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests task metrics through the public collector entry point. */
public class PrometheusTaskMetricsTests extends OpenSearchTestCase {

    private static final String METRIC = "opensearch_tasks_number";
    private static final String BULK = "indices:data/write/bulk";
    private static final String BULK_SHARD = "indices:data/write/bulk[s]";
    private static final String NODES_STATS = "cluster:monitor/nodes/stats[n]";

    private final AtomicLong taskIds = new AtomicLong();

    public void testTasksAreCountedByAction() throws IOException {
        String text = collect(tasks(task("node-1", BULK), task("node-1", BULK_SHARD), task("node-1", BULK_SHARD),
                task("node-1", BULK_SHARD), task("node-1", NODES_STATS)), node("node-1"));
        assertSample(text, 1, "nodeid", "node-1", "action", BULK);
        assertSample(text, 3, "nodeid", "node-1", "action", BULK_SHARD);
        assertSample(text, 1, "nodeid", "node-1", "action", NODES_STATS);
        assertEquals(3, samples(text, METRIC + "{").size());
        assertTrue(text.contains("# TYPE " + METRIC + " gauge"));
    }

    public void testTasksKeepNodeIdentity() throws IOException {
        String text = collect(tasks(task("node-1", BULK), task("node-2", BULK), task("node-2", BULK)),
                node("node-1"), node("node-2"));
        assertSample(text, 1, "nodeid", "node-1", "action", BULK);
        assertSample(text, 2, "nodeid", "node-2", "action", BULK);
        for (String sample : samples(text, METRIC + "{")) {
            assertTrue(sample, sample.contains("cluster=\"test-cluster\""));
            assertTrue(sample, sample.contains("node=\"test-node-"));
        }
    }

    public void testTasksOfNodesWithoutStatsAreOmitted() throws IOException {
        String text = collect(tasks(task("node-1", BULK), task("unknown-node", BULK)), node("node-1"));
        assertSample(text, 1, "nodeid", "node-1", "action", BULK);
        assertFalse(text.contains("unknown-node"));
        assertEquals(1, samples(text, METRIC + "{").size());
    }

    public void testActionsWithoutTasksAreOmitted() throws IOException {
        String text = collect(tasks(), node("node-1"));
        assertTrue(samples(text, METRIC + "{").isEmpty());
    }

    public void testDisabledTasksAreOmitted() throws IOException {
        String text = collect(null, node("node-1"));
        assertTrue(samples(text, METRIC + "{").isEmpty());
        assertFalse(samples(text, "opensearch_http_open_server_number").isEmpty());
    }

    public void testExistingMetricValuesAreUnchanged() throws IOException {
        String before = collect(null, node("node-1"));
        String after = collect(tasks(task("node-1", BULK)), node("node-1"));
        Set<String> expected = existingSamples(before);
        assertFalse(expected.isEmpty());
        assertEquals(expected, existingSamples(after));
    }

    private TaskInfo task(String nodeId, String action) {
        return new TaskInfo(new TaskId(nodeId, taskIds.incrementAndGet()), "transport", action, null, null,
                0L, 0L, false, false, TaskId.EMPTY_TASK_ID, Collections.emptyMap(), null);
    }

    private static ListTasksResponse tasks(TaskInfo... tasks) {
        return new ListTasksResponse(new ArrayList<>(List.of(tasks)), Collections.emptyList(), Collections.emptyList());
    }

    private static NodeStats node(String id) {
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
        return nodeStats;
    }

    private static String collect(ListTasksResponse listTasks, NodeStats... nodes) throws IOException {
        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), false, false);
        collector.registerMetrics();
        collector.updateMetrics("origin", "origin-id", null, nodes, null, listTasks, null);
        return collector.getTextContent();
    }

    private static Set<String> samples(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).collect(Collectors.toSet());
    }

    private static Set<String> existingSamples(String text) {
        return samples(text, "opensearch_").stream()
                .filter(line -> !line.startsWith(METRIC))
                .filter(line -> !line.startsWith("opensearch_metrics_generate_time_seconds"))
                .collect(Collectors.toSet());
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
