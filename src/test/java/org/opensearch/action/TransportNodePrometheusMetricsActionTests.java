/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.action;

import org.compuscene.metrics.prometheus.PrometheusSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.opensearch.action.admin.cluster.node.stats.NodesStatsRequest;
import org.opensearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.opensearch.action.admin.indices.stats.IndicesStatsRequest;
import org.opensearch.action.admin.indices.stats.IndicesStatsResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.ClusterAdminClient;
import org.opensearch.transport.client.IndicesAdminClient;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the per-step timeout / non-fatal degrade behavior of
 * {@link TransportNodePrometheusMetricsAction}. These cover the resilience fix only: a stuck
 * node responding to the NodesStats or IndicesStats fan-out must not fail the whole scrape.
 */
class TransportNodePrometheusMetricsActionTests {

    @Mock private Client client;
    @Mock private AdminClient adminClient;
    @Mock private ClusterAdminClient clusterAdminClient;
    @Mock private IndicesAdminClient indicesAdminClient;
    @Mock private TransportService transportService;
    @Mock private ActionFilters actionFilters;
    @Mock private Task task;

    private ClusterSettings clusterSettings;

    private static Set<Setting<?>> allSettings() {
        Set<Setting<?>> settings = new HashSet<>();
        settings.add(PrometheusSettings.PROMETHEUS_CLUSTER_SETTINGS);
        settings.add(PrometheusSettings.PROMETHEUS_INDICES);
        settings.add(PrometheusSettings.PROMETHEUS_NODES_FILTER);
        settings.add(PrometheusSettings.PROMETHEUS_SELECTED_INDICES);
        settings.add(PrometheusSettings.PROMETHEUS_SELECTED_OPTION);
        settings.add(PrometheusSettings.PROMETHEUS_SCRAPE_STEP_TIMEOUT);
        return settings;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);
        lenient().when(adminClient.indices()).thenReturn(indicesAdminClient);
    }

    private TransportNodePrometheusMetricsAction createAction(Settings settings) {
        clusterSettings = new ClusterSettings(settings, allSettings());
        return new TransportNodePrometheusMetricsAction(
            settings, client, transportService, actionFilters, clusterSettings
        );
    }

    private void completeClusterHealthAndNodesInfo() {
        ArgumentCaptor<ActionListener<ClusterHealthResponse>> healthCaptor = ArgumentCaptor.forClass(ActionListener.class);
        verify(clusterAdminClient).health(any(), healthCaptor.capture());
        healthCaptor.getValue().onResponse(mock(ClusterHealthResponse.class));

        ArgumentCaptor<ActionListener<NodesInfoResponse>> infoCaptor = ArgumentCaptor.forClass(ActionListener.class);
        verify(clusterAdminClient).nodesInfo(any(), infoCaptor.capture());
        infoCaptor.getValue().onResponse(mock(NodesInfoResponse.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void indicesStatsTotalFailure_propagatesAsFailure() {
        // A total IndicesStats failure (e.g. index_not_found_exception from a misconfigured indices filter,
        // raised during index resolution before the broadcast) must surface to the caller, not be silently
        // swallowed into a 200 with partial metrics. A single stuck node is handled separately by the
        // per-node request timeout, which yields partial results via onResponse rather than onFailure.
        Settings settings = Settings.builder()
            .put("prometheus.indices", true)
            .put("prometheus.cluster.settings", false)
            .build();
        TransportNodePrometheusMetricsAction action = createAction(settings);

        ActionListener<NodePrometheusMetricsResponse> outerListener = mock(ActionListener.class);
        action.doExecute(task, new NodePrometheusMetricsRequest(), outerListener);

        completeClusterHealthAndNodesInfo();

        ArgumentCaptor<ActionListener<NodesStatsResponse>> statsCaptor = ArgumentCaptor.forClass(ActionListener.class);
        verify(clusterAdminClient).nodesStats(any(), statsCaptor.capture());
        NodesStatsResponse statsResponse = mock(NodesStatsResponse.class);
        when(statsResponse.getNodes()).thenReturn(Collections.emptyList());
        statsCaptor.getValue().onResponse(statsResponse);

        ArgumentCaptor<ActionListener<IndicesStatsResponse>> indicesCaptor = ArgumentCaptor.forClass(ActionListener.class);
        verify(indicesAdminClient).stats(any(IndicesStatsRequest.class), indicesCaptor.capture());
        indicesCaptor.getValue().onFailure(new RuntimeException("no such index [test]"));

        verify(outerListener).onFailure(any());
        verify(outerListener, never()).onResponse(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void indicesStatsRequest_hasTimeoutAndCancelOnTimeout() {
        Settings settings = Settings.builder()
            .put("prometheus.indices", true)
            .put("prometheus.cluster.settings", false)
            .put("prometheus.scrape.step_timeout", "5s")
            .build();
        TransportNodePrometheusMetricsAction action = createAction(settings);

        ActionListener<NodePrometheusMetricsResponse> outerListener = mock(ActionListener.class);
        action.doExecute(task, new NodePrometheusMetricsRequest(), outerListener);

        completeClusterHealthAndNodesInfo();

        ArgumentCaptor<ActionListener<NodesStatsResponse>> statsCaptor = ArgumentCaptor.forClass(ActionListener.class);
        verify(clusterAdminClient).nodesStats(any(), statsCaptor.capture());
        NodesStatsResponse statsResponse = mock(NodesStatsResponse.class);
        when(statsResponse.getNodes()).thenReturn(Collections.emptyList());
        statsCaptor.getValue().onResponse(statsResponse);

        ArgumentCaptor<IndicesStatsRequest> requestCaptor = ArgumentCaptor.forClass(IndicesStatsRequest.class);
        verify(indicesAdminClient).stats(requestCaptor.capture(), any());

        IndicesStatsRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TimeValue.timeValueSeconds(5), capturedRequest.timeout());
        assertTrue(capturedRequest.getShouldCancelOnTimeout());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nodesStatsTotalFailure_propagatesAsFailure() {
        // As with IndicesStats, a total NodesStats failure propagates. A single unresponsive node does not
        // reach here: TransportNodesAction records it as a per-node FailedNodeException and still completes
        // via onResponse with partial results, bounded by the per-node request timeout.
        Settings settings = Settings.builder()
            .put("prometheus.indices", false)
            .put("prometheus.cluster.settings", false)
            .build();
        TransportNodePrometheusMetricsAction action = createAction(settings);

        ActionListener<NodePrometheusMetricsResponse> outerListener = mock(ActionListener.class);
        action.doExecute(task, new NodePrometheusMetricsRequest(), outerListener);

        completeClusterHealthAndNodesInfo();

        ArgumentCaptor<ActionListener<NodesStatsResponse>> statsCaptor = ArgumentCaptor.forClass(ActionListener.class);
        verify(clusterAdminClient).nodesStats(any(), statsCaptor.capture());
        statsCaptor.getValue().onFailure(new RuntimeException("simulated total failure"));

        verify(outerListener).onFailure(any());
        verify(outerListener, never()).onResponse(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nodesStatsRequest_hasTimeout() {
        Settings settings = Settings.builder()
            .put("prometheus.indices", false)
            .put("prometheus.cluster.settings", false)
            .put("prometheus.scrape.step_timeout", "7s")
            .build();
        TransportNodePrometheusMetricsAction action = createAction(settings);

        ActionListener<NodePrometheusMetricsResponse> outerListener = mock(ActionListener.class);
        action.doExecute(task, new NodePrometheusMetricsRequest(), outerListener);

        completeClusterHealthAndNodesInfo();

        ArgumentCaptor<NodesStatsRequest> requestCaptor = ArgumentCaptor.forClass(NodesStatsRequest.class);
        verify(clusterAdminClient).nodesStats(requestCaptor.capture(), any());

        assertEquals(TimeValue.timeValueSeconds(7), requestCaptor.getValue().timeout());
    }

    @Test
    @SuppressWarnings("unchecked")
    void scrapeStepTimeout_defaultsToFiveSeconds() {
        Settings settings = Settings.builder()
            .put("prometheus.indices", false)
            .put("prometheus.cluster.settings", false)
            .build();
        TransportNodePrometheusMetricsAction action = createAction(settings);

        ActionListener<NodePrometheusMetricsResponse> outerListener = mock(ActionListener.class);
        action.doExecute(task, new NodePrometheusMetricsRequest(), outerListener);

        completeClusterHealthAndNodesInfo();

        ArgumentCaptor<NodesStatsRequest> requestCaptor = ArgumentCaptor.forClass(NodesStatsRequest.class);
        verify(clusterAdminClient).nodesStats(requestCaptor.capture(), any());

        assertEquals(TimeValue.timeValueSeconds(5), requestCaptor.getValue().timeout());
    }
}
