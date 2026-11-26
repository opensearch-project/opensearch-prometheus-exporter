/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.collector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Build information of the exporter plugin itself. The values are written into {@value #RESOURCE} (next to this class)
 * at build time by the {@code generateBuildInfo} Gradle task.
 */
public final class ExporterBuildInfo {

    static final String RESOURCE = "exporter-build.properties";
    static final String UNKNOWN = "unknown";

    private static final Logger logger = LogManager.getLogger(ExporterBuildInfo.class);
    private static final ExporterBuildInfo CURRENT = load();

    private final String version;
    private final String revision;

    ExporterBuildInfo(String version, String revision) {
        this.version = version;
        this.revision = revision;
    }

    /**
     * Get the build information of the running plugin.
     * @return build information, with "unknown" values when it is not available
     */
    public static ExporterBuildInfo current() {
        return CURRENT;
    }

    /**
     * Get the plugin version, such as {@code 3.9.0.0}.
     * @return plugin version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Get the git revision the plugin was built from.
     * @return git commit hash
     */
    public String getRevision() {
        return revision;
    }

    private static ExporterBuildInfo load() {
        Properties properties = new Properties();
        // Resolved relative to this class, so it is read from the plugin jar and never from another jar.
        try (InputStream in = ExporterBuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(in);
            } else {
                logger.warn("Exporter build information [{}] not found", RESOURCE);
            }
        } catch (IOException e) {
            logger.warn("Failed to read exporter build information [" + RESOURCE + "]", e);
        }
        return new ExporterBuildInfo(value(properties, "version"), value(properties, "revision"));
    }

    private static String value(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? UNKNOWN : value.trim();
    }
}
