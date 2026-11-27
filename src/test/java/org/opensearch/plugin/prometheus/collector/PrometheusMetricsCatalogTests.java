/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.collector;

import io.prometheus.client.Collector;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import org.opensearch.common.CheckedRunnable;
import org.opensearch.common.SuppressForbidden;
import org.opensearch.common.collect.Tuple;
import org.opensearch.plugin.prometheus.filter.PrometheusActionFilter;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class PrometheusMetricsCatalogTests extends OpenSearchTestCase {

    private static PrometheusMetricsCatalog buildReferenceCatalog() {
        PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog("test-cluster", "opensearch_");

        catalog.registerClusterGauge("cluster_status", "Cluster status");
        catalog.registerClusterGauge("cluster_shards_number", "Number of shards", "type");
        catalog.registerClusterGauge("index_doc_number", "Number of documents", "index", "context");
        catalog.registerClusterGauge("empty_family", "Registered but never set", "label");
        catalog.registerNodeGauge("jvm_mem_heap_used_bytes", "Memory used by JVM heap");
        catalog.registerNodeGauge("threadpool_threads_count", "Count of threads", "name", "type");
        catalog.registerSummaryTimer("metrics_generate_time_seconds", "Time spent generating metrics");

        catalog.setClusterGauge("cluster_status", 0);
        catalog.setClusterGauge("cluster_shards_number", 2638, "active");
        catalog.setClusterGauge("cluster_shards_number", 937, "active_primary");

        catalog.setClusterGauge("index_doc_number", 0d, "idx-zero", "total");
        catalog.setClusterGauge("index_doc_number", 1d, "idx-one", "total");
        catalog.setClusterGauge("index_doc_number", 9999999d, "idx-below-1e7", "total");
        catalog.setClusterGauge("index_doc_number", 10000000d, "idx-at-1e7", "total");
        catalog.setClusterGauge("index_doc_number", 50000000000d, "idx-big", "total");
        catalog.setClusterGauge("index_doc_number", -1d, "idx-negative", "total");
        catalog.setClusterGauge("index_doc_number", -0.0d, "idx-negative-zero", "total");
        catalog.setClusterGauge("index_doc_number", 0.5d, "idx-fraction", "total");
        catalog.setClusterGauge("index_doc_number", 1.0E-4d, "idx-small-fraction", "total");
        catalog.setClusterGauge("index_doc_number", Double.NaN, "idx-nan", "total");
        catalog.setClusterGauge("index_doc_number", Double.POSITIVE_INFINITY, "idx-pos-inf", "total");
        catalog.setClusterGauge("index_doc_number", Double.NEGATIVE_INFINITY, "idx-neg-inf", "total");
        catalog.setClusterGauge("index_doc_number", Double.MAX_VALUE, "idx-max", "total");
        catalog.setClusterGauge("index_doc_number", Long.MAX_VALUE, "idx-long-max", "total");

        catalog.setClusterGauge("index_doc_number", 1d, "idx-with-\"quote", "total");
        catalog.setClusterGauge("index_doc_number", 2d, "idx-with-\\backslash", "total");
        catalog.setClusterGauge("index_doc_number", 3d, "idx-with-\nnewline", "total");
        catalog.setClusterGauge("index_doc_number", 4d, "idx-ünïcodé-日本語", "total");
        catalog.setClusterGauge("index_doc_number", 5d, "idx-emoji-😀", "total");
        catalog.setClusterGauge("index_doc_number", 6d, "", "total");

        Tuple<String, String> nodeOne = new Tuple<>("node-1", "AbCdEf0123456789");
        Tuple<String, String> nodeTwo = new Tuple<>("node-\"2\"", "ZyXwVu9876543210");
        catalog.setNodeGauge(nodeOne, "jvm_mem_heap_used_bytes", 31744000000d);
        catalog.setNodeGauge(nodeTwo, "jvm_mem_heap_used_bytes", 1024d);
        catalog.setNodeGauge(nodeOne, "threadpool_threads_count", 16d, "write", "completed");
        catalog.setNodeGauge(nodeOne, "threadpool_threads_count", 0d, "write", "rejected");
        catalog.setNodeGauge(nodeTwo, "threadpool_threads_count", 144d, "search", "completed");

        return catalog;
    }

    // utf8ToString() copies all pages into one String. Fine in tests; production code must not call it.
    @SuppressWarnings("deprecation")
    public void testTextFormatBytesIsIdenticalToTextFormat() throws IOException {
        PrometheusMetricsCatalog catalog = buildReferenceCatalog();
        assertEquals(catalog.toTextFormat(), catalog.toTextFormatBytes().utf8ToString());
    }

    @SuppressWarnings("deprecation")
    public void testTextFormatBytesIsIdenticalToTextFormatAcrossPageBoundaries() throws IOException {
        PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog("test-cluster", "opensearch_");
        catalog.registerClusterGauge("index_doc_number", "Number of documents", "index");
        catalog.setClusterGauge("index_doc_number", 1d, "日😀".repeat(8 * 1024));

        assertEquals(catalog.toTextFormat(), catalog.toTextFormatBytes().utf8ToString());
    }

    // Subtracts the cost of rendering into a discarding writer, leaving only what the output itself costs.
    public void testTextFormatBytesAllocatesOneDocumentSize() throws IOException {
        Gauge gauge = Gauge.build().name("opensearch_index_doc_number").help("Docs").labelNames("index").create();
        for (int i = 0; i < 100_000; i++) {
            gauge.labels("my-application-logs-" + i).set(i);
        }
        PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog("test-cluster", "opensearch_");
        catalog.registerCollector(gauge);
        long document = catalog.toTextFormatBytes().length();

        long rendered = Long.MAX_VALUE;
        long baseline = Long.MAX_VALUE;
        for (int i = 0; i < 10; i++) {
            rendered = Math.min(rendered, allocatedBy(() -> catalog.toTextFormatBytes()));
            baseline = Math.min(baseline, allocatedBy(
                    () -> TextFormat.write004(Writer.nullWriter(), Collections.enumeration(gauge.collect()))));
        }
        double ratio = (double) (rendered - baseline) / document;
        assertTrue("output cost " + ratio + "x the document size, expected about 1x", ratio < 1.5);
    }

    @SuppressForbidden(reason = "per-thread allocated bytes are only available from com.sun.management.ThreadMXBean")
    private static long allocatedBy(CheckedRunnable<IOException> r) throws IOException {
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = mx.getCurrentThreadAllocatedBytes();
        r.run();
        return mx.getCurrentThreadAllocatedBytes() - before;
    }

    public void testRegisteredCollectorIsRendered() throws IOException {
        PrometheusMetricsCatalog catalog = buildReferenceCatalog();
        PrometheusActionFilter filter = new PrometheusActionFilter("opensearch_");
        catalog.registerCollector(filter.collector("test-cluster", "node-1", "AbCdEf0123456789"));

        String actual = catalog.toTextFormatBytes().utf8ToString();
        assertTrue(actual, actual.contains("# TYPE opensearch_action_latency_seconds histogram\n"));
        assertTrue(actual, actual.contains("opensearch_action_requests_total{cluster=\"test-cluster\",node=\"node-1\","
                + "nodeid=\"AbCdEf0123456789\",action=\"indices:data/read/search\",outcome=\"success\",} 0.0\n"));
    }

    public void testRegisteredCollectorDescribesEveryMetricItCollects() {
        Collector collector = new PrometheusActionFilter("opensearch_")
                .collector("test-cluster", "node-1", "AbCdEf0123456789");

        Set<String> described = ((Collector.Describable) collector).describe().stream()
                .map(family -> family.name).collect(Collectors.toSet());
        Set<String> collected = collector.collect().stream()
                .map(family -> family.name).collect(Collectors.toSet());
        assertEquals(collected, described);
    }
}
