/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus;

import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCatalog.MetricDescription;
import org.opensearch.plugin.prometheus.collector.PrometheusMetricsCollector;
import org.opensearch.test.OpenSearchTestCase;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Tests the generated metrics documentation (METRICS.md) and the catalog metric descriptions it relies on. */
public class MetricsDocumentationTests extends OpenSearchTestCase {

    public void testEveryRegisteredMetricIsDocumentedOnce() {
        String doc = MetricsDocumentation.generate();
        List<String> rows = doc.lines().filter(line -> line.startsWith("| `opensearch_")).collect(Collectors.toList());
        for (MetricDescription metric : descriptions()) {
            long count = rows.stream().filter(row -> row.startsWith("| `" + metric.name() + "` |")).count();
            assertEquals(metric.name(), 1, count);
        }
        assertTrue(rows.stream().anyMatch(row -> row.startsWith("| `opensearch_action_requests_total` | counter | node | `action`, `outcome` |")));
        assertTrue(rows.stream().anyMatch(row -> row.startsWith("| `opensearch_action_latency_seconds` | histogram | node | `action` |")));
        assertEquals(descriptions().size() + 2, rows.size());
    }

    public void testScopeAndLabels() {
        List<String> rows = MetricsDocumentation.generate().lines().collect(Collectors.toList());
        assertTrue(rows.contains("| `opensearch_cluster_status` | gauge | cluster |  | Cluster status |"));
        assertTrue(rows.contains("| `opensearch_circuitbreaker_limit_bytes` | gauge | node | `name` | Circuit breaker size limit |"));
        assertTrue(rows.contains("| `opensearch_index_status` | gauge | cluster | `index` | Index status |"));
    }

    public void testCatalogDescribesMetricsInRegistrationOrder() {
        PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog("c", "p_");
        catalog.registerClusterGauge("b", "B help", "x");
        catalog.registerNodeGauge("a", "A help", "y", "z");
        catalog.registerSummaryTimer("t", "T help");
        assertEquals(List.of(
                new MetricDescription("p_b", "gauge", "B help", List.of("cluster", "x")),
                new MetricDescription("p_a", "gauge", "A help", List.of("cluster", "node", "nodeid", "y", "z")),
                new MetricDescription("p_t", "summary", "T help", List.of("cluster", "node", "nodeid"))),
                catalog.getMetricDescriptions());
    }

    public void testMetricNamesAreUnique() {
        List<MetricDescription> descriptions = descriptions();
        Set<String> names = descriptions.stream().map(MetricDescription::name).collect(Collectors.toSet());
        assertEquals(descriptions.size(), names.size());
    }

    private static List<MetricDescription> descriptions() {
        PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog("c", "opensearch_");
        new PrometheusMetricsCollector(catalog, true, true).registerMetrics();
        return catalog.getMetricDescriptions();
    }
}
