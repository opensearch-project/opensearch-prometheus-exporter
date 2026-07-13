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
 *
 * <p>Covers both OpenSearch 2.x (flat-key) and OpenSearch 3.x (nested object) response formats.
 * Uses an in-process stub to avoid real HTTP calls.
 */
public class ISMMetricsCollectorTests extends OpenSearchTestCase {

    // ─────────────────────────────────────────────────────────────────────────
    // Test infrastructure
    // ─────────────────────────────────────────────────────────────────────────

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

    private PrometheusMetricsCatalog newCatalog() {
        return new PrometheusMetricsCatalog("test-cluster", "opensearch_");
    }

    private StubISMMetricsCollector newCollector(boolean perIndex) {
        StubISMMetricsCollector c = new StubISMMetricsCollector(newCatalog(), perIndex);
        c.registerMetrics();
        return c;
    }

    private StubISMMetricsCollector newCollector() {
        return newCollector(false);
    }

    // ── 3.x nested state object: {"name": "...", "start_time": ...}
    private Map<String, Object> stateObj(String name) {
        Map<String, Object> s = new HashMap<>();
        s.put("name", name);
        s.put("start_time", 1700000000000L);
        return s;
    }

    // ── 3.x nested action object
    private Map<String, Object> actionObj(String name, int consumedRetries, boolean failed) {
        Map<String, Object> a = new HashMap<>();
        a.put("name", name);
        a.put("start_time", 1700000100000L);
        a.put("index", 0);
        a.put("failed", failed);
        a.put("consumed_retries", consumedRetries);
        return a;
    }

    // ── 3.x nested step object
    private Map<String, Object> stepObj(String name, String stepStatus) {
        Map<String, Object> s = new HashMap<>();
        s.put("name", name);
        s.put("start_time", 1700000200000L);
        s.put("step_status", stepStatus);
        return s;
    }

    /** Builds a 3.x-format index entry for the /explain response. */
    private Map<String, Object> indexInfo3x(String policyId, String stateName,
                                             String stepName, String stepStatus,
                                             String actionName, int consumedRetries) {
        Map<String, Object> info = new HashMap<>();
        info.put("policy_id", policyId);
        info.put("state", stateObj(stateName));
        info.put("step", stepObj(stepName, stepStatus));
        info.put("action", actionObj(actionName, consumedRetries, false));
        info.put("rolled_over", false);
        return info;
    }

    /** Builds a 3.x-format index entry without action (for edge-case tests). */
    private Map<String, Object> indexInfo3xNoAction(String policyId, String stateName,
                                                     String stepStatus) {
        Map<String, Object> info = new HashMap<>();
        info.put("policy_id", policyId);
        info.put("state", stateObj(stateName));
        info.put("step", stepObj("check_conditions", stepStatus));
        return info;
    }

    /** Builds a 2.x flat-key format index entry. */
    private Map<String, Object> indexInfo2x(String policyId, String stateName) {
        Map<String, Object> info = new HashMap<>();
        info.put("index.plugins.index_state_management.policy_id", policyId);
        info.put("index.plugins.index_state_management.state.name", stateName);
        return info;
    }

    /** Builds a 2.x flat-key entry with per-index action data. */
    private Map<String, Object> indexInfo2xWithAction(String policyId, String stateName,
                                                       String actionName, int consumedRetries) {
        Map<String, Object> info = indexInfo2x(policyId, stateName);
        info.put("index.plugins.index_state_management.action.name", actionName);
        // 7000 ms → 7 s: small value keeps Double.toString() in plain (non-scientific) notation
        info.put("index.plugins.index_state_management.step.start_time", 7000L);
        Map<String, Object> retryInfo = new HashMap<>();
        retryInfo.put("consumed_retries", consumedRetries);
        retryInfo.put("failed", false);
        info.put("index.plugins.index_state_management.action.retry_info", retryInfo);
        return info;
    }

    private Map<String, Object> failedEndpointResponse(int total, String... indexPolicyPairs) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("total_failed_managed_indices", total);
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < indexPolicyPairs.length; i += 2) {
            Map<String, Object> e = new HashMap<>();
            e.put("index_name", indexPolicyPairs[i]);
            e.put("policy_id", indexPolicyPairs[i + 1]);
            list.add(e);
        }
        resp.put("failed_indices", list);
        return resp;
    }

    private Map<String, Object> policiesResponse(String... policyIds) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("total_policies", policyIds.length);
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : policyIds) {
            Map<String, Object> p = new HashMap<>();
            p.put("_id", id);
            list.add(p);
        }
        resp.put("policies", list);
        return resp;
    }

    private Map<String, Object> explainResponse(Map<String, Object>... entries) {
        // entries come in pairs: name, infoMap — not ideal, use builder pattern instead
        throw new UnsupportedOperationException("use explainResponseMap");
    }

    private Map<String, Object> explainResponseMap(int total, Map<String, Map<String, Object>> indices) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("total_managed_indices", total);
        resp.putAll(indices);
        return resp;
    }

    private String metricsText(StubISMMetricsCollector c) throws IOException {
        // access catalog through updateMetrics side-effects — use a fresh catalog approach
        // Instead, we call the specific update method and read text from the catalog.
        // Catalog is not directly accessible; we rely on the collector calling catalog methods
        // and then extracting text from the catalog. Since catalog is private, we expose it
        // via the collector's parent class. Work around by calling updateMetrics on the stub.
        return null; // callers must use catalog directly
    }

    // ─────────────────────────────────────────────────────────────────────────
    // registerMetrics
    // ─────────────────────────────────────────────────────────────────────────

    public void testRegisterMetrics_noPerIndex_doesNotThrow() {
        ISMMetricsCollector c = new StubISMMetricsCollector(newCatalog(), false);
        c.registerMetrics();
    }

    public void testRegisterMetrics_withPerIndex_doesNotThrow() {
        ISMMetricsCollector c = new StubISMMetricsCollector(newCatalog(), true);
        c.registerMetrics();
    }

    public void testRegisterMetrics_noPerIndex_metricNamesPresent() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        ISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();
        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_policy_count"));
        assertTrue(text.contains("ism_managed_indices_total"));
        assertTrue(text.contains("ism_failed_indices_total"));
        assertFalse("per-index metric must not be present without perIndex=true",
                text.contains("ism_index_managed_bool"));
    }

    public void testRegisterMetrics_withPerIndex_perIndexMetricNamesPresent() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        ISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();
        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_index_managed_bool"));
        assertTrue(text.contains("ism_index_last_update_seconds"));
        assertTrue(text.contains("ism_index_retry_count"));
        assertTrue(text.contains("ism_index_step_failed_bool"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updatePolicyMetrics
    // ─────────────────────────────────────────────────────────────────────────

    public void testPolicyMetrics_count() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        c.updatePolicyMetrics(policiesResponse("pol-a", "pol-b"));

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_policy_count{cluster=\"test-cluster\",} 2"));
    }

    public void testPolicyMetrics_enabledBoolPerPolicy() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        c.updatePolicyMetrics(policiesResponse("alpha", "beta"));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_policy_enabled_bool{cluster=\"test-cluster\",policy=\"alpha\",} 1"));
        assertTrue(text.contains(
                "ism_policy_enabled_bool{cluster=\"test-cluster\",policy=\"beta\",} 1"));
    }

    public void testPolicyMetrics_empty() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        c.updatePolicyMetrics(policiesResponse());

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_policy_count{cluster=\"test-cluster\",} 0"));
    }

    public void testPolicyMetrics_nonNumberTotalDefaultsToZero() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Object> resp = new HashMap<>();
        resp.put("total_policies", "not-a-number");
        resp.put("policies", new ArrayList<>());
        c.updatePolicyMetrics(resp);

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_policy_count{cluster=\"test-cluster\",} 0"));
    }

    public void testPolicyMetrics_missingPoliciesField_doesNotThrow() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Object> resp = new HashMap<>();
        resp.put("total_policies", 1);
        // no "policies" key
        c.updatePolicyMetrics(resp);

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_policy_count{cluster=\"test-cluster\",} 1"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateExplainMetrics — aggregation (2.x flat-key format)
    // ─────────────────────────────────────────────────────────────────────────

    public void testExplain_2x_managedTotal() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("idx-1", indexInfo2x("pol-a", "hot"));
        indices.put("idx-2", indexInfo2x("pol-a", "warm"));
        c.updateExplainMetrics(explainResponseMap(2, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_managed_indices_total{cluster=\"test-cluster\",} 2"));
    }

    public void testExplain_2x_countByPolicy() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("idx-1", indexInfo2x("pol-a", "hot"));
        indices.put("idx-2", indexInfo2x("pol-a", "warm"));
        indices.put("idx-3", indexInfo2x("pol-b", "hot"));
        c.updateExplainMetrics(explainResponseMap(3, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol-a\",} 2"));
        assertTrue(text.contains(
                "ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol-b\",} 1"));
    }

    public void testExplain_2x_countByPolicyAndState() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("idx-1", indexInfo2x("pol-a", "hot"));
        indices.put("idx-2", indexInfo2x("pol-a", "hot"));
        indices.put("idx-3", indexInfo2x("pol-a", "warm"));
        c.updateExplainMetrics(explainResponseMap(3, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_managed_indices_by_state_total{cluster=\"test-cluster\",policy=\"pol-a\",state=\"hot\",} 2"));
        assertTrue(text.contains(
                "ism_managed_indices_by_state_total{cluster=\"test-cluster\",policy=\"pol-a\",state=\"warm\",} 1"));
    }

    public void testExplain_2x_skipsIndicesWithNoPolicyId() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Object> noPolicy = new HashMap<>();
        // policy_id explicitly null — simulates unmanaged index in 3.x explain response
        noPolicy.put("policy_id", null);
        noPolicy.put("enabled", null);

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("unmanaged", noPolicy);
        indices.put("managed", indexInfo2x("pol-a", "hot"));
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol-a\",} 1"));
        // unmanaged index must not inflate the count
        assertFalse(text.contains("ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol-a\",} 2"));
    }

    public void testExplain_skipsNonMapEntries() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Object> resp = new HashMap<>();
        resp.put("total_managed_indices", 1);
        resp.put("some_string_field", "not-a-map");
        resp.put("idx-1", indexInfo2x("pol-a", "hot"));
        c.updateExplainMetrics(resp);

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol-a\",} 1"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateExplainMetrics — aggregation (3.x nested format)
    // ─────────────────────────────────────────────────────────────────────────

    public void testExplain_3x_countByPolicy() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("fast-rollover-000001", indexInfo3x("fast_rollover", "hot",
                "attempt_rollover", "completed", "rollover", 0));
        indices.put("fast-rollover-000002", indexInfo3x("fast_rollover", "warm",
                "set_read_only", "completed", "read_only", 0));
        indices.put("tiered-000001", indexInfo3x("tiered", "hot",
                "attempt_rollover", "completed", "rollover", 0));
        c.updateExplainMetrics(explainResponseMap(3, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"fast_rollover\",} 2"));
        assertTrue(text.contains(
                "ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"tiered\",} 1"));
    }

    public void testExplain_3x_countByState() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("idx-hot-1", indexInfo3x("pol", "hot", "attempt_rollover", "completed", "rollover", 0));
        indices.put("idx-warm-1", indexInfo3x("pol", "warm", "set_read_only", "completed", "read_only", 0));
        indices.put("idx-cold-1", indexInfo3x("pol", "cold", "index_priority", "completed", "index_priority", 0));
        c.updateExplainMetrics(explainResponseMap(3, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_managed_indices_by_state_total{cluster=\"test-cluster\",policy=\"pol\",state=\"hot\",} 1"));
        assertTrue(text.contains(
                "ism_managed_indices_by_state_total{cluster=\"test-cluster\",policy=\"pol\",state=\"warm\",} 1"));
        assertTrue(text.contains(
                "ism_managed_indices_by_state_total{cluster=\"test-cluster\",policy=\"pol\",state=\"cold\",} 1"));
    }

    public void testExplain_3x_policyIdFromTopLevel() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        // 3.x: policy_id is top-level, NOT under the flat key
        Map<String, Object> info = new HashMap<>();
        info.put("policy_id", "my-policy");
        info.put("state", stateObj("hot"));

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("idx-1", info);
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"my-policy\",} 1"));
    }

    public void testExplain_3x_fallbackTo2xFlatKeyForPolicyId() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        // Only flat-key policy_id is set (2.x style); top-level policy_id absent
        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("idx-1", indexInfo2x("legacy-policy", "rollover"));
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"legacy-policy\",} 1"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateExplainMetrics — failed count derived from step.step_status
    // ─────────────────────────────────────────────────────────────────────────

    public void testExplain_failedTotal_derivedFromStepStatus() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("fail-1", indexInfo3x("failing_policy", "rollover",
                "attempt_rollover", "failed", "rollover", 3));
        indices.put("fail-2", indexInfo3x("failing_policy", "rollover",
                "attempt_rollover", "failed", "rollover", 3));
        indices.put("ok-1", indexInfo3x("fast_rollover", "hot",
                "attempt_rollover", "completed", "rollover", 0));
        c.updateExplainMetrics(explainResponseMap(3, indices));

        String text = catalog.toTextFormat();
        assertTrue("failed total must be 2",
                text.contains("ism_failed_indices_total{cluster=\"test-cluster\",} 2"));
    }

    public void testExplain_failedTotal_zeroWhenNoFailedSteps() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("ok-1", indexInfo3x("pol", "hot", "attempt_rollover", "completed", "rollover", 0));
        indices.put("ok-2", indexInfo3x("pol", "warm", "set_read_only", "completed", "read_only", 0));
        c.updateExplainMetrics(explainResponseMap(2, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_failed_indices_total{cluster=\"test-cluster\",} 0"));
    }

    public void testExplain_failedByPolicy_derivedFromStepStatus() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("a-fail-1", indexInfo3x("pol-a", "rollover",
                "attempt_rollover", "failed", "rollover", 3));
        indices.put("a-fail-2", indexInfo3x("pol-a", "rollover",
                "attempt_rollover", "failed", "rollover", 3));
        indices.put("b-fail-1", indexInfo3x("pol-b", "rollover",
                "attempt_rollover", "failed", "rollover", 1));
        indices.put("b-ok-1", indexInfo3x("pol-b", "hot",
                "attempt_rollover", "completed", "rollover", 0));
        c.updateExplainMetrics(explainResponseMap(4, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_failed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol-a\",} 2"));
        assertTrue(text.contains(
                "ism_failed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol-b\",} 1"));
    }

    public void testExplain_failedCount_ignoredWhenNoStepObject() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        // Index with policy_id and state but no step — must not count as failed
        Map<String, Object> info = new HashMap<>();
        info.put("policy_id", "pol");
        info.put("state", stateObj("hot"));
        // no "step" key

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("idx-no-step", info);
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_failed_indices_total{cluster=\"test-cluster\",} 0"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateExplainMetrics — per-index metrics, 3.x nested format
    // ─────────────────────────────────────────────────────────────────────────

    public void testPerIndex_3x_managedBoolAlways1() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("my-index", indexInfo3x("pol", "hot",
                "attempt_rollover", "completed", "rollover", 0));
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_index_managed_bool{cluster=\"test-cluster\",index=\"my-index\",policy=\"pol\",} 1"));
    }

    public void testPerIndex_3x_consumedRetries() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("fail-idx", indexInfo3x("failing_policy", "rollover",
                "attempt_rollover", "failed", "rollover", 3));
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue("consumed_retries from action object must be 3",
                text.contains("ism_index_retry_count{cluster=\"test-cluster\","
                        + "index=\"fail-idx\",policy=\"failing_policy\",action=\"rollover\",} 3"));
    }

    public void testPerIndex_3x_stepStartTime() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        Map<String, Object> info = indexInfo3x("pol", "hot",
                "attempt_rollover", "completed", "rollover", 0);
        // Use small value so Double.toString() stays in plain notation: 5000 ms → 5 s
        ((Map<String, Object>) info.get("step")).put("start_time", 5000L);

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("my-index", info);
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue("last_update_seconds should be start_time_ms / 1000",
                text.contains("ism_index_last_update_seconds{cluster=\"test-cluster\","
                        + "index=\"my-index\",policy=\"pol\",} 5"));
    }

    public void testPerIndex_3x_stepFailedBool_true() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("fail-idx", indexInfo3x("pol", "rollover",
                "attempt_rollover", "failed", "rollover", 3));
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_index_step_failed_bool{cluster=\"test-cluster\","
                        + "index=\"fail-idx\",policy=\"pol\",step=\"attempt_rollover\",} 1"));
    }

    public void testPerIndex_3x_stepFailedBool_false_whenCompleted() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("ok-idx", indexInfo3x("pol", "hot",
                "attempt_rollover", "completed", "rollover", 0));
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_index_step_failed_bool{cluster=\"test-cluster\","
                        + "index=\"ok-idx\",policy=\"pol\",step=\"attempt_rollover\",} 0"));
    }

    public void testPerIndex_3x_multipleIndicesDifferentRetryCount() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("fail-1", indexInfo3x("pol", "rollover", "attempt_rollover", "failed", "rollover", 3));
        indices.put("fail-2", indexInfo3x("pol", "rollover", "attempt_rollover", "failed", "rollover", 1));
        indices.put("ok-1",   indexInfo3x("pol", "hot",      "attempt_rollover", "completed", "rollover", 0));
        c.updateExplainMetrics(explainResponseMap(3, indices));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_index_retry_count{cluster=\"test-cluster\","
                        + "index=\"fail-1\",policy=\"pol\",action=\"rollover\",} 3"));
        assertTrue(text.contains(
                "ism_index_retry_count{cluster=\"test-cluster\","
                        + "index=\"fail-2\",policy=\"pol\",action=\"rollover\",} 1"));
        assertTrue(text.contains(
                "ism_index_retry_count{cluster=\"test-cluster\","
                        + "index=\"ok-1\",policy=\"pol\",action=\"rollover\",} 0"));
    }

    public void testPerIndex_3x_noAction_doesNotThrow() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("idx", indexInfo3xNoAction("pol", "hot", "completed"));
        c.updateExplainMetrics(explainResponseMap(1, indices));
        // must not throw; managed_bool still set
        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_index_managed_bool"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateExplainMetrics — per-index metrics, 2.x flat-key fallback
    // ─────────────────────────────────────────────────────────────────────────

    public void testPerIndex_2x_retryCount_viaFlatKeyFallback() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("logs-000001", indexInfo2xWithAction("retention_7d", "rollover", "rollover", 2));
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        assertTrue("2.x flat-key retry_info.consumed_retries must be read",
                text.contains("ism_index_retry_count{cluster=\"test-cluster\","
                        + "index=\"logs-000001\",policy=\"retention_7d\",action=\"rollover\",} 2"));
    }

    public void testPerIndex_2x_stepStartTime_viaFlatKeyFallback() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("logs-000001", indexInfo2xWithAction("pol", "rollover", "rollover", 0));
        c.updateExplainMetrics(explainResponseMap(1, indices));

        String text = catalog.toTextFormat();
        // flat-key step.start_time is 7000 ms → 7 s
        assertTrue(text.contains("ism_index_last_update_seconds{cluster=\"test-cluster\","
                + "index=\"logs-000001\",policy=\"pol\",} 7"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateFailedMetrics (2.x /failed_indices endpoint)
    // ─────────────────────────────────────────────────────────────────────────

    public void testFailedMetrics_total() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        c.updateFailedMetrics(failedEndpointResponse(2,
                "logs-fail-1", "pol-a",
                "logs-fail-2", "pol-a"));

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_failed_indices_total{cluster=\"test-cluster\",} 2"));
    }

    public void testFailedMetrics_byPolicy() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        c.updateFailedMetrics(failedEndpointResponse(3,
                "idx-a1", "pol-a",
                "idx-a2", "pol-a",
                "idx-b1", "pol-b"));

        String text = catalog.toTextFormat();
        assertTrue(text.contains(
                "ism_failed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol-a\",} 2"));
        assertTrue(text.contains(
                "ism_failed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol-b\",} 1"));
    }

    public void testFailedMetrics_zero() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        c.updateFailedMetrics(failedEndpointResponse(0));

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_failed_indices_total{cluster=\"test-cluster\",} 0"));
    }

    public void testFailedMetrics_missingFailedIndicesField_doesNotThrow() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        Map<String, Object> resp = new HashMap<>();
        resp.put("total_failed_managed_indices", 1);
        // no "failed_indices" key
        c.updateFailedMetrics(resp);
        // must not throw; total is still set
        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_failed_indices_total{cluster=\"test-cluster\",} 1"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateMetrics — full flow
    // ─────────────────────────────────────────────────────────────────────────

    public void testFullFlow_allNull_doesNotThrow() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();
        // no stubs → all fetchJson return null
        c.updateMetrics();
        // gauges are initialized to 0 on register; updateMetrics must not throw
        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_policy_count"));
    }

    public void testFullFlow_3x_noFailedEndpoint_failedDerivedFromExplain() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        c.stubResponse(ISMMetricsCollector.ISM_POLICIES_ENDPOINT,
                policiesResponse("fast_rollover", "failing_policy"));

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("fast-rollover-000001", indexInfo3x("fast_rollover", "hot",
                "attempt_rollover", "completed", "rollover", 0));
        indices.put("fail-no-alias-001", indexInfo3x("failing_policy", "rollover",
                "attempt_rollover", "failed", "rollover", 3));
        c.stubResponse(ISMMetricsCollector.ISM_EXPLAIN_ENDPOINT,
                explainResponseMap(2, indices));
        // ISM_FAILED_ENDPOINT not stubbed → null (simulates 3.x where endpoint is removed)

        c.updateMetrics();

        String text = catalog.toTextFormat();
        assertTrue("policy count = 2",
                text.contains("ism_policy_count{cluster=\"test-cluster\",} 2"));
        assertTrue("managed total = 2",
                text.contains("ism_managed_indices_total{cluster=\"test-cluster\",} 2"));
        assertTrue("failed total derived from explain = 1",
                text.contains("ism_failed_indices_total{cluster=\"test-cluster\",} 1"));
        assertTrue("failed by policy derived from explain",
                text.contains("ism_failed_indices_by_policy_total{cluster=\"test-cluster\","
                        + "policy=\"failing_policy\",} 1"));
    }

    public void testFullFlow_2x_failedEndpointOverridesExplainDerived() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        c.stubResponse(ISMMetricsCollector.ISM_POLICIES_ENDPOINT, policiesResponse("pol"));

        // explain says 1 failed via step_status
        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("fail-1", indexInfo3x("pol", "rollover",
                "attempt_rollover", "failed", "rollover", 3));
        c.stubResponse(ISMMetricsCollector.ISM_EXPLAIN_ENDPOINT,
                explainResponseMap(1, indices));

        // /failed_indices endpoint (2.x) says 3 — this should override the explain-derived value
        c.stubResponse(ISMMetricsCollector.ISM_FAILED_ENDPOINT,
                failedEndpointResponse(3, "fail-1", "pol", "fail-2", "pol", "fail-3", "pol"));

        c.updateMetrics();

        String text = catalog.toTextFormat();
        assertTrue("failed endpoint overrides explain-derived count → 3",
                text.contains("ism_failed_indices_total{cluster=\"test-cluster\",} 3"));
    }

    public void testFullFlow_perIndex_3x_withFailures() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, true);
        c.registerMetrics();

        c.stubResponse(ISMMetricsCollector.ISM_POLICIES_ENDPOINT,
                policiesResponse("failing_policy"));

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("fail-no-alias-001", indexInfo3x("failing_policy", "rollover",
                "attempt_rollover", "failed", "rollover", 3));
        c.stubResponse(ISMMetricsCollector.ISM_EXPLAIN_ENDPOINT,
                explainResponseMap(1, indices));

        c.updateMetrics();

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_index_managed_bool{cluster=\"test-cluster\","
                + "index=\"fail-no-alias-001\",policy=\"failing_policy\",} 1"));
        assertTrue(text.contains("ism_index_retry_count{cluster=\"test-cluster\","
                + "index=\"fail-no-alias-001\",policy=\"failing_policy\",action=\"rollover\",} 3"));
        assertTrue(text.contains("ism_index_step_failed_bool{cluster=\"test-cluster\","
                + "index=\"fail-no-alias-001\",policy=\"failing_policy\",step=\"attempt_rollover\",} 1"));
    }

    public void testFullFlow_partialEndpointsNull() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        // only explain is available, policies and failed are null
        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("idx-1", indexInfo3x("pol", "hot", "attempt_rollover", "completed", "rollover", 0));
        c.stubResponse(ISMMetricsCollector.ISM_EXPLAIN_ENDPOINT,
                explainResponseMap(1, indices));

        c.updateMetrics();

        String text = catalog.toTextFormat();
        assertTrue(text.contains("ism_managed_indices_total{cluster=\"test-cluster\",} 1"));
        assertTrue(text.contains("ism_managed_indices_by_policy_total{cluster=\"test-cluster\",policy=\"pol\",} 1"));
    }

    public void testFullFlow_multipleStates_allFourLifecycle() throws IOException {
        PrometheusMetricsCatalog catalog = newCatalog();
        StubISMMetricsCollector c = new StubISMMetricsCollector(catalog, false);
        c.registerMetrics();

        c.stubResponse(ISMMetricsCollector.ISM_POLICIES_ENDPOINT,
                policiesResponse("tiered_lifecycle"));

        Map<String, Map<String, Object>> indices = new HashMap<>();
        indices.put("tiered-hot",  indexInfo3x("tiered_lifecycle", "hot",  "attempt_rollover", "completed", "rollover", 0));
        indices.put("tiered-warm", indexInfo3x("tiered_lifecycle", "warm", "index_priority",   "completed", "index_priority", 0));
        indices.put("tiered-cold", indexInfo3x("tiered_lifecycle", "cold", "index_priority",   "completed", "index_priority", 0));
        c.stubResponse(ISMMetricsCollector.ISM_EXPLAIN_ENDPOINT,
                explainResponseMap(3, indices));

        c.updateMetrics();

        String text = catalog.toTextFormat();
        for (String state : new String[]{"hot", "warm", "cold"}) {
            assertTrue("state=" + state + " must appear",
                    text.contains("ism_managed_indices_by_state_total{cluster=\"test-cluster\","
                            + "policy=\"tiered_lifecycle\",state=\"" + state + "\",} 1"));
        }
    }
}
