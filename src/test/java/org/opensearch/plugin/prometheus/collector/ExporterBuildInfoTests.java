/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.collector;

import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** Tests the exporter build information and the exporter_build_info metric. */
public class ExporterBuildInfoTests extends OpenSearchTestCase {

    private static final String METRIC = "opensearch_exporter_build_info";

    public void testCurrentBuildInfoIsGeneratedAtBuildTime() {
        ExporterBuildInfo current = ExporterBuildInfo.current();
        assertTrue(current.getVersion(), current.getVersion().matches("\\d+\\.\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"));
        assertTrue(current.getRevision(), current.getRevision().matches("[0-9a-f]{40}|" + ExporterBuildInfo.UNKNOWN));
    }

    public void testMetricLabels() throws IOException {
        List<String> samples = samples(collect(new ExporterBuildInfo("3.9.0.0", "0123456789abcdef0123456789abcdef01234567")));
        assertEquals(List.of(METRIC + "{cluster=\"test-cluster\",node=\"origin\",nodeid=\"origin-id\","
                + "version=\"3.9.0.0\",revision=\"0123456789abcdef0123456789abcdef01234567\","
                + "java_version=\"" + Runtime.version() + "\",} 1.0"), samples);
    }

    public void testUnknownValuesAreStillReported() throws IOException {
        List<String> samples = samples(collect(new ExporterBuildInfo(ExporterBuildInfo.UNKNOWN, ExporterBuildInfo.UNKNOWN)));
        assertEquals(1, samples.size());
        assertTrue(samples.get(0), samples.get(0).contains("version=\"unknown\",revision=\"unknown\""));
    }

    private static String collect(ExporterBuildInfo buildInfo) throws IOException {
        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), false, false);
        collector.registerMetrics();
        collector.updateExporterBuildMetrics("origin", "origin-id", buildInfo);
        return collector.getTextContent();
    }

    private static List<String> samples(String text) {
        return text.lines().filter(line -> line.startsWith(METRIC + "{")).collect(Collectors.toList());
    }
}
