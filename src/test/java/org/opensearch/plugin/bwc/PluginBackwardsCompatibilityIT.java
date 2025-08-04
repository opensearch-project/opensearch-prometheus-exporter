/*
 * Copyright [2024] [Lukáš VLČEK]
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

package org.opensearch.plugin.bwc;

import org.junit.Assert;
import org.opensearch.Version;
import org.opensearch.common.settings.Settings;
import org.opensearch.test.rest.OpenSearchRestTestCase;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IMPORTANT: When upgrading to a new version of OpenSearch this test has to be updated
 * to reflect appropriate versions, see {@link #BWCVersion} and {@link #NewVersion}.
 * These two variables must match 'testClusters' versions used in 'build.gradle' file.
 */
public class PluginBackwardsCompatibilityIT extends OpenSearchRestTestCase {

    public static final Version BWCVersion = Version.V_2_19_2;
    public static final Version NewVersion = Version.V_2_19_3;

    private static final ClusterType CLUSTER_TYPE = ClusterType.parse(System.getProperty("tests.rest.bwcsuite"));
    private static final String CLUSTER_NAME = System.getProperty("tests.clustername");
//    private static final String MIXED_CLUSTER_TEST_ROUND = System.getProperty("tests.rest.bwcsuite_round");

    @Override
    protected final Settings restClientSettings() {
        return Settings
                .builder()
                .put(super.restClientSettings())
                // increase the timeout here to 90 seconds to handle long waits for a green
                // cluster health. the waits for green need to be longer than a minute to
                // account for delayed shards
                .put(OpenSearchRestTestCase.CLIENT_SOCKET_TIMEOUT, "90s")
                .build();
    }

    private enum ClusterType {
        OLD,
        MIXED,
        UPGRADED;

        public static ClusterType parse(String value) {
            switch (value) {
                case "old_cluster":
                    return OLD;
                case "mixed_cluster":
                    return MIXED;
                case "upgraded_cluster":
                    return UPGRADED;
                default:
                    throw new AssertionError("unknown cluster type: " + value);
            }
        }
    }

    public void testBackwardsCompatibility() throws Exception {
        int testedNodes = 0;
        String uri = getNodesPluginsUri();
        Map<String, Map<String, Object>> responseMap = (Map<String, Map<String, Object>>) getAsMap(uri).get("nodes");
        for (Map<String, Object> response : responseMap.values()) {

            String version = (String) response.get("version");
            Assert.assertFalse(version.isEmpty());

            List<Map<String, Object>> plugins = (List<Map<String, Object>>) response.get("plugins");
            Set<Object> pluginNames = plugins.stream().map(map -> map.get("name")).collect(Collectors.toSet());

            switch (CLUSTER_TYPE) {
                case OLD:
                    testedNodes++;
                    Assert.assertTrue(pluginNames.contains("prometheus-exporter"));
                    Assert.assertEquals(BWCVersion, Version.fromString(version));
                    break;
                case MIXED:
                    testedNodes++;
                    Assert.assertTrue(pluginNames.contains("prometheus-exporter"));
                    break;
                case UPGRADED:
                    testedNodes++;
                    Assert.assertTrue(pluginNames.contains("prometheus-exporter"));
                    Assert.assertEquals(NewVersion, Version.fromString(version));
                    break;
            }
        }

        assertTrue(testedNodes > 0);

        switch (CLUSTER_TYPE) {
            case OLD:
                assertEquals(1, testedNodes);
                break;
            case MIXED:
                assertEquals(1, testedNodes);
                break;
            case UPGRADED:
                assertEquals(3, testedNodes);
                break;
        }

    }

    private String getNodesPluginsUri() {
        switch (CLUSTER_TYPE) {
            case OLD:
                return "_nodes/" + CLUSTER_NAME + "-0/plugins";
            case MIXED:
                String round = System.getProperty("tests.rest.bwcsuite_round");
                if (round.equals("second")) {
                    return "_nodes/" + CLUSTER_NAME + "-1/plugins";
                } else if (round.equals("third")) {
                    return "_nodes/" + CLUSTER_NAME + "-2/plugins";
                } else {
                    return "_nodes/" + CLUSTER_NAME + "-0/plugins";
                }
            case UPGRADED:
                return "_nodes/plugins";
            default:
                throw new AssertionError("unknown cluster type: " + CLUSTER_TYPE);
        }
    }
}
