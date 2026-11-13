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
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.cluster.health.ClusterIndexHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.recovery.RecoveryStats;
import org.opensearch.index.shard.IndexingStats;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that time metrics keep sub-second precision.
 * See https://github.com/opensearch-project/opensearch-prometheus-exporter/issues/690
 */
public class PrometheusSecondsPrecisionTests extends OpenSearchTestCase {

    public void testIndexTimesKeepFractions() throws IOException {
        String text = collect();

        assertSample(text, "opensearch_index_indexing_index_time_seconds", 0.037, "index=\"logs\"", "context=\"total\"");
        assertSample(text, "opensearch_index_indexing_delete_time_seconds", 1.5, "index=\"logs\"", "context=\"total\"");
        assertSample(text, "opensearch_index_indexing_throttle_time_seconds", 0.25, "index=\"logs\"", "context=\"total\"");
        assertSample(text, "opensearch_index_recovery_throttle_time_seconds", 1.25, "index=\"logs\"", "context=\"total\"");
    }

    public void testClusterTaskMaxWaitingTimeKeepsFractions() throws IOException {
        assertSample(collect(), "opensearch_cluster_task_max_waiting_time_seconds", 0.75);
    }

    private static String collect() throws IOException {
        CommonStats stats = new CommonStats(CommonStatsFlags.ALL);
        stats.indexing = new IndexingStats(new IndexingStats.Stats.Builder()
                .indexCount(10).indexTimeInMillis(37)
                .deleteCount(2).deleteTimeInMillis(1500)
                .throttleTimeInMillis(250)
                .docStatusStats(new IndexingStats.Stats.DocStatusStats())
                .build());
        RecoveryStats recovery = new RecoveryStats();
        recovery.addThrottleTime(TimeUnit.MILLISECONDS.toNanos(1250));
        stats.recoveryStats = recovery;
        IndexStats indexStats = mock(IndexStats.class);
        when(indexStats.getTotal()).thenReturn(stats);
        when(indexStats.getPrimaries()).thenReturn(stats);
        IndicesStatsResponse indicesStats = mock(IndicesStatsResponse.class);
        when(indicesStats.getIndices()).thenReturn(Map.of("logs", indexStats));

        IndexMetadata metadata = IndexMetadata.builder("logs")
                .settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(0).build();
        ClusterHealthResponse clusterHealth = mock(ClusterHealthResponse.class);
        when(clusterHealth.getIndices()).thenReturn(
                Map.of("logs", new ClusterIndexHealth(metadata, IndexRoutingTable.builder(metadata.getIndex()).build())));
        when(clusterHealth.getStatus()).thenReturn(ClusterHealthStatus.GREEN);
        when(clusterHealth.getTaskMaxWaitingTime()).thenReturn(TimeValue.timeValueMillis(750));

        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), true, false);
        collector.registerMetrics();
        collector.updateMetrics("origin", "origin-id", clusterHealth, new NodeStats[0], indicesStats, null);
        return collector.getTextContent();
    }

    private static void assertSample(String text, String metric, double expected, String... labels) {
        Set<String> matches = text.lines()
                .filter(line -> line.startsWith(metric + "{"))
                .filter(line -> {
                    for (String label : labels) {
                        if (line.contains(label) == false) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toSet());
        assertEquals("Expected one sample for " + metric + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 1e-9);
    }
}
