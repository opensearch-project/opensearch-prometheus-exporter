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
import org.opensearch.index.warmer.WarmerStats;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the index level warmer metrics. */
public class PrometheusIndexWarmerTests extends OpenSearchTestCase {

    // See https://github.com/opensearch-project/opensearch-prometheus-exporter/issues/688
    public void testWarmerTimeIsExportedInSeconds() throws IOException {
        String text = collect(new WarmerStats(1, 7, 2500), new WarmerStats(0, 3, 1250));

        assertSample(text, "opensearch_index_warmer_time_seconds", "total", 2.5);
        assertSample(text, "opensearch_index_warmer_time_seconds", "primaries", 1.25);
        assertSample(text, "opensearch_index_warmer_count", "total", 7);
        assertSample(text, "opensearch_index_warmer_count", "primaries", 3);
        assertSample(text, "opensearch_index_warmer_current_number", "total", 1);
        assertSample(text, "opensearch_index_warmer_current_number", "primaries", 0);
    }

    private static String collect(WarmerStats total, WarmerStats primaries) throws IOException {
        CommonStats totalStats = new CommonStats(CommonStatsFlags.ALL);
        totalStats.warmer = total;
        CommonStats primariesStats = new CommonStats(CommonStatsFlags.ALL);
        primariesStats.warmer = primaries;
        IndexStats indexStats = mock(IndexStats.class);
        when(indexStats.getTotal()).thenReturn(totalStats);
        when(indexStats.getPrimaries()).thenReturn(primariesStats);
        IndicesStatsResponse indicesStats = mock(IndicesStatsResponse.class);
        when(indicesStats.getIndices()).thenReturn(Map.of("logs", indexStats));

        IndexMetadata metadata = IndexMetadata.builder("logs")
                .settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(0).build();
        ClusterHealthResponse clusterHealth = mock(ClusterHealthResponse.class);
        when(clusterHealth.getIndices()).thenReturn(
                Map.of("logs", new ClusterIndexHealth(metadata, IndexRoutingTable.builder(metadata.getIndex()).build())));
        when(clusterHealth.getStatus()).thenReturn(ClusterHealthStatus.GREEN);
        when(clusterHealth.getTaskMaxWaitingTime()).thenReturn(TimeValue.ZERO);

        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), true, false);
        collector.registerMetrics();
        collector.updateMetrics("origin", "origin-id", clusterHealth, new NodeStats[0], indicesStats, null);
        return collector.getTextContent();
    }

    private static void assertSample(String text, String metric, String context, double expected) {
        Set<String> matches = text.lines()
                .filter(line -> line.startsWith(metric + "{"))
                .filter(line -> line.contains("index=\"logs\""))
                .filter(line -> line.contains("context=\"" + context + "\""))
                .collect(Collectors.toSet());
        assertEquals("Expected one sample for " + metric + "/" + context + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0);
    }
}
