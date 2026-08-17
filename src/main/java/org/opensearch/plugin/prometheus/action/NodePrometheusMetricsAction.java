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

import org.opensearch.action.ActionType;

/**
 * Action class for Prometheus Exporter plugin.
 */
public class NodePrometheusMetricsAction extends ActionType<NodePrometheusMetricsResponse> {

    /**
     * An action singleton instance at the node level.
     */
    public static final NodePrometheusMetricsAction INSTANCE = new NodePrometheusMetricsAction();

    /**
     * A privilege that users need to have to be allowed to request metrics from plugin REST endpoint.
     */
    public static final String NAME = "cluster:monitor/prometheus/metrics";

    private NodePrometheusMetricsAction() {
        super(NAME,  NodePrometheusMetricsResponse::new);
    }
}
