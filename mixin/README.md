# Mixin for OpenSearch

This repository branch contains a "Mixin" for OpenSearch® which is a set of Grafana® Dashboard(s) and Prometheus® Rules and Alerts.

- [Introduction](#introduction)
- [Compatibility with the Prometheus Exporter Plugin](#compatibility-with-the-prometheus-exporter-plugin)
- [Release Notes](#release-notes)
- [Build Instructions](#build-instructions)
- [License](#license)
- [Trademarks & Attributions](#trademarks--attributions)

---

## Introduction

OpenSearch Grafana dashboard and Prometheus Rules and Alerts contained in this repository are meant to be a starting point for OpenSearch monitoring. The goal is to provide a solid foundation of essential metrics and alerts; however, it does not have the ambition to be the definitive and complete monitoring solution for every situation. In practice the set of important metrics and alerts is determined by a specific OpenSearch use case. Users are encouraged to explore their own specific monitoring needs and develop custom modifications of dashboards and alerts. Nevertheless, contributions back are warmly welcome.   

This work is a based on the effort that was started in <https://github.com/lukas-vlcek/elasticsearch-mixin> and later incubated in <https://github.com/openshift/elasticsearch-operator/tree/master/files> (see [#12](https://github.com/opensearch-project/opensearch-prometheus-exporter/issues/12)).

To learn more about the "mixin" concept please read <https://github.com/kubernetes-monitoring/kubernetes-mixin>.

## Compatibility with the Prometheus Exporter Plugin

There are no specific compatibility requirements or limitations. This means that no specific version of Prometheus exporter plugin for OpenSearch is required for the Opensearch mixin.
However, this can change in the future.

## Release Notes

See the [Release Notes](./RELEASE_NOTES.md) for details.

## Build Instructions

### Requirements

To build the mixin you need:
- [jsonnet](https://github.com/google/jsonnet)
- [jsonnet-bundler](https://github.com/jsonnet-bundler/jsonnet-bundler)
- [promtool](https://github.com/prometheus/prometheus) (it is a part of the Prometheus)

#### Installing dependencies on MacOS 

```shell
brew install jsonnet jsonnet-bundler prometheus
```

#### Installing dependencies on Linux

Installation on Linux based systems can be a bit more tricky because you may not find all required packages in official repos. In such case you can either build and install the dependency from its source code or find an alternative repo to install from (such as Copr, Snap, ...). Fortunately, it is also possible to setup `brew` on Linux [1,2,3] and then the installation is very straightforward: 

```shell
brew install jsonnet jsonnet-bundler prometheus
``` 
- [1] <https://docs.brew.sh/Homebrew-on-Linux>
- [2] <https://www.digitalocean.com/community/tutorials/how-to-install-and-use-homebrew-on-linux>
- [3] <https://fedoramagazine.org/using-homebrew-package-manager-on-fedora-linux/>

In any case you can verify that you have all required dependencies using the following command:

```shell
for tool in jsonnet jsonnetfmt jb promtool; do echo -n "[$tool]: "; eval "$tool --version"; done
# Example output:
[jsonnet]: Jsonnet commandline interpreter v0.19.1
[jsonnetfmt]: Jsonnet reformatter v0.19.1
[jb]: 0.5.1
[promtool]: promtool, version 2.40.2 (branch: non-git, revision: non-git)
  build user:       linuxbrew@7d6bf141d0d6
  build date:       20221117-15:41:17
  go version:       go1.19.3
  platform:         linux/amd64
```

### How to Build the Mixin

```shell
export MIXIN_FOLDER=prometheus-exporter-mixin
git clone git@github.com:aiven/prometheus-exporter-plugin-for-opensearch.git ${MIXIN_FOLDER}
cd ${MIXIN_FOLDER}
git checkout mixin
jb install
make clean && make
```
After this step the generated Grafana dashboards and Prometheus rules and alerts are found in the `distribution` folder.

```shell
ls --format='single-column' distribution/
# Output
opensearch.json
prometheus_alerts.yaml
prometheus_recording_rules.yaml
```

Some parameters and thresholds of Prometheus alerts can be customized in the [`config.libsonnet`](config.libsonnet) file (don't forget to call `make` again).

The `prometheus_alerts.yaml` and `prometheus_recording_rules.yaml` files then need to be passed to your Prometheus server, and the `opensearch.json` file need to be imported into you Grafana server. The exact details will depend on how you deploy your monitoring stack to Kubernetes.

## License

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Trademarks & Attributions

Prometheus is a registered trademark of The Linux Foundation. OpenSearch is a registered trademark of Amazon Web Services.
