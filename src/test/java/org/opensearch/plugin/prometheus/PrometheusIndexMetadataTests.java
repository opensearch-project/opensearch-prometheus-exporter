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
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.plugin.prometheus.action.IndexMetadataData;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the index metadata metrics, such as the index creation timestamp. */
public class PrometheusIndexMetadataTests extends OpenSearchTestCase {

    private static final long LOGS_CREATED = 1_759_276_800_123L;
    private static final long METRICS_CREATED = 1_762_000_000_000L;

    public void testCreationDatesAreExtracted() {
        IndexMetadataData data = new IndexMetadataData(metadata(), List.of("logs", "metrics", "deleted"));

        assertEquals(Long.valueOf(LOGS_CREATED), data.getCreationDate("logs"));
        assertEquals(Long.valueOf(METRICS_CREATED), data.getCreationDate("metrics"));
        // Indices missing from the metadata, or not requested, are skipped.
        assertNull(data.getCreationDate("deleted"));
        assertNull(data.getCreationDate("other"));
    }

    public void testSerializationRoundTrip() throws IOException {
        IndexMetadataData data = new IndexMetadataData(metadata(), List.of("logs", "metrics"));
        IndexMetadataData copy;
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            data.writeTo(out);
            copy = new IndexMetadataData(out.bytes().streamInput());
        }
        assertEquals(data.getCreationDate("logs"), copy.getCreationDate("logs"));
        assertEquals(data.getCreationDate("metrics"), copy.getCreationDate("metrics"));
        assertNull(copy.getCreationDate("other"));
    }

    public void testCreationTimestampIsExportedInSeconds() throws IOException {
        IndexMetadataData data = new IndexMetadataData(metadata(), List.of("logs", "metrics"));

        String text = collect(true, data, "logs", "metrics");

        assertSample(text, "logs", LOGS_CREATED / 1000.0);
        assertSample(text, "metrics", METRICS_CREATED / 1000.0);
        assertEquals(2, samples(text, "opensearch_index_creation_timestamp_seconds{").size());
        assertTrue(text.contains("# TYPE opensearch_index_creation_timestamp_seconds gauge"));
    }

    public void testIndicesWithoutMetadataAreOmitted() throws IOException {
        IndexMetadataData data = new IndexMetadataData(metadata(), List.of("logs"));

        String text = collect(true, data, "logs", "metrics");

        assertSample(text, "logs", LOGS_CREATED / 1000.0);
        assertEquals(1, samples(text, "opensearch_index_creation_timestamp_seconds{").size());
    }

    public void testMissingMetadataIsIgnored() throws IOException {
        assertTrue(samples(collect(true, null, "logs"), "opensearch_index_creation_timestamp_seconds{").isEmpty());
    }

    public void testCreationTimestampRequiresIndexMetrics() throws IOException {
        IndexMetadataData data = new IndexMetadataData(metadata(), List.of("logs"));
        assertTrue(samples(collect(false, data, "logs"), "opensearch_index_creation_timestamp_seconds{").isEmpty());
    }

    private static Metadata metadata() {
        return Metadata.builder()
                .put(index("logs", LOGS_CREATED), false)
                .put(index("metrics", METRICS_CREATED), false)
                .build();
    }

    private static IndexMetadata index(String name, long creationDate) {
        return IndexMetadata.builder(name)
                .settings(settings(Version.CURRENT))
                .numberOfShards(1).numberOfReplicas(0)
                .creationDate(creationDate)
                .build();
    }

    private static String collect(boolean indices, IndexMetadataData data, String... indexNames) throws IOException {
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

    private static void assertSample(String text, String index, double expected) {
        Set<String> matches = samples(text, "opensearch_index_creation_timestamp_seconds{").stream()
                .filter(line -> line.contains("index=\"" + index + "\""))
                .collect(Collectors.toSet());
        assertEquals("Expected one sample for " + index + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0005);
    }
}
