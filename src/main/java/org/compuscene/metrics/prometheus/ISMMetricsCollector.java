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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class that collects ISM (Index State Management) metrics from the OpenSearch ISM plugin REST API
 * and exposes them as Prometheus metrics via {@link PrometheusMetricsCatalog}.
 *
 * <p>Calls three ISM endpoints:
 * <ul>
 *   <li>{@code /_plugins/_ism/policies} - policy count and enabled status</li>
 *   <li>{@code /_plugins/_ism/explain} - managed indices per policy/state</li>
 *   <li>{@code /_plugins/_ism/failed_indices} - failed indices count</li>
 * </ul>
 *
 * <p>If the ISM plugin is not installed (404 response), all ISM metrics are silently skipped.
 * Enable per-index metrics via {@link PrometheusSettings#PROMETHEUS_ISM_PER_INDEX}.
 */
public class ISMMetricsCollector {

    private static final Logger logger = LogManager.getLogger(ISMMetricsCollector.class);

    static final String ISM_POLICIES_ENDPOINT = "/_plugins/_ism/policies";
    static final String ISM_EXPLAIN_ENDPOINT = "/_plugins/_ism/explain";
    static final String ISM_FAILED_ENDPOINT = "/_plugins/_ism/failed_indices";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int HTTP_OK = 200;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_NOT_FOUND = 404;
    private static final double MILLIS_TO_SECONDS = 1000.0;

    private final PrometheusMetricsCatalog catalog;
    private final String baseUrl;
    private final boolean perIndex;

    /**
     * Creates a new ISMMetricsCollector.
     *
     * @param catalog   the Prometheus metrics catalog to register and update metrics in
     * @param baseUrl   the base URL of the local OpenSearch node (e.g. {@code http://localhost:9200})
     * @param perIndex  whether to expose per-index ISM metrics (retry count, last update time)
     */
    public ISMMetricsCollector(PrometheusMetricsCatalog catalog, String baseUrl, boolean perIndex) {
        this.catalog = catalog;
        this.baseUrl = baseUrl;
        this.perIndex = perIndex;
    }

    /**
     * Registers all ISM-related Prometheus metrics in the catalog.
     * Must be called before {@link #updateMetrics()}.
     */
    public void registerMetrics() {
        catalog.registerClusterGauge("ism_policy_count", "Total number of ISM policies");
        catalog.registerClusterGauge("ism_policy_enabled_bool",
                "Whether the ISM policy is enabled (1=enabled, 0=disabled)", "policy");

        catalog.registerClusterGauge("ism_managed_indices_total",
                "Total number of indices managed by ISM");
        catalog.registerClusterGauge("ism_managed_indices_by_policy_total",
                "Number of ISM managed indices per policy", "policy");
        catalog.registerClusterGauge("ism_managed_indices_by_state_total",
                "Number of ISM managed indices per policy per lifecycle state", "policy", "state");

        catalog.registerClusterGauge("ism_failed_indices_total",
                "Total number of indices with a failed ISM policy execution");
        catalog.registerClusterGauge("ism_failed_indices_by_policy_total",
                "Number of indices with failed ISM policy execution per policy", "policy");

        if (perIndex) {
            catalog.registerClusterGauge("ism_index_managed_bool",
                    "Whether the index is currently managed by ISM (1=yes)", "index", "policy");
            catalog.registerClusterGauge("ism_index_last_update_seconds",
                    "Unix timestamp (seconds) of the last ISM step execution on the index",
                    "index", "policy");
            catalog.registerClusterGauge("ism_index_retry_count",
                    "Number of consumed retries for the last ISM action on the index",
                    "index", "policy", "action");
            catalog.registerClusterGauge("ism_index_step_failed_bool",
                    "Whether the current ISM step is in a failed state (1=failed)",
                    "index", "policy", "step");
        }
    }

    /**
     * Fetches current ISM data from the OpenSearch node and updates all registered metrics.
     * If ISM plugin is not installed or an error occurs, the update is skipped with a warning logged.
     */
    public void updateMetrics() {
        Map<String, Object> policiesResponse = fetchJson(ISM_POLICIES_ENDPOINT);
        if (policiesResponse != null) {
            updatePolicyMetrics(policiesResponse);
        }

        Map<String, Object> explainResponse = fetchJson(ISM_EXPLAIN_ENDPOINT);
        if (explainResponse != null) {
            updateExplainMetrics(explainResponse);
        }

        Map<String, Object> failedResponse = fetchJson(ISM_FAILED_ENDPOINT);
        if (failedResponse != null) {
            updateFailedMetrics(failedResponse);
        }
    }

    /**
     * Fetches and parses a JSON response from the given ISM endpoint path.
     * Returns {@code null} if ISM is not available or any error occurs.
     *
     * @param endpoint the ISM API endpoint path (e.g. {@code /_plugins/_ism/policies})
     * @return parsed JSON as a map, or {@code null} on error
     */
    @SuppressWarnings("removal")
    Map<String, Object> fetchJson(String endpoint) {
        return AccessController.doPrivileged((PrivilegedAction<Map<String, Object>>) () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + endpoint)
                        .toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("Content-Type", "application/json");

                int status = conn.getResponseCode();
                if (status == HTTP_OK) {
                    try (InputStream is = conn.getInputStream();
                         XContentParser parser = JsonXContent.jsonXContent.createParser(
                                 NamedXContentRegistry.EMPTY,
                                 LoggingDeprecationHandler.INSTANCE,
                                 is)) {
                        return parser.map();
                    }
                } else if (status == HTTP_NOT_FOUND || status == HTTP_BAD_REQUEST) {
                    logger.debug("ISM endpoint not available at {}: received {}", endpoint, status);
                } else {
                    logger.warn("ISM API {} returned unexpected status {}", endpoint, status);
                }
            } catch (IOException e) {
                logger.warn("Failed to fetch ISM data from {}: {}", endpoint, e.getMessage());
            } catch (Exception e) {
                logger.warn("Unexpected error fetching ISM data from {}: {} ({})",
                        endpoint, e.getMessage(), e.getClass().getSimpleName());
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    void updatePolicyMetrics(Map<String, Object> response) {
        Object totalObj = response.get("total_policies");
        catalog.setClusterGauge("ism_policy_count",
                totalObj instanceof Number ? ((Number) totalObj).doubleValue() : 0);

        Object policiesObj = response.get("policies");
        if (!(policiesObj instanceof List)) {
            return;
        }
        for (Object item : (List<Object>) policiesObj) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> entry = (Map<String, Object>) item;
            String policyId = (String) entry.get("_id");
            if (policyId != null) {
                catalog.setClusterGauge("ism_policy_enabled_bool", 1, policyId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    void updateExplainMetrics(Map<String, Object> response) {
        Object totalObj = response.get("total_managed_indices");
        catalog.setClusterGauge("ism_managed_indices_total",
                totalObj instanceof Number ? ((Number) totalObj).doubleValue() : 0);

        Map<String, Integer> countByPolicy = new HashMap<>();
        Map<String, Map<String, Integer>> countByPolicyState = new HashMap<>();
        // Derived failed counts from step.step_status — replaces the removed /failed_indices endpoint
        int failedTotal = 0;
        Map<String, Integer> failedByPolicy = new HashMap<>();

        for (Map.Entry<String, Object> entry : response.entrySet()) {
            String indexName = entry.getKey();
            if ("total_managed_indices".equals(indexName)) {
                continue;
            }
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> indexInfo = (Map<String, Object>) entry.getValue();

            // OpenSearch 3.x removed the top-level "managed" field; use "policy_id" presence instead
            String policyId = extractString(indexInfo, "policy_id");
            if (policyId == null) {
                policyId = extractString(indexInfo,
                        "index.plugins.index_state_management.policy_id");
            }
            if (policyId == null) {
                continue;
            }

            // "state" is a nested object {"name": "rollover", "start_time": ...} in OpenSearch 3.x
            String stateName = null;
            Object stateObj = indexInfo.get("state");
            if (stateObj instanceof Map) {
                stateName = extractString((Map<String, Object>) stateObj, "name");
            }
            if (stateName == null) {
                stateName = extractString(indexInfo,
                        "index.plugins.index_state_management.state.name");
            }

            // Derive failed status from step.step_status (works in 3.x without /failed_indices)
            boolean stepFailed = false;
            Object stepObj = indexInfo.get("step");
            if (stepObj instanceof Map) {
                String stepStatus = extractString((Map<String, Object>) stepObj, "step_status");
                stepFailed = "failed".equals(stepStatus);
            }
            if (stepFailed) {
                failedTotal++;
                failedByPolicy.merge(policyId, 1, Integer::sum);
            }

            countByPolicy.merge(policyId, 1, Integer::sum);
            if (stateName != null) {
                countByPolicyState
                        .computeIfAbsent(policyId, k -> new HashMap<>())
                        .merge(stateName, 1, Integer::sum);
            }

            if (perIndex) {
                updatePerIndexMetrics(indexName, policyId, indexInfo, stepFailed);
            }
        }

        for (Map.Entry<String, Integer> e : countByPolicy.entrySet()) {
            catalog.setClusterGauge("ism_managed_indices_by_policy_total", e.getValue(), e.getKey());
        }
        for (Map.Entry<String, Map<String, Integer>> policyEntry : countByPolicyState.entrySet()) {
            for (Map.Entry<String, Integer> stateEntry : policyEntry.getValue().entrySet()) {
                catalog.setClusterGauge("ism_managed_indices_by_state_total",
                        stateEntry.getValue(), policyEntry.getKey(), stateEntry.getKey());
            }
        }
        // Update failed gauges from explain data (fallback when /failed_indices returns 404/400)
        catalog.setClusterGauge("ism_failed_indices_total", failedTotal);
        for (Map.Entry<String, Integer> e : failedByPolicy.entrySet()) {
            catalog.setClusterGauge("ism_failed_indices_by_policy_total", e.getValue(), e.getKey());
        }
    }

    @SuppressWarnings("unchecked")
    private void updatePerIndexMetrics(String indexName, String policyId,
                                       Map<String, Object> indexInfo, boolean stepFailed) {
        catalog.setClusterGauge("ism_index_managed_bool", 1, indexName, policyId);

        // step.start_time: nested (3.x) with flat-key fallback (2.x)
        Object stepStartTime = null;
        Object stepObj = indexInfo.get("step");
        if (stepObj instanceof Map) {
            stepStartTime = ((Map<String, Object>) stepObj).get("start_time");
        }
        if (!(stepStartTime instanceof Number)) {
            stepStartTime = indexInfo.get("index.plugins.index_state_management.step.start_time");
        }
        if (stepStartTime instanceof Number) {
            catalog.setClusterGauge("ism_index_last_update_seconds",
                    ((Number) stepStartTime).doubleValue() / MILLIS_TO_SECONDS,
                    indexName, policyId);
        }

        // action: nested object in 3.x — consumed_retries is a direct field, not inside retry_info
        String actionName = null;
        Object consumedRetries = null;
        Object actionObj = indexInfo.get("action");
        if (actionObj instanceof Map) {
            Map<String, Object> action = (Map<String, Object>) actionObj;
            actionName = extractString(action, "name");
            consumedRetries = action.get("consumed_retries");
        }
        // 2.x flat-key fallback for action name and consumed_retries
        if (actionName == null) {
            actionName = extractString(indexInfo,
                    "index.plugins.index_state_management.action.name");
        }
        if (!(consumedRetries instanceof Number)) {
            Object retryInfoObj = indexInfo.get(
                    "index.plugins.index_state_management.action.retry_info");
            if (retryInfoObj instanceof Map) {
                consumedRetries = ((Map<String, Object>) retryInfoObj).get("consumed_retries");
            }
        }
        if (actionName != null && consumedRetries instanceof Number) {
            catalog.setClusterGauge("ism_index_retry_count",
                    ((Number) consumedRetries).doubleValue(), indexName, policyId, actionName);
        }

        // step failed flag
        if (stepObj instanceof Map) {
            String stepName = extractString((Map<String, Object>) stepObj, "name");
            if (stepName != null) {
                catalog.setClusterGauge("ism_index_step_failed_bool",
                        stepFailed ? 1 : 0, indexName, policyId, stepName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    void updateFailedMetrics(Map<String, Object> response) {
        Object totalObj = response.get("total_failed_managed_indices");
        catalog.setClusterGauge("ism_failed_indices_total",
                totalObj instanceof Number ? ((Number) totalObj).doubleValue() : 0);

        Object failedObj = response.get("failed_indices");
        if (!(failedObj instanceof List)) {
            return;
        }

        Map<String, Integer> failedByPolicy = new HashMap<>();
        for (Object item : (List<Object>) failedObj) {
            if (!(item instanceof Map)) {
                continue;
            }
            String policyId = (String) ((Map<String, Object>) item).get("policy_id");
            if (policyId != null) {
                failedByPolicy.merge(policyId, 1, Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> e : failedByPolicy.entrySet()) {
            catalog.setClusterGauge("ism_failed_indices_by_policy_total", e.getValue(), e.getKey());
        }
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }
}
