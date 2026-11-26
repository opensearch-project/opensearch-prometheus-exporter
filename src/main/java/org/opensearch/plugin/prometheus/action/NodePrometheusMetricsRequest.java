/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Copyright [2016] [Vincent VAN HOLLEBEKE]
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

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.plugin.prometheus.action;

import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.admin.indices.stats.CommonStatsFlags;
import org.opensearch.action.support.clustermanager.ClusterManagerNodeReadRequest;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.opensearch.action.ValidateActions.addValidationError;

/**
 * Action request class for Prometheus Exporter plugin.
 * <p>
 * Every selector is optional; {@code null} or empty means "use the default", which is everything the plugin settings allow.
 */
public class NodePrometheusMetricsRequest extends ClusterManagerNodeReadRequest<NodePrometheusMetricsRequest> {

    /** Metric groups that can be selected via {@link #include(String...)}. */
    public static final List<String> PARTS = List.of(
            "cluster_health", "nodes_stats", "indices_stats", "cluster_settings", "action_metrics");

    /** Node stats sections the collector reads, selectable via {@link #nodesStatsMetrics(String...)}. */
    public static final List<String> NODES_STATS_METRICS = List.of(
            "indices", "os", "process", "jvm", "thread_pool", "fs", "transport", "http", "breaker", "script", "ingest");

    /** Index stats flags, selectable via {@link #indicesStatsMetrics(String...)}. */
    public static final List<String> INDICES_STATS_METRICS = Arrays.stream(CommonStatsFlags.Flag.values())
            .map(CommonStatsFlags.Flag::getRestName).toList();

    @Nullable private String[] include;
    @Nullable private String[] nodesStatsMetrics;
    @Nullable private String[] indicesStatsMetrics;
    @Nullable private String[] indices;

    /**
     * A constructor.
     */
    public NodePrometheusMetricsRequest() {
        super();
    }

    /**
     * A constructor that utilizes the inputStream.
     * @param in inputStream
     * @throws IOException if there is an exception reading from inputStream
     */
    public NodePrometheusMetricsRequest(StreamInput in) throws IOException {
        super(in);
        include = in.readOptionalStringArray();
        nodesStatsMetrics = in.readOptionalStringArray();
        indicesStatsMetrics = in.readOptionalStringArray();
        indices = in.readOptionalStringArray();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalStringArray(include);
        out.writeOptionalStringArray(nodesStatsMetrics);
        out.writeOptionalStringArray(indicesStatsMetrics);
        out.writeOptionalStringArray(indices);
    }

    /**
     * Select which internal requests to execute, see {@link #PARTS}.
     * @param include part names, or null for all
     * @return this request
     */
    public NodePrometheusMetricsRequest include(@Nullable String... include) {
        this.include = emptyToNull(include);
        return this;
    }

    /**
     * Select which node stats sections to collect, see {@link #NODES_STATS_METRICS}.
     * @param metrics section names, or null for all
     * @return this request
     */
    public NodePrometheusMetricsRequest nodesStatsMetrics(@Nullable String... metrics) {
        this.nodesStatsMetrics = emptyToNull(metrics);
        return this;
    }

    /**
     * Select which index stats flags to collect, see {@link #INDICES_STATS_METRICS}.
     * @param metrics flag names, or null for all
     * @return this request
     */
    public NodePrometheusMetricsRequest indicesStatsMetrics(@Nullable String... metrics) {
        this.indicesStatsMetrics = emptyToNull(metrics);
        return this;
    }

    /**
     * Override the {@code prometheus.indices_filter.selected_indices} setting for this request.
     * @param indices index names or patterns, or null to use the setting
     * @return this request
     */
    public NodePrometheusMetricsRequest indices(@Nullable String... indices) {
        this.indices = emptyToNull(indices);
        return this;
    }

    /**
     * @param part one of {@link #PARTS}
     * @return whether the part was selected
     */
    public boolean includes(String part) {
        return include == null || Arrays.asList(include).contains(part);
    }

    /**
     * @return selected node stats sections, defaults to {@link #NODES_STATS_METRICS}
     */
    public List<String> nodesStatsMetrics() {
        return nodesStatsMetrics == null ? NODES_STATS_METRICS : Arrays.asList(nodesStatsMetrics);
    }

    /**
     * @return selected index stats flags, or null for all
     */
    @Nullable
    public String[] indicesStatsMetrics() {
        return indicesStatsMetrics;
    }

    /**
     * @return index names overriding the setting, or null
     */
    @Nullable
    public String[] indices() {
        return indices;
    }

    /**
     * Rejects unknown selector values.
     * @return validation errors, or null if valid
     */
    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException e = validateValues(null, "include", include, PARTS);
        e = validateValues(e, "nodes_stats_metrics", nodesStatsMetrics, NODES_STATS_METRICS);
        return validateValues(e, "indices_stats_metrics", indicesStatsMetrics, INDICES_STATS_METRICS);
    }

    // An empty list must not reach IndicesStatsRequest, which reads it as all indices.
    private static String[] emptyToNull(@Nullable String[] values) {
        return values == null || values.length == 0 ? null : values;
    }

    private static ActionRequestValidationException validateValues(ActionRequestValidationException e, String param,
                                                                   @Nullable String[] values, List<String> allowed) {
        if (values != null) {
            for (String v : values) {
                if (!allowed.contains(v)) {
                    e = addValidationError("unknown value [" + v + "] for [" + param + "], expected any of " + allowed, e);
                }
            }
        }
        return e;
    }
}
