# Security Issue Response Process

## Introduction

Security issues happen as part of the normal lifecycle of software development of OpenSearch. This document describes the process for reporting these issues, responding to these issues, and ensuring we treat them not only with the appropriate priority but also with respect for the discoverers, software providers and their users, and the OpenSearch community in general.

If you discover a potential security issue in this project we ask that you notify the OpenSearch Security Team via email to security@opensearch.org. Please do **not** create a public GitHub issue.

*Giving credit where credit is due, this policy is heavily influenced by the [Xen Project’s security response process](https://xenproject.org/developers/security-policy/), that was put to the test during the [embargo period for XSA-108 back in 2014](https://xenproject.org/2014/10/22/xen-project-security-policy-improvements-get-involved/) and improved its clarity around managing the pre-disclosure list and the deployment of fixes during embargo. We are standing on the shoulders of these battle-tested giants.*

## Security Response Team (SRT)

The OpenSearch Security Response Team (SRT) is comprised of a subset of the project’s maintainers responsible for looking after the project’s security, including the security issue response process outlined below. New SRT members are nominated by current SRT members.

SRT will address reported issues on a best effort basis, prioritizing them based on several factors, including severity.

### Current Members

| Name                     | GitHub Alias                                                | Affiliation |
| ------------------------ | ----------------------------------------------------------- | ----------- |
| Kunal Khatua             | [kkhatua](https://github.com/kkhatua)                       | Amazon      |
| Prabhat Chathurvedi      | [prabhat-chaturvedi](https://github.com/prabhat-chaturvedi) | Amazon      |
| Craig Perkins            | [cwperks](https://github.com/cwperks)                       | Amazon      |
| Shikhar Jain             | [shikharj05](https://github.com/shikharj05)                 | Amazon      |
| Gulshan Kumar            | [kumargu](https://github.com/kumargu)                       | Amazon      |
| Aayush Singhal           | [Aayush8394](https://github.com/Aayush8394)                 | Amazon      |
| Nils Bandener            | [nibix](https://github.com/nibix)                           | Eliatra     |

### Emeritus

| Name                     | GitHub Alias                                                | Affiliation |
| ------------------------ | ----------------------------------------------------------- | ----------- |
| Ryan Liang               | [RyanL1997](https://github.com/RyanL1997)                   | Amazon      |
| Varun Lodaya             | [varun-lodaya](https://github.com/varun-lodaya)             | Amazon      |
| Andriy Redko             | [reta](https://github.com/reta)                             | Aiven       |
| Andrey Pleskach          | [willyborankin](https://github.com/willyborankin)           | Aiven       |

## Process

Anyone finding an issue that is already publicly disclosed (for example, a CVE in one of the project’s dependencies) should feel free to create an issue and discuss openly on GitHub. The process below is only intended for issues that have not been publicly disclosed yet.

1. We request that instead of opening a GitHub issue, it is reported via email at security@opensearch.org. Please include a description of the issue, and any other information that could help in the reproduction and creation of a fix (version numbers, configuration values, reproduction steps...)
2. The OpenSearch Security Team will negotiate the conditions for an embargo period and a disclosure timeline with the discoverer (see [Embargo Schedule](#embargo-schedule)).
3. After the vulnerability is confirmed, if no CVE number is already reserved, the OpenSearch Security Team will reserve one, and communicate it to the discoverer and all parties in the pre-disclosure list (see [Pre-disclosure list](#pre-disclosure-list)).
4. As soon as our advisory is available, we will send it, including patches, to members of the pre-disclosure list. In the event that we do not have a patch available 2 working weeks before the disclosure date, we aim to send an advisory that reflects the current state of knowledge to the pre-disclosure list. An updated advisory will be published as soon as available. At this stage, the advisory will be clearly marked with the embargo date.
5. On the day the embargo is lifted, we will publish the advisory and release the new versions containing the fix.
6. If new information and/or better fixes become available, new advisory versions will be released.

During an embargo period, the OpenSearch Security Team may be required to make potentially controversial decisions in private, since they cannot confer with the community without breaking the embargo. The team will attempt to make such decisions following the guidance of this document and, where necessary, their own best judgement. Following the embargo period, on a best effort basis, the Security Team will disclose any such decisions, including the reasoning behind them, in the interests of transparency and to help provide guidance should a similar decision be required in the future.

## Embargo Schedule

Embargo periods will be negotiated on a case-by-case basis depending on the severity of the issue and availability of the fix, where a general starting point is to release an advisory to the pre-disclosure list within 2 weeks of the initial notification, and publicly releasing the advisory within 4 weeks of the advisory pre-release.

When a discoverer reports a problem to us and requests longer delays than we would consider ideal, we will honor such a request if reasonable. If a discoverer wants an accelerated disclosure compared to what we would prefer, we naturally do not have the power to insist that a discoverer waits for us to be ready and will honor the date specified by the discoverer.
Naturally, if a vulnerability is being exploited in the wild, we will make immediately public release of the patch(es.)

## Pre-disclosure List

We maintain a pre-disclosure list of contributors, vendors and operators of OpenSearch for two main reasons:

1. To apply the fixes to large user populations requiring a significant amount of work post-disclosure
2. To privately collaborate with those who can help write and test the fixes so we can release them as soon as possible and with high confidence in their quality

If there is an embargo, the pre-disclosure list will receive copies of the advisory and patches, with a clearly marked embargo date, as soon as they are available. The pre-disclosure list will also receive copies of public advisories when they are first issued or updated.

We expect list members to maintain the confidentiality of the vulnerability up to the embargo date. Specifically, prior to the embargo date, pre-disclosure list members should not make available, even to their own customers and partners:

* The OpenSearch advisory
* Their own advisory
* The impact, scope, set of vulnerable systems or the nature of the vulnerability
* Revision control commits which are a fix for the problem
* Patched software (even in binary form)

Without prior consultation with the OpenSearch Security Team, list members may make available to their users only:

* The existence of an issue
* The assigned OpenSearch advisory number
* The planned disclosure date

List members may, if (and only if) the OpenSearch Security Team grants permission, deploy fixed versions during the embargo. Permission for deployment, and any restrictions, will be stated in the embargoed advisory text. Where the list member is a service provider who intends to take disruptive action such as rebooting as part of deploying a fix: the list member’s communications to its users about the service disruption may mention that the disruption is to correct a security issue, and relate it to the public information about the issue (as listed above). This applies whether the deployment occurs during the embargo (with permission–see above) or is planned for after the end of the embargo.

Pre-disclosure list members are allowed to share fixes to embargoed issues, analysis, etc., with the OpenSearch Security Team and security teams of other list members. Technical measures must be taken to prevent non-list-member organizations, or unauthorized staff in list-member organizations, from obtaining the embargoed materials.

## Pre-disclosure list application process

Organizations who meet the criteria above (i.e. significant work needed post-disclosure to remediate the issue and/or ability to help create or test the potential fixes) should contact the OpenSearch Security Team via email at security@opensearch.org if they wish to be added to the pre-disclosure list. In the email, you must include:

* The name of your organization
* How you’re using OpenSearch
* A description of why you fit the criteria (number of users, amount of work needed to remediate, ability to collaborate on fixes...)
* Information about your handling of security problems
    * Your invitation to members of the public, who discover security problems with your products/services, to report them in confidence to you
    * Specifically, the contact information (email addresses or other contact instructions) which such a member of the public should use
* A statement to the effect that you have read this policy and agree to abide by the terms for inclusion in the list, specifically the requirements to regarding confidentiality during an embargo period
* The email(s) you wish added to the pre-disclosure list

The OpenSearch Security Team will review your application and get back to you with their decision.