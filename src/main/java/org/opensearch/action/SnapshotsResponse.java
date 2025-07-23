/*
 * Copyright [2018] [Vincent VAN HOLLEBEKE]
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

package org.opensearch.action;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.snapshots.SnapshotInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a container class for holding response data related to snapshots.
 */
public class SnapshotsResponse extends ActionResponse {
    private final List<SnapshotInfo> snapshotInfos;

    /**
     * A constructor.
     * @param in A streamInput to materialize the instance from
     * @throws IOException if there is an exception reading from inputStream
     */
    public SnapshotsResponse(StreamInput in) throws IOException {
        super(in);
        snapshotInfos = Collections.synchronizedList(in.readList(SnapshotInfo::new));
    }

    /**
     * A constructor.
     */
    public SnapshotsResponse() {
        this.snapshotInfos = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Writes the instance into {@link StreamOutput}.
     *
     * @param out the output stream to which the instance is to be written
     * @throws IOException if there is an exception writing to the output stream
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeCollection(snapshotInfos);
    }

    /**
     * Getter for {@code snapshotInfos} list.
     *
     * @return the list of {@link SnapshotInfo} objects
     */
    public List<SnapshotInfo> getSnapshotInfos() {
        return snapshotInfos;
    }

    /**
     * Adds {@code snapshotInfosToAdd} to the {@code snapshotInfos} list.
     */
    public void addSnapshotInfos(List<SnapshotInfo> snapshotInfosToAdd) {
        snapshotInfos.addAll(snapshotInfosToAdd);
    }
}
