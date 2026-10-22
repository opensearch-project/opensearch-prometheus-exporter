#!/usr/bin/env bash
#
# Copyright OpenSearch Contributors
# SPDX-License-Identifier: Apache-2.0
#
# Smoke-test a plugin release ZIP against the matching minimal OpenSearch distribution:
# download OpenSearch, install the plugin, start a single node, scrape /_prometheus/metrics.
#
# Usage: smoke-test.sh <plugin-version> <plugin-zip> [work-dir]
#   plugin-version  e.g. 3.9.0.0 (OpenSearch version is derived: 3.9.0)
#   plugin-zip      local path to prometheus-exporter-<plugin-version>.zip
#   work-dir        where to download/unpack OpenSearch (default: a new temp dir)
#
# Only linux tarballs are published; on macOS they run with a local JDK via OPENSEARCH_JAVA_HOME
# (set it yourself, or the script picks JDK 21 from /usr/libexec/java_home).

set -euo pipefail

if [[ $# -lt 2 ]]; then
    sed -n '9,15p' "$0"
    exit 1
fi

PLUGIN_VERSION="$1"
PLUGIN_ZIP="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
WORK_DIR="${3:-$(mktemp -d)}"
OS_VERSION="${PLUGIN_VERSION%.*}"
PORT="${SMOKE_TEST_PORT:-9250}"

[[ -f "$PLUGIN_ZIP" ]] || { echo "Plugin ZIP not found: $PLUGIN_ZIP" >&2; exit 1; }

case "$(uname -m)" in
    arm64|aarch64) ARCH=arm64 ;;
    *) ARCH=x64 ;;
esac

if [[ "$(uname -s)" == "Darwin" && -z "${OPENSEARCH_JAVA_HOME:-}" ]]; then
    if ! OPENSEARCH_JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
        echo "No JDK 21 found. The linux OpenSearch tarball needs a local JDK on macOS:" >&2
        echo "install JDK 21, or set OPENSEARCH_JAVA_HOME to a JDK that OpenSearch ${OS_VERSION} supports." >&2
        exit 1
    fi
    export OPENSEARCH_JAVA_HOME
fi

TARBALL="opensearch-min-${OS_VERSION}-linux-${ARCH}.tar.gz"
URL="https://artifacts.opensearch.org/releases/core/opensearch/${OS_VERSION}/${TARBALL}"
OS_HOME="${WORK_DIR}/opensearch-${OS_VERSION}"

echo "==> Work dir: ${WORK_DIR}"
if [[ ! -d "$OS_HOME" ]]; then
    echo "==> Downloading ${URL}"
    curl -fsSL -o "${WORK_DIR}/${TARBALL}" "$URL"
    tar -xzf "${WORK_DIR}/${TARBALL}" -C "$WORK_DIR"
fi

echo "==> Installing plugin ${PLUGIN_ZIP}"
"${OS_HOME}/bin/opensearch-plugin" remove prometheus-exporter >/dev/null 2>&1 || true
"${OS_HOME}/bin/opensearch-plugin" install --batch "file://${PLUGIN_ZIP}"

echo "==> Starting OpenSearch on port ${PORT}"
"${OS_HOME}/bin/opensearch" -d -p "${WORK_DIR}/opensearch.pid" \
    -Ediscovery.type=single-node -Ehttp.port="${PORT}" -Epath.logs="${WORK_DIR}/logs"
trap 'kill "$(cat "${WORK_DIR}/opensearch.pid")" 2>/dev/null || true' EXIT

STARTED=false
for _ in $(seq 1 60); do
    if curl -fs "localhost:${PORT}/_cluster/health" >/dev/null 2>&1; then STARTED=true; break; fi
    sleep 2
done
if [[ "$STARTED" != true ]]; then
    echo "==> SMOKE TEST FAILED: OpenSearch did not start within 120s. Last log lines:" >&2
    tail -20 "${WORK_DIR}"/logs/*.log >&2 2>/dev/null || true
    exit 1
fi

echo "==> Installed plugins"
curl -fsS "localhost:${PORT}/_cat/plugins?v"

METRICS="${WORK_DIR}/metrics.txt"
curl -fsS "localhost:${PORT}/_prometheus/metrics" -o "$METRICS"
LINES=$(grep -vc '^#' "$METRICS" || true)
echo "==> Scraped ${LINES} metric samples into ${METRICS}"

if [[ "$LINES" -gt 0 ]] && grep -q '^opensearch_cluster_status' "$METRICS"; then
    echo "==> SMOKE TEST PASSED (${PLUGIN_VERSION} on OpenSearch ${OS_VERSION})"
else
    echo "==> SMOKE TEST FAILED: no metrics or opensearch_cluster_status missing" >&2
    exit 1
fi
