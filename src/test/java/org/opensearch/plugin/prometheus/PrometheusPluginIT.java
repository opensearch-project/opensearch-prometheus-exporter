/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Copyright [2021] [Lukas Vlcek]
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
package org.opensearch.plugin.prometheus;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.ParseException;
import org.opensearch.action.admin.cluster.node.info.NodeInfo;
import org.opensearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.opensearch.action.admin.cluster.node.info.PluginsAndModules;
import org.opensearch.action.admin.indices.stats.CommonStats;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
// CS-SUPPRESS-SINGLE: RegexpSingleline supportsDedicatedMasters is an OpenSearch API with no alternative
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2, numClientNodes = 0, supportsDedicatedMasters = false)
public class PrometheusPluginIT extends OpenSearchIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(PrometheusExporterPlugin.class);
    }

    /**
     * Plugin must be installed on every cluster node.
     */
    public void testPluginInstalled() {
        NodesInfoResponse response = client().admin().cluster().prepareNodesInfo().clear().all().get();
        assertEquals(0, response.failures().size());
        assertFalse(response.getNodes().isEmpty());
        for (NodeInfo ni : response.getNodes()) {
            assertNotNull(ni.getInfo(PluginsAndModules.class));
            assertEquals(
                    1,
                    ni.getInfo(PluginsAndModules.class).getPluginInfos().stream().filter(
                            pluginInfo -> pluginInfo.getClassname().endsWith("PrometheusExporterPlugin")
                    ).count()
            );
        }
    }

    public void testPrometheusClientResponse() throws IOException, ParseException {
        RestClient rc = getRestClient();
        logClusterState();
        Response response = rc.performRequest(new Request("GET", "_prometheus/metrics"));
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("text/plain; charset=UTF-8", response.getEntity().getContentType());
        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        assertTrue(body.startsWith("# HELP"));
    }

    /**
     * Index level time metrics must export the index stats as fractional seconds.
     */
    public void testIndexTimeMetricsInSeconds() throws IOException, ParseException {
        String index = "time-units";
        assertAcked(prepareCreate(index).setSettings(Settings.builder()
                .put("index.number_of_shards", 1)
                .put("index.number_of_replicas", 0)
                // No scheduled refreshes, so stats can't move between the two reads.
                .put("index.refresh_interval", "-1")));
        for (int i = 0; i < 100; i++) {
            client().prepareIndex(index).setId(String.valueOf(i)).setSource("foo", "bar").get();
        }
        client().prepareDelete(index, "0").get();
        refresh(index);

        CommonStats stats = client().admin().indices().prepareStats(index).get().getIndex(index).getTotal();
        Response response = getRestClient().performRequest(new Request("GET", "_prometheus/metrics"));
        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

        assertEquals(stats.getIndexing().getTotal().getIndexTime().millis() / 1000.0,
                indexMetric(body, "index_indexing_index_time_seconds", index), 0.0);
        assertEquals(stats.getIndexing().getTotal().getDeleteTime().millis() / 1000.0,
                indexMetric(body, "index_indexing_delete_time_seconds", index), 0.0);
        assertEquals(stats.getIndexing().getTotal().getThrottleTime().millis() / 1000.0,
                indexMetric(body, "index_indexing_throttle_time_seconds", index), 0.0);
        assertEquals(stats.getRecoveryStats().throttleTime().millis() / 1000.0,
                indexMetric(body, "index_recovery_throttle_time_seconds", index), 0.0);
        assertEquals(stats.getWarmer().totalTimeInMillis() / 1000.0,
                indexMetric(body, "index_warmer_time_seconds", index), 0.0);
    }

    private static double indexMetric(String body, String metric, String index) {
        Matcher m = Pattern.compile("^opensearch_" + metric + "\\{cluster=\"[^\"]*\",index=\"" + index
                + "\",context=\"total\",\\} (\\S+)$", Pattern.MULTILINE).matcher(body);
        assertTrue(metric + " not exported", m.find());
        return Double.parseDouble(m.group(1));
    }
}
