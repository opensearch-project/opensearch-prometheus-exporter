# Writing the release notes

## Collect the changes

List every first-parent commit between the previous release tag and the release SHA, then map each
commit to its PR. Squash-merged Dependabot commits often have no `(#NNN)` suffix, so do not parse
commit titles; ask the API instead:

Use the previous tag's **upstream** commit, not the local tag, which can point elsewhere. For this and for
`$UPSTREAM`, see SKILL.md:

```bash
R=opensearch-project/opensearch-prometheus-exporter
PREV_SHA=$(git ls-remote $UPSTREAM "refs/tags/<prev-tag>" "refs/tags/<prev-tag>^{}" | tail -1 | cut -f1)
for c in $(git rev-list --first-parent "${PREV_SHA}..<sha>"); do
  line=""
  for attempt in 1 2 3; do   # the API sometimes returns an empty body; retry
    line=$(gh api repos/$R/commits/$c/pulls -q '.[0] | "\(.number)\t\(.user.login)\t\(.title)\t\([.labels[].name] | join(","))"' 2>/dev/null) \
      && [ -n "$line" ] && break
    sleep 2
  done
  echo "${line:-UNMAPPED	$(git log -1 --format='%h %s' $c)}"
done
```

Every commit must produce one line. Resolve each `UNMAPPED` line before writing the notes: retry it, take the
PR number from a `(#NNN)` suffix in the commit title, or ask the user. Never drop a commit silently.

The previous tag must be on the same release line: `3.8.0.0` for `3.9.0.0`, `2.19.6.0` for `2.19.7.0`,
and `3.9.0.0` for the plugin patch release `3.9.0.1`.

## Format

Use only the sections that have entries, in this order. Past releases (`gh release view <tag> -R $R`)
are the reference for tone.

```markdown
<optional opener, see Rules>

## Features

* <PR title> by @<author> in https://github.com/opensearch-project/opensearch-prometheus-exporter/pull/<n>

## Bug Fixes

* ...

## Maintenance Upgrades

* Bump to OpenSearch X.Y.Z by @<author> in https://github.com/opensearch-project/opensearch-prometheus-exporter/pull/<n>
* <other human-authored maintenance PRs, same format>
* Bump actions/checkout from 6.0.2 to 7.0.1 (#544, #579)
* Bump gradle/actions/setup-gradle from 6.1.0 to 6.2.0 in #551

**Full Changelog**: https://github.com/opensearch-project/opensearch-prometheus-exporter/compare/<prev-tag>...X.Y.Z.N
```

Rules:
- **Opener for security-driven releases:** when the OpenSearch release fixes CVEs (a bundled JDK update,
  or dependency bumps that the OpenSearch release notes mark as CVE fixes), start the notes with one line
  that says so, e.g. "Builds against OpenSearch 3.10.1, which updates the bundled JDK for CVE-2026-114938."
  (3.10.1.0) or "Builds against OpenSearch 2.19.8, which updates the bundled JDK for CVE-2026-114938 and
  bumps Jackson and zstd-jni to address multiple CVEs." (2.19.8.0). Take the facts from the OpenSearch
  release notes (`gh release view X.Y.Z -R opensearch-project/OpenSearch`) and the tracking issue; name a CVE
  only when a source names it. Users upgrade for these fixes, and the plugin release is what lets them move
  to the patched OpenSearch. Leave the opener out when the OpenSearch release has no security fixes.
- Sort by section using the PR title and labels: new metrics or settings are **Features**, fixes are
  **Bug Fixes**, and dependency, build, CI, docs and version bumps are **Maintenance Upgrades**.
  When a PR does not clearly fit, ask.
- Put the OpenSearch version bump PR first in Maintenance Upgrades. A plugin patch release has no such
  PR; its main entries are usually under Bug Fixes.
- Human PRs: `<title> by @<author> in <full PR URL>`.
- Dependabot PRs: no author. Merge all bumps of the same dependency into one line, from the lowest
  "from" version to the highest "to" version: `(#a, #b)` for several PRs, `in #a` for a single one.
- Show the notes to the user before you put them on the draft.
