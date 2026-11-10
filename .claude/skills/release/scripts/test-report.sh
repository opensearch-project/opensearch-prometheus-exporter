#!/usr/bin/env bash
#
# Copyright OpenSearch Contributors
# SPDX-License-Identifier: Apache-2.0
#
# Report the test results of a finished build, from the Gradle result files and the BWC cluster logs,
# not from the Gradle output: `bwcTestSuite` prints NO-SOURCE and BUILD SUCCESSFUL says nothing about
# which tests ran. Exits 1 if anything is failed, skipped or missing, or if a BWC node did not upgrade.
#
# Usage: test-report.sh <plugin-version> <worktree>
#   plugin-version  e.g. 3.9.0.0 (the new OpenSearch version is derived: 3.9.0)
#   worktree        checkout where `./gradlew clean build` and then `./bwctest.sh` ran
#
# The old BWC version is read from BWCversion in the worktree's gradle.properties.

set -euo pipefail

if [[ $# -ne 2 ]]; then
    sed -n '10,15p' "$0"
    exit 2
fi

PLUGIN_VERSION="$1"
WORKTREE="$2"
NEW_VERSION="${PLUGIN_VERSION%.*}"
OLD_VERSION="$(sed -nE 's/^BWCversion[[:space:]]*=[[:space:]]*([^[:space:]]+).*/\1/p' "${WORKTREE}/gradle.properties")"
RESULTS="${WORKTREE}/build/test-results"
CLUSTERS="${WORKTREE}/build/testclusters"
PROBLEMS=0

problem() {
    echo "PROBLEM: $*"
    PROBLEMS=$((PROBLEMS + 1))
}

# Sum one attribute of the <testsuite> elements in a result directory.
sum_attr() {
    local dir="$1" attr="$2" file total=0 value
    for file in "$dir"/TEST-*.xml; do
        [[ -f "$file" ]] || continue
        value="$(grep -m1 -o '<testsuite [^>]*' "$file" | sed -nE "s/.*[[:space:]]${attr}=\"([0-9]+)\".*/\1/p")"
        total=$((total + ${value:-0}))
    done
    echo "$total"
}

report_suite() {
    local dir="$1" name tests failures errors skipped
    name="$(basename "$dir")"
    tests="$(sum_attr "$dir" tests)"
    failures="$(sum_attr "$dir" failures)"
    errors="$(sum_attr "$dir" errors)"
    skipped="$(sum_attr "$dir" skipped)"
    printf '%-42s %6s %6s %6s %6s\n' "$name" "$tests" "$failures" "$errors" "$skipped"
    [[ "$tests" -gt 0 ]] || problem "$name ran no tests"
    [[ "$failures" -eq 0 && "$errors" -eq 0 ]] || problem "$name has failures or errors"
    [[ "$skipped" -eq 0 ]] || problem "$name skipped tests"
}

echo "Test report for ${PLUGIN_VERSION}: BWC upgrade ${OLD_VERSION:-unknown} -> ${NEW_VERSION}"
echo "Commit: $(git -C "$WORKTREE" log -1 --format='%h %s')"
if ! git -C "$WORKTREE" diff --quiet HEAD; then
    echo "Uncommitted changes: $(git -C "$WORKTREE" diff --name-only HEAD | tr '\n' ' ')"
fi
[[ -n "$OLD_VERSION" ]] || problem "BWCversion not found in gradle.properties"

echo
echo "== Build and test suites =="
printf '%-42s %6s %6s %6s %6s\n' Suite Tests Failed Errors Skipped
# The release branches have no unit tests so far: everything in src/test is an *IT class, and the
# `test` task reports NO-SOURCE. Results are required only if unit test sources exist.
if [[ -d "${RESULTS}/test" ]]; then
    report_suite "${RESULTS}/test"
elif [[ -n "$(find "${WORKTREE}/src/test" -name '*.java' ! -name '*IT.java' -print -quit 2>/dev/null)" ]]; then
    problem "src/test has unit test sources but no results: did ./gradlew clean build run?"
else
    printf '%-42s %s\n' "test" "no unit tests on this branch (NO-SOURCE)"
fi
for suite in integTest yamlRestTest; do
    if [[ -d "${RESULTS}/${suite}" ]]; then
        report_suite "${RESULTS}/${suite}"
    else
        problem "no results for ${suite}: did ./gradlew clean build run?"
    fi
done

echo
echo "== BWC stages =="
printf '%-42s %6s %6s %6s %6s\n' Stage Tests Failed Errors Skipped
BWC_STAGES=0
for dir in "$RESULTS"/bwcCluster*; do
    [[ -d "$dir" ]] || continue
    BWC_STAGES=$((BWC_STAGES + 1))
    report_suite "$dir"
done
[[ "$BWC_STAGES" -gt 0 ]] || problem "no BWC results: did ./bwctest.sh run?"

echo
echo "== BWC nodes (from their logs) =="
printf '%-18s %-18s %8s %8s\n' Node Versions Plugin Errors
BWC_NODES=0
# bwcCluster0-N and bwcCluster1-N are the upgraded clusters. The bwcCluster-<task>-N directories are
# not: they run the new OpenSearch without the plugin, and the tests do not use them.
for dir in "$CLUSTERS"/bwcCluster[0-9]*; do
    [[ -d "$dir" ]] || continue
    node="$(basename "$dir")"
    log="$(find "$dir/logs" -maxdepth 1 -name '*.log' ! -name '*_*' 2>/dev/null | head -1)"
    if [[ -z "$log" ]]; then
        problem "${node} has no log"
        continue
    fi
    BWC_NODES=$((BWC_NODES + 1))
    versions="$(grep -oE 'version\[[0-9]+\.[0-9]+\.[0-9]+\]' "$log" | sed -E 's/version\[(.*)\]/\1/' | paste -sd ' ' - || true)"
    plugin="$(grep -c 'loaded plugin \[prometheus-exporter\]' "$log" || true)"
    errors="$(grep -cE ' (ERROR|FATAL) ' "$log" || true)"
    printf '%-18s %-18s %8s %8s\n' "$node" "${versions// / -> }" "$plugin" "$errors"
    [[ "$versions" == "${OLD_VERSION} ${NEW_VERSION}" ]] || problem "${node} did not go ${OLD_VERSION} -> ${NEW_VERSION}"
    [[ "$plugin" -eq 2 ]] || problem "${node} did not load the plugin on both versions"
    [[ "$errors" -eq 0 ]] || problem "${node} logged errors"
done
[[ "$BWC_NODES" -gt 0 ]] || problem "no BWC node logs: did ./bwctest.sh run?"

echo
if [[ "$PROBLEMS" -gt 0 ]]; then
    echo "==> TEST REPORT FAILED: ${PROBLEMS} problem(s)"
    exit 1
fi
echo "==> TEST REPORT PASSED"
