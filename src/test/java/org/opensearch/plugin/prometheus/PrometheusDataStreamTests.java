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
import org.opensearch.action.admin.indices.datastream.DataStreamsStatsAction;
import org.opensearch.core.common.unit.ByteSizeValue;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Tests the data stream metrics. */
public class PrometheusDataStreamTests extends OpenSearchTestCase {

    public void testDataStreamStatsAreExported() throws IOException {
        String text = collect(response(
                new DataStreamsStatsAction.DataStreamStats("logs-nginx", 12, new ByteSizeValue(53_687_091_200L), 1_763_035_200_123L),
                new DataStreamsStatsAction.DataStreamStats("logs-app", 1, new ByteSizeValue(4096L), 1_763_000_000_000L)));

        assertSample(text, "backing_indices_number", "logs-nginx", 12);
        assertSample(text, "store_size_bytes", "logs-nginx", 53_687_091_200.0);
        assertSample(text, "maximum_timestamp_seconds", "logs-nginx", 1_763_035_200.123);
        assertSample(text, "backing_indices_number", "logs-app", 1);
        assertSample(text, "store_size_bytes", "logs-app", 4096);
        assertSample(text, "maximum_timestamp_seconds", "logs-app", 1_763_000_000.0);
        for (String metric : List.of("backing_indices_number", "store_size_bytes", "maximum_timestamp_seconds")) {
            assertEquals(2, samples(text, "opensearch_data_stream_" + metric + "{").size());
            assertTrue(text.contains("# TYPE opensearch_data_stream_" + metric + " gauge"));
        }
    }

    public void testEmptyDataStreamHasNoMaximumTimestamp() throws IOException {
        String text = collect(response(
                new DataStreamsStatsAction.DataStreamStats("logs-empty", 1, new ByteSizeValue(208L), 0L)));

        assertSample(text, "backing_indices_number", "logs-empty", 1);
        assertSample(text, "store_size_bytes", "logs-empty", 208);
        assertTrue(samples(text, "opensearch_data_stream_maximum_timestamp_seconds{").isEmpty());
    }

    public void testNoDataStreams() throws IOException {
        String text = collect(response());
        assertTrue(samples(text, "opensearch_data_stream_").isEmpty());
    }

    public void testMissingStatsAreIgnored() throws IOException {
        String text = collect(null);
        assertTrue(samples(text, "opensearch_data_stream_").isEmpty());
        assertTrue(text.contains("# TYPE opensearch_data_stream_backing_indices_number gauge"));
    }

    private static DataStreamsStatsAction.Response response(DataStreamsStatsAction.DataStreamStats... dataStreams) {
        long total = 0;
        int backingIndices = 0;
        for (DataStreamsStatsAction.DataStreamStats stats : dataStreams) {
            total += stats.getStoreSize().getBytes();
            backingIndices += stats.getBackingIndices();
        }
        return new DataStreamsStatsAction.Response(1, 1, 0, List.of(), dataStreams.length, backingIndices,
                new ByteSizeValue(total), dataStreams);
    }

    private static String collect(DataStreamsStatsAction.Response response) throws IOException {
        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), false, false);
        collector.registerMetrics();
        collector.updateMetrics("origin", "origin-id", null, new NodeStats[0], null, null, response);
        return collector.getTextContent();
    }

    private static Set<String> samples(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).collect(Collectors.toSet());
    }

    private static void assertSample(String text, String metric, String dataStream, double expected) {
        Set<String> matches = samples(text, "opensearch_data_stream_" + metric + "{").stream()
                .filter(line -> line.contains("data_stream=\"" + dataStream + "\""))
                .collect(Collectors.toSet());
        assertEquals("Expected one sample for " + metric + "/" + dataStream + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 0.0005);
    }
}
