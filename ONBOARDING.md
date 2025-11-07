- [Getting Started as an OpenSearch Project Contributor](#getting-started-as-an-opensearch-project-contributor)
  - [Introduction](#introduction)
    - [Who is this for?](#who-is-this-for)
    - [What will this guide help you do?](#what-will-this-guide-help-you-do)
  - [Key Concepts](#key-concepts)
    - [About the OpenSearch Project](#about-the-opensearch-project)
    - [How We Work](#how-we-work)
    - [Contributor Expectations](#contributor-expectations)
    - [Resources and Links](#resources-and-links)
  - [Set Up Accounts and Access](#set-up-accounts-and-access)
    - [GitHub Account](#github-account)
    - [OpenSearch Forum and Slack Accounts](#opensearch-forum-and-slack-accounts)

# Getting Started as an OpenSearch Project Contributor

## Introduction

### Who is this for?

Anyone who's interested in contributing code (features, fixes, documentation) to one of the OpenSearch Project open-source repositories for the first time.

### What will this guide help you do?

1. Understand the key concepts of the OpenSearch Project and how to find additional resources.
2. Set up the accounts you'll need to contribute.

## Key Concepts

### About the OpenSearch Project

- **Read (3 min) [the project homepage](https://opensearch.org/)** to get a sense of what the OpenSearch software does and how it's used.
- **Read (4 min) "[About OpenSearch](https://opensearch.org/about.html)"** to understand the primary project components and development principles.
- Optional - Read founding documents to understand the project history
  - Read (2 min) "[Linux Foundation Announces OpenSearch Software Foundation](https://www.linuxfoundation.org/press/linux-foundation-announces-opensearch-software-foundation-to-foster-open-collaboration-in-search-and-analytics) from September, 2024.
  - Read (8 min) "[Introducing OpenSearch](https://aws.amazon.com/blogs/opensource/introducing-opensearch/)" from April, 2021.
  - Read (6 min) "[Stepping up for a truly open source Elasticsearch](https://aws.amazon.com/blogs/opensource/stepping-up-for-a-truly-open-source-elasticsearch/)" from January, 2021.

### How We Work

We model our way of working on "[the Apache Way](https://apache.org/theapacheway)" (optional - read 4 min). Specifically, we've borrowed and adapted:

- *Community Over Code:* A healthy community is a higher priority than good code. Strong communities can always rectify problems with their code, whereas an unhealthy community will likely struggle to maintain a codebase in a sustainable manner.
- *Earned Authority:* All individuals are given the opportunity to participate, but their influence is based on publicly earned merit – what they contribute to the community. Merit lies with the individual, does not expire, is not influenced by employment status or employer, and is non-transferable (merit earned in one project cannot be applied to another).
- *Open Communications:* All communications related to code and decision-making should be publicly accessible to ensure asynchronous collaboration, as necessitated by a globally-distributed community.
- *Community of Peers:* Individuals participate, not organizations. Roles are equal irrespective of title, votes hold equal weight, and contributions are made on a voluntary basis (even if paid to work on code).
- *Progress over Perfection:* Struggling with a test failure? Can't quite figure out why that data structure isn't doing what you expect? Feeling defeated by the complexities of that massively distributed compute engine? The community idea exists to help each other through difficult problems. So fail early, commit often, ask for help, and don't be afraid.

### Contributor Expectations

Contributors are subject to a code of conduct, and it's essential that all contributions are original work.

- **Read (6 min) [the OpenSearch Code of Conduct](https://opensearch.org/codeofconduct.html)**.
- **Read (6 min) [the OpenSearch CONTRIBUTING Guide](https://github.com/opensearch-project/.github/blob/main/CONTRIBUTING.md)**. Please make sure you understand the implications of the [Developer Certificate of Origin](https://github.com/opensearch-project/.github/blob/main/CONTRIBUTING.md#developer-certificate-of-origin).

### Resources and Links

There are more than 100 [GitHub repositories](https://github.com/orgs/opensearch-project/repositories?q=&type=public&language=&sort=) that are part of the OpenSearch project. Explore the list to get a sense of the project breadth and scope.

- The [roadmap](https://github.com/orgs/opensearch-project/projects/220) provides a single place to keep track of upcoming releases and current projects.

Looking for answers? While [the forum](https://forum.opensearch.org/) and [Slack](https://opensearch.org/slack.html) are always a good option, it's worth checking these first.

- [Frequently Asked Questions](https://opensearch.org/faq).
- [User Documentation](https://docs.opensearch.org/latest).
- Developer Documentation
  - Start with `DEVELOPER_GUIDE.md` in the repository you'll be working in (see [OpenSearch](https://github.com/opensearch-project/OpenSearch/blob/main/DEVELOPER_GUIDE.md) or [OpenSearch Dashboards](https://github.com/opensearch-project/OpenSearch-Dashboards/blob/main/DEVELOPER_GUIDE.md) guides).
  - Most repositories have many other documentation files. To find them, search for Markdown files (`*.md` - [example](https://github.com/search?q=repo%3Aopensearch-project%2FOpenSearch-Dashboards+language%3AMarkdown&type=code&l=Markdown)), either on GitHub or in your editor.

## Set Up Accounts and Access

### GitHub Account

To create issues, leave comments, or submit pull requests, you'll need a GitHub account. Already have a GitHub account? Great! If not, it's quick to get started. (We've summarized the most important steps for the OpenSearch project here, but for more complete information, see the [GitHub documentation](https://docs.github.com/en/get-started/onboarding/getting-started-with-your-github-account).)

1. **Do (2 min) Sign up for an account** by navigating to https://github.com/ and following the prompts ([source](https://docs.github.com/en/get-started/onboarding/getting-started-with-your-github-account#1-creating-an-account)).
2. **Do (4 min) [Verify your email address](https://docs.github.com/en/get-started/signing-up-for-github/verifying-your-email-address)**.
3. Recommended - Do (20 min) Set up GitHub to connect with SSH
    1. [Check for existing SSH key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/checking-for-existing-ssh-keys).
    2. [Generate new SSH key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent).
    3. [Add a new SSH key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/adding-a-new-ssh-key-to-your-github-account).
    4. [Test your SSH connection](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/testing-your-ssh-connection).
4. Recommended - Do (8 min) [Configure 2-factor authentication](https://docs.github.com/en/authentication/securing-your-account-with-two-factor-authentication-2fa/configuring-two-factor-authentication).
5. Optional - Read (5 min) If you're new to using GitHub or git, we recommend reviewing "[Using GitHub's tools and processes](https://docs.github.com/en/get-started/onboarding/getting-started-with-your-github-account#part-2-using-githubs-tools-and-processes)".

### OpenSearch Forum and Slack Accounts

There are multiple ways to [connect with the OpenSearch community](https://opensearch.org/connect.html), [the forum](https://forum.opensearch.org/) and [Slack](https://www.opensearch.org/slack.html) are the best places to start.

- **Do (2 min)** [create an OpenSearch forum](https://forum.opensearch.org/) account.
- **Do (2 min)** [create an OpenSearch Slack](https://www.opensearch.org/slack.html) account.
