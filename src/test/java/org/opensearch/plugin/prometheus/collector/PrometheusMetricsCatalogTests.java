/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.collector;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import org.opensearch.common.CheckedRunnable;
import org.opensearch.common.SuppressForbidden;
import org.opensearch.common.collect.Tuple;
import org.opensearch.plugin.prometheus.filter.PrometheusActionFilter;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class PrometheusMetricsCatalogTests extends OpenSearchTestCase {

    /**
     * A fixed workload exercising every public entry point and every awkward case the real
     * collector can produce: both label scopes, a registered-but-unused family, label values
     * needing escaping or UTF-8 encoding, and values spanning every branch of
     * {@link Double#toString}.
     */
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

        // Values chosen to cross every branch of Double.toString.
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

        // Label values that must be escaped or UTF-8 encoded.
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

    /**
     * Both renderings enumerate the same registry, so they must agree byte for byte.
     * <p>
     * {@code utf8ToString()} here and below leaves the pages untouched but copies them all into one contiguous
     * String, which is exactly the allocation the paging avoids. That is fine in a test, but production code
     * must not call it on the response.
     */
    @SuppressWarnings("deprecation")
    public void testTextFormatBytesIsIdenticalToTextFormat() throws IOException {
        PrometheusMetricsCatalog catalog = buildReferenceCatalog();
        assertEquals(catalog.toTextFormat(), catalog.toTextFormatBytes().utf8ToString());
    }

    /**
     * One label value spanning several pages, built from 3- and 4-byte UTF-8 characters, so that
     * multi-byte sequences straddle page and writer-buffer boundaries.
     */
    @SuppressWarnings("deprecation")
    public void testTextFormatBytesIsIdenticalToTextFormatAcrossPageBoundaries() throws IOException {
        PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog("test-cluster", "opensearch_");
        catalog.registerClusterGauge("index_doc_number", "Number of documents", "index");
        catalog.setClusterGauge("index_doc_number", 1d, "日😀".repeat(8 * 1024));

        assertEquals(catalog.toTextFormat(), catalog.toTextFormatBytes().utf8ToString());
    }

    /**
     * Guards against the scrape document being copied again. On large clusters it runs to tens of MB,
     * and every contiguous copy of it is a G1 humongous allocation, so rendering must cost one paged
     * buffer of the document's own size and nothing more.
     * <p>
     * Rendering the same samples into a discarding writer gives the cost that does not depend on where
     * the output goes (sample enumeration, number formatting, escaping); subtracting it leaves what the
     * output itself costs. That is about one document's size. Rendering through a
     * {@link java.io.StringWriter} costs several, and any single extra copy, whether a String, a
     * {@code byte[]} or a {@code BytesArray}, adds at least one more.
     */
    @SuppressForbidden(reason = "per-thread allocated bytes are only available from com.sun.management.ThreadMXBean")
    public void testTextFormatBytesAllocatesOneDocumentSize() throws Exception {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue("thread allocation accounting is not available on this JVM",
                bean instanceof com.sun.management.ThreadMXBean
                        && ((com.sun.management.ThreadMXBean) bean).isThreadAllocatedMemoryEnabled());
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) bean;

        // The samples live in a registry of their own, registered with an otherwise empty catalog, so that
        // the baseline can enumerate exactly the samples the catalog renders.
        CollectorRegistry registry = indicesRegistry(between(400, 800));
        PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog("test-cluster", "opensearch_");
        catalog.registerCollector(new Collector() {
            @Override
            public List<MetricFamilySamples> collect() {
                return Collections.list(registry.metricFamilySamples());
            }
        });
        long[] document = new long[1];

        CheckedRunnable<IOException> render = () -> document[0] = catalog.toTextFormatBytes().length();
        CheckedRunnable<IOException> discard = () ->
                TextFormat.write004(java.io.Writer.nullWriter(), registry.metricFamilySamples());

        // Warm up so that the JIT has settled, then keep the cheapest of several runs to discard noise.
        for (int i = 0; i < 5; i++) {
            render.run();
            discard.run();
        }
        long rendered = Long.MAX_VALUE;
        long baseline = Long.MAX_VALUE;
        for (int i = 0; i < 10; i++) {
            rendered = Math.min(rendered, allocatedBy(mx, render));
            baseline = Math.min(baseline, allocatedBy(mx, discard));
        }

        double ratio = (double) (rendered - baseline) / document[0];
        assertTrue(String.format(Locale.ROOT,
                "the output cost %.2fx its own size (%,d bytes for a %,d-byte document, over a %,d-byte"
                        + " rendering baseline); about 1x is expected, so it is likely being copied again",
                ratio, rendered - baseline, document[0], baseline), ratio < 1.5);
    }

    @SuppressForbidden(reason = "per-thread allocated bytes are only available from com.sun.management.ThreadMXBean")
    private static long allocatedBy(com.sun.management.ThreadMXBean mx, CheckedRunnable<IOException> r)
            throws IOException {
        long before = mx.getCurrentThreadAllocatedBytes();
        r.run();
        return mx.getCurrentThreadAllocatedBytes() - before;
    }

    /**
     * A registry shaped like the per-index part of a real scrape: 60 gauge families, each with a
     * {@code total} and a {@code primaries} sample per index. Each index adds about 14 KB of output,
     * so 400 to 800 indices come to 6 to 11 MB.
     */
    private static CollectorRegistry indicesRegistry(int indices) {
        CollectorRegistry registry = new CollectorRegistry();
        int families = 60;
        Gauge[] gauges = new Gauge[families];
        for (int f = 0; f < families; f++) {
            gauges[f] = Gauge.build()
                    .name("opensearch_index_metric_" + f)
                    .help("Index metric number " + f)
                    .labelNames("cluster", "index", "context")
                    .register(registry);
        }
        for (int i = 0; i < indices; i++) {
            String index = "my-application-logs-2026.10." + i;
            for (int f = 0; f < families; f++) {
                gauges[f].labels("test-cluster", index, "total").set(i * 1000.5d + f);
                gauges[f].labels("test-cluster", index, "primaries").set(i * 10.25d + f);
            }
        }
        return registry;
    }

    /**
     * Metrics kept outside the catalog, such as the action filter's, are rendered in the same document.
     */
    public void testRegisteredCollectorIsRendered() throws IOException {
        PrometheusMetricsCatalog catalog = buildReferenceCatalog();
        PrometheusActionFilter filter = new PrometheusActionFilter("opensearch_");
        catalog.registerCollector(filter.collector("test-cluster", "node-1", "AbCdEf0123456789"));

        String actual = catalog.toTextFormatBytes().utf8ToString();
        assertTrue(actual, actual.contains("# TYPE opensearch_action_latency_seconds histogram\n"));
        assertTrue(actual, actual.contains("opensearch_action_requests_total{cluster=\"test-cluster\",node=\"node-1\","
                + "nodeid=\"AbCdEf0123456789\",action=\"indices:data/read/search\",outcome=\"success\",} 0.0\n"));
    }

    /**
     * Registering a collector that provides a name the catalog already has fails, rather than rendering
     * the metric twice.
     */
    public void testRegisteredCollectorMustNotShareMetricNames() {
        PrometheusMetricsCatalog catalog = buildReferenceCatalog();
        PrometheusActionFilter filter = new PrometheusActionFilter("opensearch_");
        catalog.registerCollector(filter.collector("test-cluster", "node-1", "AbCdEf0123456789"));

        expectThrows(IllegalArgumentException.class,
                () -> catalog.registerCollector(filter.collector("test-cluster", "node-1", "AbCdEf0123456789")));
    }

    /**
     * The registry checks only the names a collector describes, so a metric added to the action filter but
     * left out of its description would be rendered without being checked for a clash.
     */
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
