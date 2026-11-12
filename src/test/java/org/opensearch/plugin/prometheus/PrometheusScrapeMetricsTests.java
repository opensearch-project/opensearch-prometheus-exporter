/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.plugin.prometheus.action.ScrapeStats;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Tests the scrape duration and success metrics and the {@link ScrapeStats} holder. */
public class PrometheusScrapeMetricsTests extends OpenSearchTestCase {

    public void testDurationAndSuccessPerCollector() {
        ScrapeStats stats = new ScrapeStats();
        stats.record(ScrapeStats.CLUSTER_HEALTH, TimeUnit.MILLISECONDS.toNanos(20), true);
        stats.record(ScrapeStats.NODES_STATS, TimeUnit.MILLISECONDS.toNanos(1500), true);
        stats.record(ScrapeStats.INDICES_STATS, TimeUnit.MILLISECONDS.toNanos(250), false);
        String text = collect(stats);

        assertSample(text, "opensearch_scrape_duration_seconds", 0.02, ScrapeStats.CLUSTER_HEALTH);
        assertSample(text, "opensearch_scrape_duration_seconds", 1.5, ScrapeStats.NODES_STATS);
        assertSample(text, "opensearch_scrape_duration_seconds", 0.25, ScrapeStats.INDICES_STATS);
        assertSample(text, "opensearch_scrape_success", 1, ScrapeStats.CLUSTER_HEALTH);
        assertSample(text, "opensearch_scrape_success", 1, ScrapeStats.NODES_STATS);
        assertSample(text, "opensearch_scrape_success", 0, ScrapeStats.INDICES_STATS);
        assertEquals(3, samples(text, "opensearch_scrape_success{").size());
        assertTrue(text.contains("# TYPE opensearch_scrape_success gauge"));
        assertTrue(text.contains("# TYPE opensearch_scrape_duration_seconds gauge"));
    }

    public void testMetricsAreReportedForTheOriginNode() {
        ScrapeStats stats = new ScrapeStats();
        stats.record(ScrapeStats.NODES_STATS, 1, true);
        for (String sample : samples(collect(stats), "opensearch_scrape_")) {
            assertTrue(sample, sample.contains("cluster=\"test-cluster\""));
            assertTrue(sample, sample.contains("node=\"origin\""));
            assertTrue(sample, sample.contains("nodeid=\"origin-id\""));
        }
    }

    public void testUnrecordedStepsAreOmitted() {
        assertTrue(samples(collect(new ScrapeStats()), "opensearch_scrape_").isEmpty());
        assertTrue(samples(collect(null), "opensearch_scrape_").isEmpty());
    }

    public void testNegativeDurationIsClampedToZero() {
        ScrapeStats stats = new ScrapeStats();
        stats.record(ScrapeStats.NODES_INFO, -5, true);
        assertEquals(0, stats.getSteps().get(ScrapeStats.NODES_INFO).durationNanos());
    }

    public void testSerializationKeepsStepsAndOrder() throws IOException {
        ScrapeStats stats = new ScrapeStats();
        stats.record(ScrapeStats.CLUSTER_HEALTH, 10, true);
        stats.record(ScrapeStats.NODES_INFO, 20, true);
        stats.record(ScrapeStats.NODES_STATS, 30, false);
        stats.record(ScrapeStats.CLUSTER_SETTINGS, 40, true);
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            stats.writeTo(out);
            ScrapeStats read = new ScrapeStats(out.bytes().streamInput());
            assertEquals(stats.getSteps(), read.getSteps());
            assertEquals(List.of(ScrapeStats.CLUSTER_HEALTH, ScrapeStats.NODES_INFO, ScrapeStats.NODES_STATS,
                    ScrapeStats.CLUSTER_SETTINGS), List.copyOf(read.getSteps().keySet()));
        }
    }

    private static String collect(ScrapeStats stats) {
        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), false, false);
        collector.registerMetrics();
        collector.updateScrapeMetrics("origin", "origin-id", stats);
        try {
            return collector.getTextContent();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Set<String> samples(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).collect(Collectors.toSet());
    }

    private static void assertSample(String text, String metric, double expected, String collector) {
        Set<String> matches = samples(text, metric + "{").stream()
                .filter(line -> line.contains("collector=\"" + collector + "\"")).collect(Collectors.toSet());
        assertEquals("Expected one sample for " + metric + " " + collector + " in " + text, 1, matches.size());
        String sample = matches.iterator().next();
        assertEquals(sample, expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)), 1e-9);
    }
}
