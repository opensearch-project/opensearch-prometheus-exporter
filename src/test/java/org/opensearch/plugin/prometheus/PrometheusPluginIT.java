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
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
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
}
