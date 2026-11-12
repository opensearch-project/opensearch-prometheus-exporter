/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus;

import org.opensearch.Build;
import org.opensearch.Version;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** Tests the version info metric through the public collector entry point. */
public class PrometheusVersionMetricsTests extends OpenSearchTestCase {

    private static final String METRIC = "opensearch_version_info";

    public void testVersionInfoMatchesGetRootApi() throws IOException {
        List<String> samples = samples(collect("cluster-uuid-1", Version.CURRENT, Build.CURRENT));
        assertEquals(1, samples.size());
        assertEquals(METRIC + "{cluster=\"test-cluster\",node=\"origin\",nodeid=\"origin-id\","
                + "cluster_uuid=\"cluster-uuid-1\","
                + "version=\"" + Build.CURRENT.getQualifiedVersion() + "\","
                + "distribution=\"" + Build.CURRENT.getDistribution() + "\","
                + "build_type=\"" + Build.CURRENT.type().displayName() + "\","
                + "build_hash=\"" + Build.CURRENT.hash() + "\","
                + "build_date=\"" + Build.CURRENT.date() + "\","
                + "lucene_version=\"" + Version.CURRENT.luceneVersion + "\",} 1.0", samples.get(0));
    }

    public void testCustomBuildValues() throws IOException {
        Build build = new Build(Build.Type.DEB, "abc123", "2026-01-02T03:04:05Z", false, "3.5.0", "opensearch");
        String sample = samples(collect("uuid", Version.CURRENT, build)).get(0);
        assertTrue(sample, sample.contains("version=\"3.5.0\""));
        assertTrue(sample, sample.contains("build_type=\"deb\""));
        assertTrue(sample, sample.contains("build_hash=\"abc123\""));
        assertTrue(sample, sample.contains("build_date=\"2026-01-02T03:04:05Z\""));
        assertTrue(sample, sample.endsWith(" 1.0"));
    }

    public void testMissingVersionOrBuildIsOmitted() throws IOException {
        assertTrue(samples(collect("uuid", null, Build.CURRENT)).isEmpty());
        assertTrue(samples(collect("uuid", Version.CURRENT, null)).isEmpty());
    }

    private static String collect(String clusterUUID, Version version, Build build) throws IOException {
        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(
                new PrometheusMetricsCatalog("test-cluster", "opensearch_"), false, false);
        collector.registerMetrics();
        collector.updateVersionMetrics("origin", "origin-id", clusterUUID, version, build);
        return collector.getTextContent();
    }

    private static List<String> samples(String text) {
        return text.lines().filter(line -> line.startsWith(METRIC + "{")).collect(Collectors.toList());
    }
}
