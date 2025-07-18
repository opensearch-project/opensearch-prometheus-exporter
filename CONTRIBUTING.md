- [Contributing to OpenSearch](#contributing-to-opensearch)
- [First Things First](#first-things-first)
- [Ways to Contribute](#ways-to-contribute)
  - [Bug Reports](#bug-reports)
  - [Feature Requests \& Proposals](#feature-requests--proposals)
  - [Documentation Changes](#documentation-changes)
  - [Contributing Code](#contributing-code)
- [Developer Certificate of Origin](#developer-certificate-of-origin)
  - [Troubleshooting Failed DCO Checks](#troubleshooting-failed-dco-checks)
- [License Headers](#license-headers)
  - [Java](#java)
  - [Python, Ruby, Shell](#python-ruby-shell)
- [Review Process](#review-process)
- [Using Feature Branches](#using-feature-branches)
- [Experimental Features](#experimental-features)

## Contributing to OpenSearch

OpenSearch, a member of non-profit Linux Foundation, is a community project that is built and maintained by people just like **you**. We're glad you're interested in helping out. There are several different ways you can do it, but before we talk about that, let's talk about how to get started.

## First Things First

1. **When in doubt, open an issue** - For almost any type of contribution, the first step is opening an issue. Even if you think you already know what the solution is, writing down a description of the problem you're trying to solve will help everyone get context when they review your pull request. If it's truly a trivial change (e.g. spelling error), you can skip this step -- but as the subject says, when it doubt, [open an issue](https://github.com/opensearch-project/.github/issues).

2. **Only submit your own work**  (or work you have sufficient rights to submit) - Please make sure that any code or documentation you submit is your work or you have the rights to submit. We respect the intellectual property rights of others, and as part of contributing, we'll ask you to sign your contribution with a "Developer Certificate of Origin" (DCO) that states you have the rights to submit this work and you understand we'll use your contribution. There's more information about this topic in the [DCO section](#developer-certificate-of-origin).

3. When you're ready to start, we have [a step-by-step onboarding guide](ONBOARDING.md) to help you get oriented.

## Ways to Contribute

### Bug Reports

Ugh! Bugs!

A bug is when software behaves in a way that you didn't expect and the developer didn't intend. To help us understand what's going on, we first want to make sure you're working from the latest version.

Once you've confirmed that the bug still exists in the latest version, you'll want to check to make sure it's not something we already know about on the [open issues GitHub page](https://github.com/opensearch-project/.github/issues).

If you've upgraded to the latest version and you can't find it in our open issues list, then you'll need to tell us how to reproduce it Provide as much information as you can. You may think that the problem lies with your query, when actually it depends on how your data is indexed. The easier it is for us to recreate your problem, the faster it is likely to be fixed.

### Feature Requests & Proposals

If you've thought of a way that OpenSearch could be better, we want to hear about it. We track `feature requests` ([examples](https://github.com/search?q=org%3Aopensearch-project+%22Is+your+feature+request+related+to+a+problem%3F%22&type=Issues)) using GitHub, so please feel free to open an issue which describes the feature you would like to see, why you need it, and how it should work. If you would like contribute code toward building it, you might consider a `feature proposal` ([examples](https://github.com/search?q=org%3Aopensearch-project+%22How+did+you+come+up+with+this+proposal%3F%22&type=Issues)) instead. A feature proposal is the first step to helping the community better understand what you are planning to contribute, why it should be built, and collaborate on ensuring you have all the data points you need for implementation.

### Documentation Changes

There are two types of documentation in OpenSearch: developer documentation, which describes how OpenSearch is designed internally, and user documentation, which describes how to use OpenSearch. 

Developer documentation is maintained in the repository to which it pertains. The workflow for contributing developer documentation is the same as the one for contributing code.

User documentation is maintained in the [documentation-website](https://github.com/opensearch-project/documentation-website/) repo. You can find the rendered documentation at [opensearch.org/docs](https://opensearch.org/docs). To learn how to contribute user documentation, see the [contribution guidelines](https://github.com/opensearch-project/documentation-website/blob/main/CONTRIBUTING.md).

### Contributing Code

As with other types of contributions, the first step is to [open an issue on GitHub](https://github.com/opensearch-project/.github/issues/new/choose). Opening an issue before you make changes makes sure that someone else isn't already working on that particular problem. It also lets us all work together to find the right approach before you spend a bunch of time on a PR. So again, when in doubt, open an issue.

## Developer Certificate of Origin

OpenSearch is an open source product released under the Apache 2.0 license (see either [the Apache site](https://www.apache.org/licenses/LICENSE-2.0) or the [LICENSE.txt file](LICENSE.txt)). The Apache 2.0 license allows you to freely use, modify, distribute, and sell your own products that include Apache 2.0 licensed software.

We respect intellectual property rights of others and we want to make sure all incoming contributions are correctly attributed and licensed. A Developer Certificate of Origin (DCO) is a lightweight mechanism to do that.

The DCO is a declaration attached to every contribution made by every developer. That representation is important for legal purposes and was the community-developed outcome after a [$1 billion lawsuit by SCO against IBM](https://en.wikipedia.org/wiki/SCO%E2%80%93Linux_disputes). The representation is designed to prevent issues but also keep the burden on contributors low. It has proven very adaptable to other projects, is built into git itself (and now also GitHub), and is in use by thousands of projects to avoid more burdensome requirements to contribute (such as a CLA).

In the commit message of the contribution, the developer simply adds a `Signed-off-by` statement and thereby agrees to the DCO, which you can find below or at [DeveloperCertificate.org](http://developercertificate.org/).

```
Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the
    best of my knowledge, is covered under an appropriate open
    source license and I have the right under that license to
    submit that work with modifications, whether created in whole
    or in part by me, under the same open source license (unless
    I am permitted to submit under a different license), as
    Indicated in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including
    all personal information I submit with it, including my
    sign-off) is maintained indefinitely and may be redistributed
    consistent with this project or the open source license(s)
    involved.
 ```

We require that every contribution to OpenSearch is signed with a Developer Certificate of Origin. 

DCO checks are enabled via a [DCO workflow app](https://github.com/apps/dco) across the entire opensearch-project organization, and your PR will fail CI without it.

Additionally, we kindly ask you to use your real name. A real name does not require a legal name, nor a birth name, nor any name that appears on an official ID (e.g. a passport). Your real name is the name you convey to people in the community for them to use to identify you as you. The key concern is that your identification is sufficient enough to contact you if an issue were to arise in the future about your contribution. Thus, your real name should not be an anonymous id or false name that misrepresents who you are.

Each commit must include a DCO which looks like this, which includes a real name and a valid email address where you can receive emails:

```
Signed-off-by: Jane Smith <jane.smith@email.com>
```

You may type this line on your own when writing your commit messages. However, if your `user.name` and `user.email` are set in your `git config`, you can use `-s` or `--signoff` to add the `Signed-off-by` line to the end of the commit message automatically.

Forgot to add DCO to a commit? Amend it with `git commit --amend -s`.

### Troubleshooting Failed DCO Checks

The DCO workflow app requires signatures on every commit that's part of a PR.

If you've already signed all your commits and your PR still fails the DCO check, it's likely because the email address you signed the commit with doesn't match the one on your GitHub account.

To fix:

1. Go to [your GitHub email settings](https://github.com/settings/emails) and uncheck "Keep my email address private".
2. Following the [GitHub documentation](https://docs.github.com/en/account-and-profile/setting-up-and-managing-your-personal-account-on-github/managing-email-preferences/setting-your-commit-email-address#setting-your-commit-email-address-in-git), set your commit email address to your primary GitHub email address.

## License Headers

New files in your code contributions should contain the following license header. If you are modifying existing files with license headers, or including new files that already have license headers, do not remove or modify them without guidance.

### Java

```
/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
*/
```

### Python, Ruby, Shell
```
# Copyright OpenSearch Contributors
# SPDX-License-Identifier: Apache-2.0
#
# The OpenSearch Contributors require contributions made to
# this file be licensed under the Apache-2.0 license or a
# compatible open source license.
```

## Review Process

We deeply appreciate everyone who takes the time to make a contribution. We will review all contributions as quickly as possible. As a reminder, [opening an issue](https://github.com/opensearch-project/.github/issues/new/choose) discussing your change before you make it is the best way to smooth the PR process. This will prevent a rejection because someone else is already working on the problem, or because the solution is incompatible with the architectural direction.

During the PR process, expect that there will be some back-and-forth. Please try to respond to comments in a timely fashion, and if you don't wish to continue with the PR, let us know. If a PR takes too many iterations for its complexity or size, we may reject it. Additionally, if you stop responding we may close the PR as abandoned. In either case, if you feel this was done in error, please add a comment on the PR.

If we accept the PR, a [maintainer](MAINTAINERS.md) will merge your change and usually take care of backporting it to appropriate branches ourselves.

If we reject the PR, we will close the pull request with a comment explaining why. This decision isn't always final: if you feel we have misunderstood your intended change or otherwise think that we should reconsider then please continue the conversation with a comment on the PR and we'll do our best to address any further points you raise.

## Using Feature Branches

Our recommended approach for development is doing frequent small PR merges to main. This lets us catch integration issues earlier, makes it easier to review your PRs and makes your development visible to everyone.  It's okay if it's not the complete feature, as long as the PR won’t break a build or any existing functionality. 

But sometimes it may be useful to create a feature branch.  This allows you work on long-running disruptive features in isolation.   The reason we don't recommend it is because it still requires maintainer access to merge changes, and the overhead of rebasing is high.  If you do want to use a feature branch:
  
1. Treat feature branch PR's the same as PR's to `main`. CI should run on all PR's, no incomplete work should be merged, tests are necessary, etc.
   a. This maintains the code quality going into each feature making the integration to main PR's much easier and quicker.
   b. More visibility during development since it gives reviewers the necessary time to review each PR.
2. Please use Feature specific labels: This helps identify feature related issues and PR's.

All the safeguard here are not rules but guidelines and should be adopted by each repository based on their specific requirements. This is to ensure that feature branch development is less likely to have code quality issues and massive merge to main PR's.

For contributors looking to add a new feature that would require the creation of a feature branch, the process begins by opening an issue in the repo with the feature proposal. Depending on the nature of the feature, the maintainers of the project can decide to create a feature branch and use this model.

## Experimental Features

Experimental releases are used to gather early feedback on features. Features should only be marked experimental if there is a high likelihood that API or experience will change. We strongly advise people to avoid using experimental features in production as there is no guarantee the API or experience won't change before release. It is best to avoid experimental feature releases unless it is necessary. Our goal is to have all experimental features into production or removed within 2 minor releases.  We generally use [Feature Flags](https://github.com/opensearch-project/OpenSearch/blob/main/DEVELOPER_GUIDE.md#experimental-development) to isolate experimental features in backend code.