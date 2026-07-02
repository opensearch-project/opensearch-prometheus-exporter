/*
 * Copyright [2024] [OpenSearch Contributors]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.compuscene.metrics.prometheus;

import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link ISMMetricsCollector}.
 * Uses an in-process stub to avoid real HTTP calls.
 */
public class ISMMetricsCollectorTests extends OpenSearchTestCase {

    /**
     * Stub that injects pre-built responses instead of making real HTTP calls.
     */
    private static class StubISMMetricsCollector extends ISMMetricsCollector {

        private final Map<String, Map<String, Object>> responses = new HashMap<>();

        StubISMMetricsCollector(PrometheusMetricsCatalog catalog, boolean perIndex) {
            super(catalog, "http://stub:9200", perIndex);
        }

        void stubResponse(String endpoint, Map<String, Object> response) {
            responses.put(endpoint, response);
        }

        @Override
        Map<String, Object> fetchJson(String endpoint) {
            return responses.get(endpoint);
        }
    }

    /**
     * Creates a fresh catalog for tests.
     */
    private PrometheusMetricsCatalog newCatalog() {
        return new PrometheusMetricsCatalog("test-cluster", "opensearch_");
    }

    // ── registerMetrics ──────────────────────────────────────────────────────

    /**
     * registerMetrics should not throw and should succeed for both perIndex modes.
     */
    public void testRegisterMetricsNoPerIndex() {
        ISMMetricsCollector collector = new StubISMMetricsCollector(newCatalog(), false);
        collector.registerMetrics(); // must not throw
    }

    /**
     * registerMetrics with perIndex=true registers additional per-index gauges.
     */
    public void testRegisterMetricsWithPerIndex() {
        ISMMetricsCollector collector = new StubISMMetricsCollector(newCatalog(), true);
        collector.registerMetrics();
    }

    // ── updatePolicyMetrics ──────────────────────────────────────────────────

    /**
     * updatePolicyMetrics sets ism_policy_count and ism_policy_enabled_bool for each policy.
     */
    public void testUpdatePolicyMetrics() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector collector = new StubISMMetricsCollector(catalog, false);
        collector.registerMetrics();

        Map<String, Object> response = new HashMap<>();
        response.put("total_policies", 2);
        List<Map<String, Object>> policies = new ArrayList<>();
        Map<String, Object> policy1 = new HashMap<>();
        policy1.put("_id", "retention_7d");
        policies.add(policy1);
        Map<String, Object> policy2 = new HashMap<>();
        policy2.put("_id", "retention_30d");
        policies.add(policy2);
        response.put("policies", policies);

        collector.updatePolicyMetrics(response);

        String text = catalog.toTextFormat();
        assertTrue("policy count metric present", text.contains("ism_policy_count"));
        assertTrue("retention_7d label present", text.contains("retention_7d"));
        assertTrue("retention_30d label present", text.contains("retention_30d"));
        assertTrue("policy enabled value is 1",
                text.contains("ism_policy_enabled_bool{cluster=\"test-cluster\",policy=\"retention_7d\"} 1.0"));
    }

    /**
     * updatePolicyMetrics handles empty policies list without throwing.
     */
    public void testUpdatePolicyMetricsEmpty() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector collector = new StubISMMetricsCollector(catalog, false);
        collector.registerMetrics();

        Map<String, Object> response = new HashMap<>();
        response.put("total_policies", 0);
        response.put("policies", new ArrayList<>());

        collector.updatePolicyMetrics(response);

        String text = catalog.toTextFormat();
        assertTrue("policy count is 0", text.contains("ism_policy_count{cluster=\"test-cluster\"} 0.0"));
    }

    // ── updateExplainMetrics ─────────────────────────────────────────────────

    /**
     * updateExplainMetrics aggregates managed indices by policy and state.
     */
    public void testUpdateExplainMetricsAggregation() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector collector = new StubISMMetricsCollector(catalog, false);
        collector.registerMetrics();

        Map<String, Object> response = new HashMap<>();
        response.put("total_managed_indices", 3);

        response.put("logs-000001", buildIndexInfo("retention_7d", "rollover", true));
        response.put("logs-000002", buildIndexInfo("retention_7d", "set_read_only", true));
        response.put("logs-000003", buildIndexInfo("retention_30d", "rollover", true));

        collector.updateExplainMetrics(response);

        String text = catalog.toTextFormat();
        assertTrue("total managed indices set", text.contains("ism_managed_indices_total"));
        assertTrue("retention_7d policy count present",
                text.contains("ism_managed_indices_by_policy_total{cluster=\"test-cluster\","
                        + "policy=\"retention_7d\"} 2.0"));
        assertTrue("retention_30d policy count present",
                text.contains("ism_managed_indices_by_policy_total{cluster=\"test-cluster\","
                        + "policy=\"retention_30d\"} 1.0"));
        assertTrue("rollover state count for retention_7d present",
                text.contains("ism_managed_indices_by_state_total{cluster=\"test-cluster\","
                        + "policy=\"retention_7d\",state=\"rollover\"} 1.0"));
    }

    /**
     * updateExplainMetrics with perIndex=true populates per-index gauges.
     */
    public void testUpdateExplainMetricsPerIndex() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector collector = new StubISMMetricsCollector(catalog, true);
        collector.registerMetrics();

        Map<String, Object> indexInfo = buildIndexInfo("retention_7d", "rollover", true);
        indexInfo.put("index.plugins.index_state_management.step.start_time", 1700000000000L);
        indexInfo.put("index.plugins.index_state_management.action.name", "rollover");
        Map<String, Object> retryInfo = new HashMap<>();
        retryInfo.put("consumed_retries", 2);
        indexInfo.put("index.plugins.index_state_management.action.retry_info", retryInfo);

        Map<String, Object> response = new HashMap<>();
        response.put("total_managed_indices", 1);
        response.put("logs-000001", indexInfo);

        collector.updateExplainMetrics(response);

        String text = catalog.toTextFormat();
        assertTrue("per-index managed_bool present",
                text.contains("ism_index_managed_bool{cluster=\"test-cluster\","
                        + "index=\"logs-000001\",policy=\"retention_7d\"} 1.0"));
        assertTrue("per-index last_update_seconds present",
                text.contains("ism_index_last_update_seconds"));
        assertTrue("per-index retry_count present",
                text.contains("ism_index_retry_count{cluster=\"test-cluster\","
                        + "index=\"logs-000001\",policy=\"retention_7d\",action=\"rollover\"} 2.0"));
    }

    /**
     * updateExplainMetrics ignores entries with managed=false.
     */
    public void testUpdateExplainMetricsSkipsUnmanaged() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector collector = new StubISMMetricsCollector(catalog, false);
        collector.registerMetrics();

        Map<String, Object> response = new HashMap<>();
        response.put("total_managed_indices", 0);
        response.put("logs-unmanaged", buildIndexInfo("retention_7d", "rollover", false));

        collector.updateExplainMetrics(response);

        String text = catalog.toTextFormat();
        assertFalse("unmanaged index should not appear in by_policy metric",
                text.contains("ism_managed_indices_by_policy_total{cluster=\"test-cluster\","
                        + "policy=\"retention_7d\"} 1.0"));
    }

    // ── updateFailedMetrics ──────────────────────────────────────────────────

    /**
     * updateFailedMetrics sets total failed count and per-policy breakdown.
     */
    public void testUpdateFailedMetrics() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector collector = new StubISMMetricsCollector(catalog, false);
        collector.registerMetrics();

        Map<String, Object> response = new HashMap<>();
        response.put("total_failed_managed_indices", 2);
        List<Map<String, Object>> failedList = new ArrayList<>();
        failedList.add(buildFailedEntry("logs-fail-1", "retention_7d"));
        failedList.add(buildFailedEntry("logs-fail-2", "retention_7d"));
        response.put("failed_indices", failedList);

        collector.updateFailedMetrics(response);

        String text = catalog.toTextFormat();
        assertTrue("total failed = 2",
                text.contains("ism_failed_indices_total{cluster=\"test-cluster\"} 2.0"));
        assertTrue("per-policy failed = 2",
                text.contains("ism_failed_indices_by_policy_total{cluster=\"test-cluster\","
                        + "policy=\"retention_7d\"} 2.0"));
    }

    /**
     * updateFailedMetrics with zero failures sets the total to 0.
     */
    public void testUpdateFailedMetricsZero() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector collector = new StubISMMetricsCollector(catalog, false);
        collector.registerMetrics();

        Map<String, Object> response = new HashMap<>();
        response.put("total_failed_managed_indices", 0);
        response.put("failed_indices", new ArrayList<>());

        collector.updateFailedMetrics(response);

        String text = catalog.toTextFormat();
        assertTrue("total failed = 0",
                text.contains("ism_failed_indices_total{cluster=\"test-cluster\"} 0.0"));
    }

    // ── updateMetrics full flow ──────────────────────────────────────────────

    /**
     * Full updateMetrics call: all three endpoints return valid data and all metrics are set.
     */
    public void testUpdateMetricsFullFlow() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector collector = new StubISMMetricsCollector(catalog, false);
        collector.registerMetrics();

        // Policies stub
        Map<String, Object> policiesResp = new HashMap<>();
        policiesResp.put("total_policies", 1);
        List<Map<String, Object>> pols = new ArrayList<>();
        Map<String, Object> pol = new HashMap<>();
        pol.put("_id", "retention_7d");
        pols.add(pol);
        policiesResp.put("policies", pols);
        collector.stubResponse(ISMMetricsCollector.ISM_POLICIES_ENDPOINT, policiesResp);

        // Explain stub
        Map<String, Object> explainResp = new HashMap<>();
        explainResp.put("total_managed_indices", 1);
        explainResp.put("logs-000001", buildIndexInfo("retention_7d", "rollover", true));
        collector.stubResponse(ISMMetricsCollector.ISM_EXPLAIN_ENDPOINT, explainResp);

        // Failed indices stub
        Map<String, Object> failedResp = new HashMap<>();
        failedResp.put("total_failed_managed_indices", 0);
        failedResp.put("failed_indices", new ArrayList<>());
        collector.stubResponse(ISMMetricsCollector.ISM_FAILED_ENDPOINT, failedResp);

        collector.updateMetrics();

        String text = catalog.toTextFormat();
        assertTrue("policy count = 1",
                text.contains("ism_policy_count{cluster=\"test-cluster\"} 1.0"));
        assertTrue("managed total = 1",
                text.contains("ism_managed_indices_total{cluster=\"test-cluster\"} 1.0"));
        assertTrue("failed total = 0",
                text.contains("ism_failed_indices_total{cluster=\"test-cluster\"} 0.0"));
    }

    // ── fetchJson (null handling) ────────────────────────────────────────────

    /**
     * updateMetrics handles null responses (e.g. ISM not installed) without throwing.
     */
    public void testUpdateMetricsHandlesNullResponses() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector collector = new StubISMMetricsCollector(catalog, false);
        collector.registerMetrics();
        // no responses set — all fetchJson() return null
        collector.updateMetrics(); // must not throw

        // metrics are registered but not set, so they show 0
        String text = catalog.toTextFormat();
        assertTrue("ISM metrics block present", text.contains("ism_policy_count"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildIndexInfo(String policyId, String stateName, boolean managed) {
        Map<String, Object> info = new HashMap<>();
        info.put("managed", managed);
        info.put("index.plugins.index_state_management.policy_id", policyId);
        info.put("index.plugins.index_state_management.state.name", stateName);
        return info;
    }

    private Map<String, Object> buildFailedEntry(String indexName, String policyId) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("index_name", indexName);
        entry.put("policy_id", policyId);
        return entry;
    }
}
