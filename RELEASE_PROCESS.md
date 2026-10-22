# Release Process

Plugin version `X.Y.Z.N` works with OpenSearch `X.Y.Z` only. Most releases are `X.Y.Z.0` and follow a new OpenSearch release `X.Y.Z`. A plugin patch release (`N > 0`) ships plugin fixes for an OpenSearch version that already has a plugin release, without upgrading OpenSearch. Releases of the current major version are made from `main`; releases of older lines are made from maintenance branches such as `v2.19` or `v3.6`.

The result of a release is a git tag `X.Y.Z.N` on a single commit, and a GitHub release with the plugin ZIP archive (`prometheus-exporter-X.Y.Z.N.zip`) built from that commit.

Maintainers cannot create tags: an organization-level ruleset blocks tag creation for the `maintain` role, both by `git push` and by publishing a release. Maintainers stage a **draft release**, and an OpenSearch admin publishes it (which creates the tag) on request.

AI coding agents can drive this process with the [`release` skill](.claude/skills/release/SKILL.md), see [Using the `release` skill](#using-the-release-skill).

## Release Process Steps

1. [Open a tracking issue](#1-open-a-tracking-issue)
2. [Prepare the release branch](#2-prepare-the-release-branch)
3. [Build, verify and stage a draft release](#3-build-verify-and-stage-a-draft-release)
4. [Request the release tag](#4-request-the-release-tag)
5. [Wrap up](#5-wrap-up)

## Detailed instructions

### 1. Open a tracking issue

Open an issue titled `Release X.Y.Z.N` with a checklist, and keep it updated during the release. Community members often open it as soon as OpenSearch `X.Y.Z` is out; if one already exists, use it. For a plugin patch release, explain in the issue why it is needed.

- Example: [#588](https://github.com/opensearch-project/opensearch-prometheus-exporter/issues/588) (template: [tracking-issue.md](.claude/skills/release/templates/tracking-issue.md))

### 2. Prepare the release branch

A release is always built against the **final** OpenSearch version from Maven Central, never against a `-SNAPSHOT`. Between releases, `main` (and `v3.6`) build against `X.Y.Z-SNAPSHOT`, but the release commit must have `opensearch_version = X.Y.Z`, so that the tag reproduces the released ZIP. OpenSearch publishes its Maven artifacts some days after its GitHub release, so check first that [`build-tools` `X.Y.Z`](https://repo.maven.apache.org/maven2/org/opensearch/gradle/build-tools/) is on Maven Central. Until it is, the release has to wait.

First check the target branch: the OpenSearch upgrade is often merged well before the release, sometimes because the community needed a build against the new OpenSearch. If `gradle.properties` already has the new `version` and the final `opensearch_version`, no upgrade PR is needed; only the release docs (Compatibility Matrix row, install command) may still be missing, and those go in a separate PR (example: [#576](https://github.com/opensearch-project/opensearch-prometheus-exporter/pull/576)). If the upgrade was merged as `X.Y.Z-SNAPSHOT`, open one PR titled `Prepare release X.Y.Z.N` that switches it to `X.Y.Z` and adds the release docs. Also check for an open upgrade PR, and help finish it instead of opening a second one.

Otherwise, open a PR titled `Bump to OpenSearch X.Y.Z` against the target branch. It usually changes:

- `gradle.properties`: `version`, `opensearch_version` (`X.Y.Z` on `main` and `v3.6`; empty on `v2.19`, where the build derives it), and `BWCversion` / `BWCPluginVersion`, which point to the previous release
- `PluginBackwardsCompatibilityIT`: the `BWCVersion` and `NewVersion` constants
- the Compatibility Matrix: [COMPATIBILITY.md](COMPATIBILITY.md) (the README section on `v2.19`)
- `README.md`: the install command URL

A new OpenSearch version sometimes needs build fixes (dependency conflicts, stricter precommit checks). Those go into the same PR.

For a release from a maintenance branch, also add the Compatibility Matrix row to `COMPATIBILITY.md` on `main`.

For a plugin patch release (`X.Y.Z.N`, `N > 0`), the PR is titled `Prepare release X.Y.Z.N`. It only changes `version`, adds a Compatibility Matrix row (e.g. `3.9.0 | 3.9.0.1`), and updates the install command if this is the newest release on the branch. The BWC settings stay unchanged, and `opensearch_version` too, unless the branch is on `X.Y.Z-SNAPSHOT`, which the PR switches to `X.Y.Z`. The release must come from a branch that builds against OpenSearch `X.Y.Z`. If `main` has already moved on, that means a `vX.Y` maintenance branch, which is created on request by the admins (example: [.github#505](https://github.com/opensearch-project/.github/issues/505)).

- Examples: [#614](https://github.com/opensearch-project/opensearch-prometheus-exporter/pull/614) (`main`), [#563](https://github.com/opensearch-project/opensearch-prometheus-exporter/pull/563) (`v2.19`)

Commits must be signed off (see the [Contribution policy](CONTRIBUTING.md#developer-certificate-of-origin)).

### 3. Build, verify and stage a draft release

Pick the commit to release; usually it is the head of the target branch after the bump PR is merged. Its `gradle.properties` must not contain `-SNAPSHOT`. Use this same commit for every step below.

Build from a clean checkout of that commit, using the JDK version from `.github/workflows/CI.yml` (`jobs.build.strategy.matrix.java`):

```shell
$ git worktree add ../release-X.Y.Z.N <sha>
$ (cd ../release-X.Y.Z.N && ./gradlew clean build)
$ shasum -a 256 ../release-X.Y.Z.N/build/distributions/prometheus-exporter-X.Y.Z.N.zip
```

Note the checksum: the tag request and the final check after publishing use it.

Smoke-test the ZIP: install it into the [minimal OpenSearch distribution](https://opensearch.org/downloads.html) `X.Y.Z`, start it, and check `GET /_prometheus/metrics`. [smoke-test.sh](.claude/skills/release/scripts/smoke-test.sh) does all of this. Run it from the `main` checkout, because maintenance branches don't have the script:

```shell
$ .claude/skills/release/scripts/smoke-test.sh X.Y.Z.N ../release-X.Y.Z.N/build/distributions/prometheus-exporter-X.Y.Z.N.zip
```

When the draft release (below) is created, remove the worktree with `git worktree remove ../release-X.Y.Z.N`.

Write the release notes, with `Features`, `Bug Fixes` and `Maintenance Upgrades` sections and a `Full Changelog` link; see the [past releases](https://github.com/opensearch-project/opensearch-prometheus-exporter/releases) and [release-notes.md](.claude/skills/release/templates/release-notes.md). Then create the draft release (a draft does not create the tag):

```shell
$ gh release create X.Y.Z.N --draft --target <full-sha> --title "Release X.Y.Z.N" \
    --notes-file notes.md build/distributions/prometheus-exporter-X.Y.Z.N.zip
```

For a release of an older line (a maintenance branch, or a patch release of an older OpenSearch version), add `--latest=false` so it does not replace the newest release as "Latest".

### 4. Request the release tag

Open a `[GitHub Request] Create release tag X.Y.Z.N for opensearch-prometheus-exporter` issue in [opensearch-project/.github](https://github.com/opensearch-project/.github/issues/new/choose). Ask the admins to publish the draft release, and give the target commit, the ZIP checksum and a link to the tracking issue.

- Examples: [.github#627](https://github.com/opensearch-project/.github/issues/627), [.github#670](https://github.com/opensearch-project/.github/issues/670) (template: [tag-request.md](.claude/skills/release/templates/tag-request.md))

### 5. Wrap up

After the release is published:

- Download the published ZIP and check that its checksum matches the one you built.
- Fix the release date in the Compatibility Matrix if it differs from the actual publish date (example: [#616](https://github.com/opensearch-project/opensearch-prometheus-exporter/pull/616)). The date is the plugin release date, not the OpenSearch release date.
- Announce the release on the [OpenSearch forum](https://forum.opensearch.org), in the `OpenSearch` category with the `releases` tag (example: [3.9.0.0](https://forum.opensearch.org/t/prometheus-exporter-plugin-3-9-0-0-released/28327)).
- Tick the checklist in the tracking issue and close it.

## Using the `release` skill

The [`release` skill](.claude/skills/release/SKILL.md) lets [Claude Code](https://claude.com/claude-code) drive the steps above. In a Claude Code session in this repository, run:

```
/release 2.19.7.0
```

Asking in plain words ("let's release 3.10.0.0", "continue the 2.19.7.0 release") works as well.

Run it from a `main` checkout, also for maintenance releases: the skill exists only on `main`. It picks the target branch from the version (e.g. `2.19.7.0` → `v2.19`) and does all branch work in separate git worktrees, so your checkout stays on `main` and your uncommitted changes stay where they are.

**Start with a dry run.** Before the real release, ask for a dry run, for example *"do a dry run of the release skill for 2.19.7.0"*. The skill then goes through every phase without changing anything on GitHub: it runs the checks, applies the version changes and builds in a temporary worktree, runs the smoke test, and drafts the PR descriptions, release notes and tag request. It ends with a report of what each phase would do and anything that blocks the release. Typical problems it catches early: OpenSearch artifacts missing from Maven Central, build failures with the new OpenSearch version, wrong dates in the compatibility matrix, and nothing new to release. When the dry run looks fine, run the release for real.

**How it works.** The skill first finds out where the release is: tracking issue, OpenSearch upgrade, release docs, draft release, tag request, wrap-up. It reports what is already done, with links, and continues from the first unfinished step. A release usually takes days (reviews, Maven publication, the admins), so run the skill again with the same version whenever you want to continue; it always starts from the current state on GitHub.

**What it does without asking:** read-only checks (git, GitHub, Maven Central), local builds in a temporary worktree, and the smoke test.

**What it asks before doing:** anything visible to others. It shows the full text and waits for your approval before it pushes a branch, opens, edits, comments on or closes a PR or issue, or creates or edits a release.

**What it cannot do:** create the release tag (only OpenSearch admins can; the skill prepares the request issue) or post on the forum (the skill drafts the announcement for you).

**Prerequisites:**

- [`gh`](https://cli.github.com) logged in as a maintainer of this repository
- a fork of the repository as a git remote, for the PR branches
- the JDK version from the target branch's CI (`.github/workflows/CI.yml`); JDK 21 for current branches
- network access to Maven Central and `artifacts.opensearch.org` (the smoke test downloads OpenSearch)

**Typical blockers it reports:** OpenSearch `X.Y.Z` artifacts not on Maven Central yet (the build fails until they are), a failing build or smoke test, a plugin patch release with no new commits, or an open PR that already does the upgrade.

## Done!

It was a lot of steps and you deserve a special reward! Go have a nice cup of tea or coffee.
