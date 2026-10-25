### What is the type of request?

Repository Management

### Details of the request

Requesting creation of tag `X.Y.Z.N` for `opensearch-project/opensearch-prometheus-exporter` on the `<branch>` branch, by publishing the prepared draft release.

Draft release "Release X.Y.Z.N" is staged with a built and smoke-tested `prometheus-exporter-X.Y.Z.N.zip` (sha256 `<sha256>`), targeting `<branch>` at `<full-sha>`.

Repo maintainers have `maintain` (not `admin`), so the org tag ruleset blocks us from creating the tag or publishing the release. Please publish the draft: https://github.com/opensearch-project/opensearch-prometheus-exporter/releases (draft "Release X.Y.Z.N").

### Additional information to support your request

<!-- For a plugin patch release, replace the "Routine release" line with:
     "Plugin patch release for OpenSearch X.Y.Z with fixes for <...> (release PR: opensearch-project/opensearch-prometheus-exporter#<pr>)."
     For a release on an older line (draft created with --latest=false), add a line:
     "The draft is marked as not Latest; please keep it that way when publishing." -->
- Tracking issue: opensearch-project/opensearch-prometheus-exporter#TRACKING_ISSUE
- Routine release matching OpenSearch X.Y.Z (version bump: opensearch-project/opensearch-prometheus-exporter#BUMP_PR). Release notes are on the draft.
- The maintainer team (<@-mention every GitHub ID listed in MAINTAINERS.md on main>) is aware.

### When does this request need to be completed?

As soon as possible: the artifact is built and verified, and users are waiting for the X.Y.Z.N release.

### Notes

Track the progress of your request here: https://github.com/orgs/opensearch-project/projects/208/views/33.
Member of @opensearch-project/admin will take a look at the request soon.
Thanks!
