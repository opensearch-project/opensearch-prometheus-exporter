---
name: release
description: Release a new version of the Prometheus Exporter plugin (X.Y.Z.N), either for a new OpenSearch X.Y.Z (N = 0) or as a plugin patch release for an existing OpenSearch version (N > 0), on main or a maintenance branch (v2.19, v3.6). Use when asked to release, cut, prepare or publish a plugin version, to do a dry run of a release, bump to a new OpenSearch version for a release, draft release notes, request a release tag, or continue a release tracking issue ("Release X.Y.Z.N").
argument-hint: <plugin-version, e.g. 3.10.0.0, 2.19.7.0 or 3.9.0.1>
---

# Release the plugin

The human-readable process is [RELEASE_PROCESS.md](../../../RELEASE_PROCESS.md); this skill drives it.
The arguments are `$ARGUMENTS`. Take the plugin version (`X.Y.Z.N`, four numbers) from them; other words set the
mode (e.g. "dry run", see below). If there is no version, ask for it.

## Ground rules

- **Maintainers cannot create tags.** An org-level ruleset blocks tag creation for the `maintain` role,
  both by `git push` and by publishing a release. A maintainer stages a **draft release**, and an
  OpenSearch admin publishes it through a request issue in `opensearch-project/.github` (phase 4).
- **Confirm before every outward-facing action.** Show the full text and wait for a yes before you
  open, edit, comment on or close an issue or PR, create or edit a release, or push a branch.
- **No leftover placeholders.** When filling a template, remove its HTML comments (they are instructions
  for you), then check the result: `grep -nE 'X\.Y\.Z|NNNN|[A-Z]+_[A-Z_]+|<[^>]+>' <file>` must print nothing.
  The checklist's `TODO: link to PR` is intended text and does not match. GitHub silently drops some unfilled `<placeholders>` as HTML
  tags, so they are easy to miss in the rendered issue. Of two alternatives in a template (e.g. OpenSearch
  release vs. patch release), keep only the one that applies.
- **Release builds use the final OpenSearch version, never a `-SNAPSHOT`.** Between releases, `main`
  (and `v3.6`) build against `X.Y.Z-SNAPSHOT` from the OpenSearch snapshot repository. The release commit
  must have `opensearch_version = X.Y.Z`, resolved from Maven Central, so that the tag reproduces the
  released ZIP.
- **Release exactly one commit.** The tag is created on the draft's target SHA, and that SHA is not
  always the bump commit (3.8.0.0 was released from a later commit). Ask which SHA to release,
  suggesting the branch head, and use the same SHA for the build, the smoke test, the notes and the draft.
- PRs are opened from the user's fork against `opensearch-project/opensearch-prometheus-exporter`.
  The fork is the git remote that is not `opensearch-project`. Commits need a DCO sign-off (`git commit -s`).
- **Do all branch work in worktrees; never switch the user's checkout.** Run the skill from a `main`
  checkout (the skill exists only on `main`). Never run `git checkout`, `git switch` or `git stash` there,
  and leave its uncommitted changes alone. The skill's own files (`.claude/skills/release/`) are not on the
  maintenance branches, so switching to one would remove the script and templates mid-release. Instead,
  give every PR branch and every build its own worktree in the scratchpad, and always run the script
  and read the templates from the `main` checkout:
  ```bash
  git fetch origin
  git worktree prune                                                                  # drop worktrees of past sessions
  git worktree add -b <pr-branch> <scratchpad>/pr-<pr-branch> origin/<base-branch>    # a PR branch
  git worktree add --detach <scratchpad>/build-$V <sha>                               # a build
  ```
  Edit, build, commit and push inside the worktree (`git -C <worktree> ...`). Once the branch is
  pushed, remove the worktree (`git worktree remove <worktree>`). Keep the local branch until its PR
  is merged, in case it needs more commits. A release spans several sessions, and each session has its
  own scratchpad, so a PR branch from an earlier session usually still exists locally: then
  `git worktree add -b` fails. In that case, show the branch's state (`git log origin/<base-branch>..<pr-branch>`,
  and whether it is pushed and has a PR), and ask whether to continue it (`git worktree add <path> <pr-branch>`,
  without `-b`) or start over.
- Today's date: use `date`, never guess.

## Dry run

When the user asks for a dry run (or a "check", "rehearsal", "what would happen"), go through every
phase **without any outward-facing action**: no push, no PR, issue, comment or release, not even with
confirmation. Recommend a dry run when the user starts a release without having done one.

- Run all the read-only checks of each phase, and continue past a blocker to the later phases where
  possible, so that one run reports every problem.
- Apply the phase 2 changes in a detached worktree in the scratchpad
  (`git worktree add --detach <scratchpad>/build-$V origin/<branch>`), run the build there, and, if it
  passes, the smoke test.
- Draft everything that would be posted (PR descriptions, release notes, tag request, forum post) and
  show it, but post nothing.
- Report per phase: done / would do (with the exact change) / blocked (with the reason and what unblocks it).
  Also report anything wrong you notice on the way, e.g. a wrong date in a compatibility matrix.
- Afterwards, save the changes as a patch in the scratchpad and remove the worktree.

## Versions and branches

Plugin `X.Y.Z.N` runs on OpenSearch `X.Y.Z` only. There are two kinds of release:

- **OpenSearch release** (`N = 0`): follows a new OpenSearch `X.Y.Z`. This is the usual case.
- **Plugin patch release** (`N > 0`): ships plugin fixes for an OpenSearch version that already has a
  plugin release. OpenSearch is not upgraded. None has been made yet (as of 3.9.0.0), so check these
  rules against the actual branch and tell the user what you assumed.

Derive everything from the plugin version:

The current major is released from `main`, older lines from `vX.Y` maintenance branches (list them with
`git ls-remote --heads origin`). Known layouts (as of Oct 2026):

| | `main` | `v3.6` | `v2.19` |
|---|---|---|---|
| `opensearch_version` | `X.Y.Z-SNAPSHOT` between releases, `X.Y.Z` in the release commit | same as `main` | leave empty, the build derives `X.Y.Z` from `version` |
| Compatibility matrix | `COMPATIBILITY.md` | `COMPATIBILITY.md` | `README.md` section |
| BWC test class (under `src/test/java/org/opensearch/plugin/`) | `prometheus/bwc/PluginBackwardsCompatibilityIT.java` | `bwc/PluginBackwardsCompatibilityIT.java` | `bwc/PluginBackwardsCompatibilityIT.java` |

For a maintenance release, the matrix row also goes into `COMPATIBILITY.md` on `main`.

`v1.3` is end of life: OpenSearch 1.3.x will have no further releases. Do not release from it. If the user
insists, treat it as a branch not in the table: it has no BWC tests, builds with JDK 14 and Gradle 6, keeps
the matrix in the README, and needs an older JDK for the smoke test (`OPENSEARCH_JAVA_HOME`).

**Previous release** (for BWC versions, release notes and the changelog link): for `N > 0` it is
`X.Y.Z.(N-1)`. For `N = 0` it is the highest existing tag on the same line below this version: the
highest `X.*` tag for `main` (for a new major, e.g. `4.0.0.0`, the highest tag of the previous major), and
the highest `X.Y.*` tag for a maintenance branch. Use the upstream tags, ignoring the `v`-prefixed
duplicates (e.g. `v2.19.6.0`), and sort them as versions, not as text (`3.10.0.0` comes after `3.9.0.0`):

```bash
git ls-remote --tags origin | awk '{print $2}' | sed 's#refs/tags/##; s#\^{}##' \
  | grep -E '^3\.[0-9]+\.[0-9]+\.[0-9]+$' | sort -V -u    # e.g. all 3.x release tags
```
 Release tags are a
mix of lightweight (e.g. `2.19.6.0`) and annotated (e.g. `3.9.0.0`) tags, and only annotated ones have a peeled
`^{}` line, so do not filter on it. Resolve a tag's commit with
`git ls-remote origin refs/tags/<tag> 'refs/tags/<tag>^{}' | tail -1 | cut -f1`. Local tags can
differ: a maintainer's locally signed tag may point to another commit than the one the admins published
(seen with `3.7.0.0`). Use the upstream SHA in `git log` / `git rev-list` ranges, and never overwrite
local tags without asking.

**Patch release target branch:** the branch whose `gradle.properties` builds against OpenSearch `X.Y.Z`
(`X.Y.Z` or `X.Y.Z-SNAPSHOT`; on `v2.19`, a `version` of `X.Y.Z.*`).
If `main` has moved on to a newer OpenSearch and there is no `vX.Y` branch, stop and tell the user. A
new branch is requested from the admins like the `v3.6` one was (opensearch-project/.github#505).

The paths and layout differ between branches, and branches drift. Always read the files on the
target branch (`git show origin/<branch>:<path>`) instead of trusting this table. For a branch not
covered here, check its last release commit to see what it changed, and tell the user to add it to the table.

For a patch release (`N > 0`), first check that there is something to release: if
`git log <prev-sha>..origin/<branch>` is empty, the release has no content. Tell the user, and mention any
open PRs against the branch (`gh pr list -R $R -B <branch>`). For `N = 0` an empty log is normal: the
OpenSearch upgrade can be the only change.

## Find the current phase

Work out where the release is before you do anything, and report it to the user. For an OpenSearch
release (`N = 0`), first check that OpenSearch `X.Y.Z` is released at all
(`gh api repos/opensearch-project/OpenSearch/git/matching-refs/tags/X.Y.Z` must list `refs/tags/X.Y.Z`).
If it is not, say so and stop: there is nothing to release yet, not even a tracking issue to open.

```bash
V=<plugin-version>; R=opensearch-project/opensearch-prometheus-exporter
gh issue list -R $R -s all -S "\"Release $V\" in:title"                        # phase 1
git show origin/<branch>:gradle.properties | grep -E '^(version|opensearch_version|BWC)'   # phase 2a
gh pr list -R $R -s all -S "OpenSearch X.Y.Z in:title"                         # phase 2a: upgrade PR open or merged?
git show origin/<branch>:<matrix-file> | grep "$V"; git show origin/<branch>:README.md | grep "download/$V/"  # phase 2b
gh release view $V -R $R --json isDraft,targetCommitish,assets,publishedAt    # phase 3 / 5
gh issue list -R opensearch-project/.github -s all -S "\"release tag $V\" in:title"   # phase 4
```

In shell variables, write `"origin/${B}:path"` with braces: in zsh, `$B:g...` or `$B:r...` is a history
modifier and silently corrupts the ref. Set variables on their own lines, not at the end of a `&&` chain,
so that a failing command before them cannot leave them empty.

Then continue from the first phase that is not done. Each phase ends with a status line naming the
next one; the user can stop in between (a release usually spans days, waiting on reviews and admins).

## Phase 1: Tracking issue

Look for an open `Release X.Y.Z.N` issue. Community members often open one; reuse it.
If none exists, fill [templates/tracking-issue.md](templates/tracking-issue.md) and create it:
`gh issue create -R $R --title "Release $V" --body-file <file>`.
For an OpenSearch release, link the OpenSearch release notes and the opensearch-build release ticket for
`X.Y.Z` (find it with `gh issue list -R opensearch-project/opensearch-build -s all -S "Release X.Y.Z in:title"`).
For a patch release, say why it is needed and link the fixes (ask the user).

## Phase 2: Prepare the branch

Phase 2 has two parts, and each is needed only if the branch does not already contain it. The OpenSearch
upgrade is often merged long before the release, sometimes just because the community needed a build
against the new OpenSearch. The release docs are sometimes done in the upgrade PR (#614) and sometimes
separately (#563 upgrade, #576 docs). **Always check the target branch and report what is already done,
with the PRs that did it, before you change anything.** Never open a PR that duplicates a merged or
open one.

### 2a. OpenSearch upgrade (`N = 0` only)

Already done if `gradle.properties` on the target branch has `version = $V`, `opensearch_version = X.Y.Z`
(the final version, or empty on `v2.19`), and `BWCversion` / `BWCPluginVersion` pointing to the previous
release. Check the BWC test class too. Find the PR that did it (`git log origin/<branch> -- gradle.properties`),
so you can list it in the release notes.

The usual state on `main` (and `v3.6`): the upgrade to `X.Y.Z-SNAPSHOT` was merged before OpenSearch `X.Y.Z`
came out. Then the only change left for 2a is `opensearch_version = X.Y.Z-SNAPSHOT` → `X.Y.Z`. Make it, together
with the 2b changes, in one PR titled `Prepare release X.Y.Z.N`, once the Maven check below passes.

If an upgrade PR is **open** (search above, including PRs from community members and bots), do not
create another. Tell the user and offer to review it or help get it merged.

A **partial** upgrade also counts as found: for example, `version` bumped but the BWC settings still pointing
two releases back. Report exactly what is missing and fix only that.

**Before upgrading, check that OpenSearch `X.Y.Z` is on Maven Central**, on every branch. The GitHub release
and the download tarballs come out before the Maven artifacts. Until those arrive, a build against the final
version fails with `Could not find org.opensearch.gradle:build-tools:X.Y.Z`. A `-SNAPSHOT` build on `main`
may still pass, but it must not be released:

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://repo.maven.apache.org/maven2/org/opensearch/gradle/build-tools/X.Y.Z/build-tools-X.Y.Z.pom
```

On anything other than `200`, stop, tell the user the release is blocked, and link the opensearch-build release
ticket (it is usually still open). Phases 1 and 2b can still go ahead.

Otherwise, in a new worktree on branch `release-$V` based on `origin/<target-branch>` (see Ground rules):

1. `gradle.properties`: `version = $V`; `opensearch_version = X.Y.Z` (empty on `v2.19`);
   `BWCversion` = the previous release's OpenSearch version, and `BWCPluginVersion` = the previous release
   tag exactly (e.g. `3.8.0` / `3.8.0.0`, or `3.9.0` / `3.9.0.1` after a patch release). The build downloads
   that plugin ZIP from GitHub, so the tag must exist.
2. `PluginBackwardsCompatibilityIT`: `BWCVersion = Version.V_<previous OpenSearch>`, `NewVersion = Version.V_<X_Y_Z>`.
3. Run `./gradlew clean build` with the JDK from `.github/workflows/CI.yml` (`jobs.build.strategy.matrix.java`;
   use the highest). A new OpenSearch version can break the build: dependency conflicts are usually
   fixed with `resolutionStrategy.force` in `build.gradle` (see #614), and precommit checks may get stricter.
   Put such fixes in this PR and explain them in the description.
4. If 2b is also needed, make its changes in the same PR (one PR, like #614).
5. Commit (`-s`) as `Bump to OpenSearch X.Y.Z` (or `Prepare release X.Y.Z.N` when only the `-SNAPSHOT` switch
   and 2b were left). Then, after confirmation, push to the fork and open the PR
   against the target branch, referencing the tracking issue. Examples: #614 (main), #563 (v2.19).
   Remove the worktree once the branch is pushed.

For a **patch release** (`N > 0`) there is no OpenSearch upgrade. Only `version = $V` changes in
`gradle.properties` (plus `-SNAPSHOT` → `X.Y.Z` if the branch has it); leave the BWC settings alone. The BWC tests cover the
upgrade from the previous OpenSearch version, which is the same as before. Check that the branch builds
against OpenSearch `X.Y.Z`, and make this change together with 2b in a PR titled `Prepare release X.Y.Z.N`.

### 2b. Release docs

Already done if the compatibility matrix has a row for `$V` and the README install URL points to `$V`
(or to a newer release on the branch). Otherwise update only what is missing:

- Compatibility matrix: add a row at the top of the right section, aligned like the existing rows.
  A patch release gets its own row (`3.9.0 | 3.9.0.1 | ...`) above the older rows for the same OpenSearch version.
  For the date, use the planned release date; phase 5 corrects it.
- `README.md`: update the `opensearch-plugin install .../releases/download/<v>/prometheus-exporter-<v>.zip` URL,
  unless a newer release is already on the branch.

If no upgrade PR is being opened, make these changes in their own worktree (branch `release-docs-$V`),
commit them as `Update compatibility matrix for X.Y.Z.N` (`-s`) and, after confirmation, open a PR (example: #576).

**Maintenance releases:** check `COMPATIBILITY.md` on `main` too, and if the row is missing, open a small
PR against `main` that adds it, from a separate worktree based on `origin/main` (branch `compat-matrix-$V`).
Do not use the user's `main` checkout for it. Otherwise the main matrix falls behind (see #600).

Phase 2 is done when 2a and 2b are both on the target branch. Update the tracking issue checklist
("Compatibility Matrix is updated (#NNN)").

## Phase 3: Build, verify, stage the draft release

1. **Pick the SHA** (see Ground rules). Show `git log --oneline <prev-sha>..<sha>` (the previous tag's upstream
   commit, see Versions and branches) so the user can see what goes in.
   Check that the SHA contains the phase 2 changes: `git show <sha>:gradle.properties` has `version = $V`
   and no `-SNAPSHOT`, and the compatibility matrix at `<sha>` has the `$V` row.
2. **Build from a clean worktree** in the scratchpad, never the working copy:
   ```bash
   git worktree add --detach <scratchpad>/build-$V <sha>
   cd <scratchpad>/build-$V && JAVA_HOME=<jdk> ./gradlew clean build
   shasum -a 256 build/distributions/prometheus-exporter-$V.zip
   ```
   The full build includes the integration tests. If it fails, stop and report; do not release.
   The sha256 goes into the tag request (phase 4), which is also where phase 5 reads it back: the
   worktree and the scratchpad are gone by then.
   Optionally run `./bwctest.sh` there too (BWC tests are not part of `build`).
3. **Smoke-test** the ZIP against the real OpenSearch distribution. Run the script from the `main` checkout,
   not from the worktree, which may not have it:
   ```bash
   <main-checkout>/.claude/skills/release/scripts/smoke-test.sh $V <scratchpad>/build-$V/build/distributions/prometheus-exporter-$V.zip <scratchpad>/os-$V
   ```
   It downloads the minimal OpenSearch `X.Y.Z` tarball, installs the plugin, starts one node and checks
   `/_prometheus/metrics`. On macOS it runs the linux tarball with a local JDK 21 (there are no macOS builds).
4. **Release notes:** follow [templates/release-notes.md](templates/release-notes.md).
5. **Create the draft** (after confirmation). A draft does not create the tag, so maintainers can do this:
   ```bash
   gh release create $V -R $R --draft --target <full-sha> --title "Release $V" \
     --notes-file <notes> <scratchpad>/build-$V/build/distributions/prometheus-exporter-$V.zip
   ```
   For every release that is not the newest version overall (any maintenance-branch release, e.g. 2.19.7.0,
   or a patch of an older OpenSearch version), add `--latest=false` so it does not replace the newest release as "Latest".
   Check it with `gh release view $V -R $R --json isDraft,targetCommitish,assets`: it must be a draft, have the
   right target, and have one asset whose `digest` is `sha256:<the sha256 you built>`.
6. Remove the worktree: `git worktree remove <scratchpad>/build-$V`.

## Phase 4: Request the tag

Fill [templates/tag-request.md](templates/tag-request.md) with the SHA, the sha256, the tracking issue,
the bump PR and the maintainers (from `git show origin/main:MAINTAINERS.md`), and open it after confirmation:

```bash
gh issue create -R opensearch-project/.github --label github-request \
  --title "[GitHub Request] Create release tag $V for opensearch-prometheus-exporter" --body-file <file>
```

Comment on the tracking issue with a link to the request. Then wait: an admin publishes the draft,
which creates the tag. Phase 4 is done when `gh release view $V` shows it published (`isDraft: false`).

## Phase 5: Wrap-up

1. **Verify the published release:** download the asset from its public URL
   (`https://github.com/opensearch-project/opensearch-prometheus-exporter/releases/download/$V/prometheus-exporter-$V.zip`)
   and check that its sha256 matches the one in the tag request issue (and the asset's `digest` in
   `gh release view $V -R $R --json assets`). Check that `git ls-remote --tags origin $V`
   points to the released SHA.
2. **Fix the release date** in the compatibility matrix, so it matches `publishedAt` (format `Mon DD, YYYY`).
   If it differs, open a small PR (`Fix X.Y.Z.N release date in compatibility matrix`, see #616), against
   `main`, and for a maintenance release also against the branch. Each PR gets its own worktree.
3. **Forum announcement:** the user posts it by hand. Draft it from [templates/forum-post.md](templates/forum-post.md).
4. **Tracking issue:** tick the checklist items in the body (`gh issue edit --body-file`), comment with
   the release link, the tag request and the forum post, and close it once everything is ticked.
