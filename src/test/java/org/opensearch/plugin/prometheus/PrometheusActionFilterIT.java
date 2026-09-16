/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opensearch.action.admin.cluster.node.info.NodeInfo;
import org.opensearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.opensearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.http.HttpInfo;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tests the metrics accumulated by
 * {@link org.opensearch.plugin.prometheus.filter.PrometheusActionFilter} and exposed through
 * {@code _prometheus/metrics}.
 * <p>
 * The filter only observes actions coordinated by the node it runs on, and the metrics endpoint only
 * reports the node that serves the request. Every request in these tests therefore goes through a client
 * pinned to a single node, so that the node doing the work is also the node being scraped. Using
 * {@link #getRestClient()} would round-robin across the cluster and make the counts non-deterministic.
 */
@SuppressWarnings("deprecation") // RestClient is deprecated in favor of opensearch-java, but it's convenient for integration testing
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class PrometheusActionFilterIT extends OpenSearchIntegTestCase {

    private static final String LATENCY = "opensearch_action_latency_seconds";
    private static final String REQUESTS = "opensearch_action_requests_total";

    private static final String SEARCH_ACTION = "indices:data/read/search";
    private static final String BULK_ACTION = "indices:data/write/bulk";

    private static final Pattern SAMPLE_LINE =
            Pattern.compile("^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\\{(.*)})?\\s+(\\S+)(?:\\s+\\S+)?$");
    private static final Pattern LABEL =
            Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)=\"([^\"]*)\"");

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return List.of(PrometheusExporterPlugin.class);
    }

    /**
     * A successful search increments the success counter and is observed by the histogram exactly once.
     */
    public void testSuccessfulSearchesAreCountedAndTimed() throws IOException, ParseException {
        try (RestClient client = pinnedClient()) {
            String index = "action_filter_search";
            createIndexWithDocs(client, index);

            List<Sample> before = scrape(client);
            int searches = 5;
            for (int i = 0; i < searches; i++) {
                search(client, index);
            }
            List<Sample> after = scrape(client);

            assertEquals("search success counter",
                    searches,
                    delta(before, after, REQUESTS, "action", SEARCH_ACTION, "outcome", "success"),
                    0.0);
            assertEquals("observations recorded by the histogram",
                    searches,
                    delta(before, after, LATENCY + "_count", "action", SEARCH_ACTION),
                    0.0);
            assertEquals("the +Inf bucket must agree with the observation count",
                    searches,
                    delta(before, after, LATENCY + "_bucket", "action", SEARCH_ACTION, "le", "+Inf"),
                    0.0);
            assertTrue("total observed time should have advanced",
                    delta(before, after, LATENCY + "_sum", "action", SEARCH_ACTION) > 0.0);

            // Holds regardless of any concurrent traffic: every success is observed exactly once.
            assertEquals("every successful request must be observed exactly once",
                    delta(before, after, REQUESTS, "action", SEARCH_ACTION, "outcome", "success"),
                    delta(before, after, LATENCY + "_count", "action", SEARCH_ACTION),
                    0.0);
        }
    }

    /**
     * A bulk request is tracked, and the shard-level sub-actions it fans out to are not.
     */
    public void testBulkRequestsAreCounted() throws IOException, ParseException {
        try (RestClient client = pinnedClient()) {
            String index = "action_filter_bulk";
            createIndexWithDocs(client, index);

            List<Sample> before = scrape(client);
            bulkIndex(client, index, 2);
            List<Sample> after = scrape(client);

            assertEquals("bulk success counter",
                    1,
                    delta(before, after, REQUESTS, "action", BULK_ACTION, "outcome", "success"),
                    0.0);

            // A bulk request fans out to indices:data/write/bulk[s] on each participating shard. Matching
            // is by exact action name, so those must not appear; this guards the coordinator-only scope.
            //
            // The expected set covers every tracked action, including the search action that this test
            // never exercises: the filter resolves a child per action up front, so all series exist at zero
            // from node startup. That is deliberate -- it means rate() is meaningful from the first scrape
            // rather than only after the first request of each kind -- so this assertion does not depend on
            // any other test having run first.
            Set<String> tracked = after.stream()
                    .filter(sample -> sample.name.startsWith(LATENCY) || sample.name.startsWith(REQUESTS))
                    .map(sample -> sample.labels.get("action"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            assertEquals("only coordinator-level actions should be tracked",
                    new HashSet<>(Arrays.asList(SEARCH_ACTION, BULK_ACTION)), tracked);
        }
    }

    /**
     * A failing request is counted by outcome, but deliberately not observed by the latency histogram:
     * requests that fail fast would otherwise pull the reported quantiles down.
     */
    public void testFailedSearchIsCountedButNotTimed() throws IOException, ParseException {
        try (RestClient client = pinnedClient()) {
            List<Sample> before = scrape(client);

            Request search = new Request("POST", "/index_that_does_not_exist/_search");
            search.setJsonEntity("{\"query\":{\"match_all\":{}}}");
            ResponseException failure = expectThrows(ResponseException.class, () -> client.performRequest(search));
            assertEquals(404, failure.getResponse().getStatusLine().getStatusCode());

            List<Sample> after = scrape(client);

            assertEquals("a 404 is a client error",
                    1,
                    delta(before, after, REQUESTS, "action", SEARCH_ACTION, "outcome", "client_error"),
                    0.0);
            assertEquals("a failed request is not a success",
                    0,
                    delta(before, after, REQUESTS, "action", SEARCH_ACTION, "outcome", "success"),
                    0.0);
            assertEquals("a failed request must not be observed by the histogram",
                    0,
                    delta(before, after, LATENCY + "_count", "action", SEARCH_ACTION),
                    0.0);
        }
    }

    /**
     * The metrics are cumulative counters. Scraping must not reset them: doing so would be
     * indistinguishable from a process restart to Prometheus, and would mean a second reader of the
     * endpoint saw a different answer.
     */
    public void testScrapingDoesNotResetMetrics() throws IOException, ParseException {
        try (RestClient client = pinnedClient()) {
            String index = "action_filter_cumulative";
            createIndexWithDocs(client, index);
            search(client, index);

            List<Sample> first = scrape(client);
            List<Sample> second = scrape(client);

            double count = value(first, REQUESTS, "action", SEARCH_ACTION, "outcome", "success");
            double sum = value(first, LATENCY + "_sum", "action", SEARCH_ACTION);
            assertTrue("expected at least one search to have been recorded", count > 0.0);
            assertTrue("expected a non-zero observed duration", sum > 0.0);

            assertEquals("counter must survive a scrape",
                    count, value(second, REQUESTS, "action", SEARCH_ACTION, "outcome", "success"), 0.0);
            assertEquals("histogram sum must survive a scrape",
                    sum, value(second, LATENCY + "_sum", "action", SEARCH_ACTION), 0.0);
            assertEquals("histogram observation count must survive a scrape",
                    value(first, LATENCY + "_count", "action", SEARCH_ACTION),
                    value(second, LATENCY + "_count", "action", SEARCH_ACTION), 0.0);
        }
    }

    /**
     * The action metrics must carry the same cluster and node identity as the rest of the plugin's output,
     * and must be declared so that Prometheus reads them as a histogram rather than untyped samples.
     */
    public void testActionMetricsAreLabelledAndTyped() throws IOException, ParseException {
        try (RestClient client = pinnedClient()) {
            String index = "action_filter_labels";
            createIndexWithDocs(client, index);
            search(client, index);

            Response response = client.performRequest(new Request("GET", "/_prometheus/metrics"));
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            assertTrue("histogram type must be declared, so quantiles can be computed",
                    body.contains("# TYPE " + LATENCY + " histogram"));
            assertTrue("counter type must be declared", body.contains("# TYPE " + REQUESTS + " counter"));

            Sample sample = findOne(parse(body), LATENCY + "_count", "action", SEARCH_ACTION);
            for (String label : Arrays.asList("cluster", "node", "nodeid")) {
                String labelValue = sample.labels.get(label);
                assertNotNull("missing " + label + " label on " + sample.name, labelValue);
                assertFalse("empty " + label + " label on " + sample.name, labelValue.isEmpty());
            }
        }
    }

    /**
     * Builds a client bound to a single node, so that the node coordinating a request is also the node
     * whose metrics are read back.
     * <p>
     * The HTTP address is resolved with a node info call rather than from {@code cluster().httpAddresses()}.
     * Under the {@code integTest} task these tests run against an external cluster, which joins it by
     * starting a coordinating-only node inside the test JVM; that node always gets
     * {@code MockHttpTransport}, whose bound address is a dummy {@code 0.0.0.0:0}, and it is what
     * {@code httpAddresses()} reports. Nodes without a real HTTP address are therefore skipped here.
     */
    private RestClient pinnedClient() {
        NodesInfoResponse nodesInfo = client().admin().cluster()
                .prepareNodesInfo()
                .clear()
                .addMetric(NodesInfoRequest.Metric.HTTP.metricName())
                .get();
        assertFalse("node info request failed: " + nodesInfo.failures(), nodesInfo.hasFailures());
        List<NodeInfo> sortedNodes = new ArrayList<>(nodesInfo.getNodes());
        sortedNodes.sort(Comparator.comparing(n -> n.getNode().getId()));

        for (NodeInfo node : sortedNodes) {
            HttpInfo httpInfo = node.getInfo(HttpInfo.class);
            if (httpInfo == null) {
                continue;
            }
            InetSocketAddress address = httpInfo.address().publishAddress().address();
            if (address.getPort() == 0) {
                continue;
            }
            return RestClient.builder(new HttpHost("http", address.getHostString(), address.getPort())).build();
        }
        throw new AssertionError("no node in the cluster exposes a bound HTTP address");
    }

    private void createIndexWithDocs(RestClient client, String index) throws IOException {
        Request create = new Request("PUT", "/" + index);
        create.setJsonEntity("{\"settings\":{\"index\":{\"number_of_shards\":1,\"number_of_replicas\":0}}}");
        assertEquals(200, client.performRequest(create).getStatusLine().getStatusCode());
        bulkIndex(client, index, 3);
    }

    private void bulkIndex(RestClient client, String index, int documents) throws IOException {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < documents; i++) {
            body.append("{\"index\":{\"_index\":\"").append(index).append("\"}}\n");
            body.append("{\"field\":\"value").append(i).append("\"}\n");
        }
        Request bulk = new Request("POST", "/_bulk");
        bulk.addParameter("refresh", "true");
        bulk.setJsonEntity(body.toString());
        assertEquals(200, client.performRequest(bulk).getStatusLine().getStatusCode());
    }

    private void search(RestClient client, String index) throws IOException {
        Request search = new Request("POST", "/" + index + "/_search");
        search.setJsonEntity("{\"query\":{\"match_all\":{}}}");
        assertEquals(200, client.performRequest(search).getStatusLine().getStatusCode());
    }

    private List<Sample> scrape(RestClient client) throws IOException, ParseException {
        Response response = client.performRequest(new Request("GET", "/_prometheus/metrics"));
        assertEquals(200, response.getStatusLine().getStatusCode());
        return parse(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
    }

    private static double delta(List<Sample> before, List<Sample> after, String name, String... labels) {
        return value(after, name, labels) - value(before, name, labels);
    }

    private static double value(List<Sample> samples, String name, String... labels) {
        return findOne(samples, name, labels).value;
    }

    /**
     * Locates the single sample matching a metric name and a subset of its labels. The cluster and node
     * labels are not known ahead of time, so they are left unconstrained; requiring exactly one match keeps
     * that from silently widening into a match against several series.
     */
    private static Sample findOne(List<Sample> samples, String name, String... labels) {
        assertEquals("labels must be given in name/value pairs", 0, labels.length % 2);
        List<Sample> matches = new ArrayList<>();
        for (Sample sample : samples) {
            if (!sample.name.equals(name)) {
                continue;
            }
            boolean matched = true;
            for (int i = 0; i < labels.length; i += 2) {
                if (!labels[i + 1].equals(sample.labels.get(labels[i]))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                matches.add(sample);
            }
        }
        assertEquals("expected exactly one sample for " + name + " " + Arrays.toString(labels)
                + ", found " + matches, 1, matches.size());
        return matches.getFirst();
    }

    private static List<Sample> parse(String body) {
        List<Sample> samples = new ArrayList<>();
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            Matcher matcher = SAMPLE_LINE.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            Map<String, String> labels = new HashMap<>();
            String labelBlock = matcher.group(2);
            if (labelBlock != null) {
                Matcher labelMatcher = LABEL.matcher(labelBlock);
                while (labelMatcher.find()) {
                    labels.put(labelMatcher.group(1), labelMatcher.group(2));
                }
            }
            samples.add(new Sample(matcher.group(1), labels, parseValue(matcher.group(3))));
        }
        assertFalse("scrape produced no samples", samples.isEmpty());
        return samples;
    }

    /**
     * The text format renders infinities the Go way, which {@link Double#parseDouble} does not accept.
     */
    private static double parseValue(String value) {
        return switch (value) {
            case "+Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            case "NaN" -> Double.NaN;
            default -> Double.parseDouble(value);
        };
    }

    /**
     * A single line of the Prometheus text format.
     */
    private static final class Sample {
        private final String name;
        private final Map<String, String> labels;
        private final double value;

        private Sample(String name, Map<String, String> labels, double value) {
            this.name = name;
            this.labels = labels;
            this.value = value;
        }

        @Override
        public String toString() {
            return name + labels + " " + value;
        }
    }
}
