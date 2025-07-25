# Releasing a new version of OpenSearch Mixin

1. [Generate Grafana dashboard and Prometheus alert files](RELEASING.md#1-generate-grafana-dashboard-and-prometheus-alert-files)
2. [Create new release tag](RELEASING.md#2-create-new-release-tag)
3. [Create new release draft on GitHub](RELEASING.md#3-create-new-release-draft-on-github)
4. [Publish new release](RELEASING.md#4-publish-new-release)

## 1. Generate Grafana dashboard and Prometheus alert files

```shell
make clean & make
```

## 2. Create new release tag

The naming convention for mixin release tag is:

`mixin-<version>`

The `<version>` follows [semver](https://semver.org/) conventions.

To create the tag:

```sh
git fetch <upstream> --tags
git tag -s -a mixin-1.0.0-rc.1 -m "Release mixin-1.0.0-rc.1"
git tag -v mixin-1.0.0-rc.1
git push <upstream> mixin-1.0.0-rc.1
```

## 3. Create new release draft on GitHub

Upload generated files (from step 1.) and write release notes.

All files from the folder `distribution` are attached to the new release. At this moment this includes three files.
  - opensearch.json
  - prometheus_alerts.yaml
  - prometheus_recording_rules.yaml

## 4. Publish new release

Mark the draft release as public and release it. Consider writing announcement for OpenSearch forums (use `OpenSearch` category and `releases` tag).
