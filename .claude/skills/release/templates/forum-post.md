# Forum announcement

Post at https://forum.opensearch.org in the **OpenSearch** category with the **releases** tag.
Example: https://forum.opensearch.org/t/prometheus-exporter-plugin-3-10-0-0-released/28689

The forum renders Markdown: keep the body in Markdown, as below. Show the title and the full body in the chat,
so the user can copy them, and save them to files if the user asks.

Title: `Prometheus Exporter Plugin X.Y.Z.N released`

Body:

```markdown
Hi everyone,

**Prometheus Exporter Plugin X.Y.Z.N for OpenSearch X.Y.Z has been released.**

Release notes and download: https://github.com/opensearch-project/opensearch-prometheus-exporter/releases/tag/X.Y.Z.N

Install:

    ./bin/opensearch-plugin install https://github.com/opensearch-project/opensearch-prometheus-exporter/releases/download/X.Y.Z.N/prometheus-exporter-X.Y.Z.N.zip

<!-- Release highlights: take them from the release notes, one bullet per user-facing change, each linked to
     its PR. Group them under "New features:" (new metrics or settings), "Enhancements:" (improvements of
     existing metrics or behavior) and "Bug fixes:", in this order, and keep only the groups that have entries.
     When a change does not clearly fit one group, ask. Mention deprecations (old metric names, settings)
     and when they will be removed. Leave out dependency, build, CI and docs changes; if the release has
     more than the listed changes, keep the "… and more." line.
     For a plain version bump with no user-facing changes, leave out the whole highlights section.
     For a plugin patch release, start the section with what it fixes and that users of
     X.Y.Z.(N-1) should upgrade. -->
**Release highlights**

New features:

* <feature> ([#<n>](https://github.com/opensearch-project/opensearch-prometheus-exporter/pull/<n>))

Enhancements:

* <enhancement> ([#<n>](https://github.com/opensearch-project/opensearch-prometheus-exporter/pull/<n>))

Bug fixes:

* <fix> ([#<n>](https://github.com/opensearch-project/opensearch-prometheus-exporter/pull/<n>))

… and more.

<!-- New contributors: authors (not bots) of PRs in this release whose first merged PR in the repository
     is in this release. Check each author with
     gh search prs -R opensearch-project/opensearch-prometheus-exporter --author <login> --merged --sort created --order asc --limit 1
     and compare the PR with the previous release. Leave out this section if there are none. -->
Welcome to our new contributors:

* [@<login>](https://github.com/<login>)

Thanks to everyone who contributed!

Feedback welcome. Please report bugs and feature requests in [Issues](https://github.com/opensearch-project/opensearch-prometheus-exporter/issues).

Your Prometheus Exporter Plugin maintenance team
```
