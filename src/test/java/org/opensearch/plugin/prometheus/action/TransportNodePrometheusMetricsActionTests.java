/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.action;

import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.test.OpenSearchTestCase;

public class TransportNodePrometheusMetricsActionTests extends OpenSearchTestCase {

    public void testHealthRequestAppliesLevelAtTransportLayer() {
        ClusterHealthRequest perIndex = TransportNodePrometheusMetricsAction.healthRequest(true);
        assertTrue(perIndex.isApplyLevelAtTransportLayer());
        assertEquals(ClusterHealthRequest.Level.INDICES, perIndex.level());
        assertTrue(perIndex.local());

        ClusterHealthRequest clusterOnly = TransportNodePrometheusMetricsAction.healthRequest(false);
        assertTrue(clusterOnly.isApplyLevelAtTransportLayer());
        assertEquals(ClusterHealthRequest.Level.CLUSTER, clusterOnly.level());
    }
}
