/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.action;

import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Index metadata, such as the index creation date, extracted from the cluster state for the indices
 * selected for index level metrics.
 */
public class IndexMetadataData implements Writeable {

    // index name -> creation date in milliseconds since the epoch
    private final Map<String, Long> creationDates;

    /**
     * Extracts the metadata of the given indices from the cluster state metadata.
     * Indices missing from the metadata (for example deleted in the meantime) are skipped.
     * @param metadata {@link Metadata} from the cluster state
     * @param indices names of the indices to extract
     */
    public IndexMetadataData(Metadata metadata, Iterable<String> indices) {
        Map<String, Long> dates = new HashMap<>();
        for (String index : indices) {
            IndexMetadata indexMetadata = metadata.index(index);
            if (indexMetadata != null) {
                dates.put(index, indexMetadata.getCreationDate());
            }
        }
        this.creationDates = dates;
    }

    /**
     * A constructor that materializes the instance from {@link StreamInput}.
     * @param in A {@link StreamInput} to materialize the instance from
     * @throws IOException if reading from {@link StreamInput} is not successful
     */
    public IndexMetadataData(StreamInput in) throws IOException {
        this.creationDates = in.readMap(StreamInput::readString, StreamInput::readLong);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(creationDates, StreamOutput::writeString, StreamOutput::writeLong);
    }

    /**
     * Creation date of the index.
     * @param index index name
     * @return creation date in milliseconds since the epoch, or null if unknown
     */
    public Long getCreationDate(String index) {
        return creationDates.get(index);
    }
}
