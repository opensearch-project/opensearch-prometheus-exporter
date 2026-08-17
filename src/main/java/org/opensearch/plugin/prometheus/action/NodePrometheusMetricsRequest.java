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
import org.opensearch.action.support.clustermanager.ClusterManagerNodeReadRequest;
import org.opensearch.core.common.io.stream.StreamInput;

import java.io.IOException;

/**
 * Action request class for Prometheus Exporter plugin.
 */
public class NodePrometheusMetricsRequest extends ClusterManagerNodeReadRequest<NodePrometheusMetricsRequest> {

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
    }

    /**
     * A validation of the request.
     * Currently, no validations are needed, thus this method always returns null.
     * @return null
     */
    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
