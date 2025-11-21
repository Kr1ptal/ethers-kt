# Contributing to ethers-kt

**THANK YOU** for taking the time to contribute to ethers-kt!

We are happy to have you here! Opportunities to get involved with ethers-kt are open to everyone, no matter your
level of expertise.

**We greatly value and appreciate every single contribution, regardless of its size.**

This guide is designed to assist you in getting started. Don't feel overwhelmed by it. Think of it as a helpful roadmap
to guide you through the process.

### Table Of Contents

[Code of Conduct](#-code-of-conduct)

[I just have a quick question!](#-i-just-have-a-quick-question)

[How Can I Contribute?](#-how-can-i-contribute)

* [Reporting Bugs](#-reporting-bugs)
* [Suggesting Enhancements](#-suggesting-enhancements)
* [Your First Code Contribution](#-your-first-code-contribution)
* [Pull Requests](#-pull-requests)
    * [Graphite tool](#graphite-tool)
    * [Commits](#commits)
    * [Tests](#tests)
* [Labeling](#-labeling)
    * [Issue labels](#issue-labels)

## 🤝 Code of Conduct

The project and all participants are subject to the guidelines outlined in
the [Code of Conduct](https://github.com/Kr1ptal/ethers-kt/blob/master/CODE_OF_CONDUCT.md). Your active participation
implies your commitment to following these standards. If you come across any behavior that violates these
guidelines, please report it to [report@kriptal.io](mailto:report@kriptal.io).

> ## ❓ I just have a quick question!
>
> Please open a thread under [Discussions](https://github.com/Kr1ptal/ethers-kt/discussions) instead of creating an
> issue.
>

## 👥 How Can I Contribute?

### 🔎 Reporting Bugs

Bug reports are collected through GitHub issues. Prior to creating a new report, please review
the [bug reports list][search-label-type-bug] as you may discover that a similar bug has already been reported.

> If you find a **Closed** issue that appears to be the same problem you're experiencing, please open a new issue and
> include a link to the original one in the body of your new bug report.

Once you've confirmed that the bug hasn't been reported, kindly adhere to these guidelines when reporting it:

- **Use a clear and descriptive title** for the issue to identify the bug report.
- **Describe the exact steps to reproduce the problem** with as much detail as possible.å
- **Provide specific examples** to replicate the issue. Include links to files or GitHub projects, or code
  snippets that can be used to reproduce the problem. Refer
  to [How to create a Minimal, Reproducible Example](https://stackoverflow.com/help/minimal-reproducible-example)
  article.
- **Describe the behavior you observed after following the steps and point out what exactly is the problem** with that
  behavior.
- **Explain which behavior is expected** to see instead and why. Include pseudocode, sketches, snapshots, links or other
  resources that help you describe steps and clearly demonstrate the problem.
- If available, please **include a stack trace**.
- If the problem is related to performance or memory, **include a CPU profile capture with your report**.
- If the problem wasn't triggered by a specific action, **describe your actions leading up to the issue**.

The two key pieces of information necessary for us to effectively assess the report are a detailed description of the
observed behavior and a straightforward test case that allows us to replicate the problem. Without the ability to
recreate the issue, it becomes impossible for us to fix it.

### ✨ Suggesting Enhancements

Enhancement suggestions are monitored through GitHub issues. Before you create one, please review
the [feature request list][search-label-type-feature] as you might find out a similar suggestion already exists. When
proposing an enhancement, ensure to provide comprehensive information - utilize the provided template and describe the
steps to implement the requested feature. To create an effective enhancement suggestion, please follow these guidelines:

- **Use a clear and descriptive title** for the issue to identify the suggestion.
- **Provide a step-by-step description** of the suggested enhancement with as many details as possible.
- **Include specific examples** to demonstrate the idea. Include pseudocode, sketches, snapshots, links or other
  resources that help you explain the idea.
- **List some other use cases** where this enhancement exists.

### 🧑‍💻 Your First Code Contribution

Not sure where to get started? You can begin by exploring issues with the following labels:

- [difficulty: easy][search-label-difficulty-easy] - Tasks that typically involve just a few lines of code and a couple
  of tests, making them suitable for beginners.
- [type: research][search-label-type-research] - A bit more involved tasks that are great for contributors looking for a
  slightly more complex challenge, which require additional research before resolving them.

If you're still unsure where to begin, feel free to reach out to us!

### 📝 Pull Requests

Pull Requests (PRs) serve as the primary means for implementing **code changes**, **documentation**, and
**dependencies** within the **ethers-kt** repository. We appreciate even the tiniest PRs, such as correcting a
single-character typo, fixing the indents or just providing some additional clarification to the code.

Before making a substantial change, it's typically a good practice to initiate an issue that describes the proposed
modification. This step helps to obtain feedback and guidance, ultimately increasing the likelihood of your PR being
merged.

To have your contribution considered, please adhere these steps:

1. Try to keep pull requests small. If needed, break your changes into multiple pull requests.
2. Follow the [commits](#commits) and [tests](#tests) guidelines.
3. Try to complete all instructions provided in the PR template. Feel free to skip parts that are unknown or irrelevant
   to your pull request.
4. After submitting your pull request, ensure that all checks are successfully passing. <details><summary>Checks are
   failing. What should I do?</summary> Don't worry! If the checks are failing, and you believe this is unrelated to
   your changes, please leave a comment on the pull request. Explain your perspective on why the failure is
   unrelated and a maintainer will re-run the status check on your behalf. If it's determined that the failure was a
   false positive, we will open an issue to address the problem within our check suite.</details>

While it is essential to meet the prerequisites above before having your pull request reviewed, the reviewers may
request additional design work, tests, or other adjustments to ensure that your pull request aligns with our standards
and objectives.

#### Graphite tool

We recommend contributors to use the [Graphite tool](https://graphite.dev/docs/intro-to-graphite). It is designed to
write and review smaller pull request, stay unblocked, and ship faster. It enhances your normal `git` workflow with:

1. Helping you keep the pull requests small.
2. Syncing your trunk branch with remote and handling all the rebasing complexity for your working branches.
3. Encouraging you to use one commit (or small context related commits) per branch.

Making the unit of change a PR instead of a commit makes testing, reviewing, landing, and reverting changes much easier.

#### Commits

To clearly communicate the nature of the changes, commit messages must follow
the adopted [Conventional Commits specification](https://www.conventionalcommits.org/en/v1.0.0/):

- Commit message structure:
  ```
  <type>[optional scope]: <description>
  
  [optional body]
  ```

- `<type>` conventions:
    - `fix:` = commit patching a bug in your codebase
    - `feat:` = commit introducing a new feature to the codebase
    - `BREAKING CHANGE:` footer or `!` after the type/scope = commit that contains a breaking change. It can be part of
      commits of any type.
    - **other types** are allowed - `chore:`, `ci:`, `docs:`, `refactor:`, `test:`.

- An `[optional scope]` **MAY** be provided after a `<type>`. A scope **MUST** consist of a noun describing a section of
  the codebase surrounded by parenthesis, e.g., `fix(providers)`.

- A `<description>` **MUST** immediately follow the colon and space after the `type/scope` prefix. The description is a
  short summary of the code changes,
  e.g., `fix(providers): array parsing issue when multiple spaces were contained in string`.

- A longer free-form `[optional body]` **MAY** be provided after the short description, providing additional contextual
  information about the code changes. The body **MUST** begin one blank line after the description.

#### Tests

After either adding a new functionality to **ethers-kt** or fixing an existing, broken functionality, the pull request
should include one or more tests to ensure library does not regress in the futures.

To easily identify which parts of the code are not already tested use the [JaCoCo](https://www.jacoco.org) code coverage
report generated by the following command:

```
./gradlew testCodeCoverageReport
```

The report is generated in the `./build/reports/jacoco/testCodeCoverageReport/html/index.html` file. Based on this
information add tests to cover the missing parts of the code.

### 🚩 Labeling

Labels simplify the process of locating issues or pull requests that you're interested in and help us track progress.
Each label listed below offers quick search links to help you find open items marked with that label.

While the labels are grouped according to their intended purpose, it's not mandatory for every issue to have a label
from each group, and it's possible for an issue to have more than one label from the same group.

#### Issue labels

Issue labels are organized into three primary categories: `type`, `status`, `difficulty`, and `chain`:

| Label name            | 🔎 Quick search                          | Description                                                                                                                                 |
|-----------------------|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `type: bug`           | [search][search-label-type-bug]          | Confirmed bugs or reports that are very likely to be bugs.                                                                                  |
| `type: feature`       | [search][search-label-type-feature]      | Feature requests.                                                                                                                           |
| `type: chore`         | [search][search-label-type-chore]        | Involves routine, non-critical, or housekeeping work, contributing to project maintenance and codebase cleanliness.                         |
| `type: research`      | [search][search-label-type-research]     | Gather new or additional information as part of research activities within the project.                                                     |
| `type: epic`          | [search][search-label-type-epic]         | A large task which should be broken down into multiple smaller ones.                                                                        |
| `status: duplicate`   | [search][search-label-status-duplicate]  | Duplicates of other issues which have been previously reported.                                                                             |
| `status: blocked`     | [search][search-label-status-blocked]    | Blocked on other issues.                                                                                                                    |
| `status: invalid`     | [search][search-label-status-invalid]    | Invalid issues, including those that cannot be replicated.                                                                                  |
| `status: wontfix`     | [search][search-label-status-wontfix]    | Team has decided not to address these issues at the moment, either because they are functioning as intended or due to other considerations. |
| `difficulty: easy`    | [search][search-label-difficulty-easy]   | Easy issues to resolve, suitable for beginners.                                                                                             |
| `difficulty: medium`  | [search][search-label-difficulty-medium] | Medium issues requiring additional skills.                                                                                                  |
| `difficulty: hard`    | [search][search-label-difficulty-hard]   | Hard issues to resolve, which usually involve additional discussion with maintainers before being implemented.                              |
| `chain: <chain_name>` | /                                        | Blockchain specific issues.                                                                                                                 |

[search-label-type-bug]: https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22type%3A+bug%22

[search-label-type-feature]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22type%3A+feature%22

[search-label-type-chore]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22type%3A+chore%22

[search-label-type-research]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22type%3A+research%22

[search-label-type-epic]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22type%3A+epic%22

[search-label-status-duplicate]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22status%3A+duplicate%22

[search-label-status-blocked]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22status%3A+blocked%22

[search-label-status-invalid]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22status%3A+invalid%22

[search-label-status-wontfix]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22status%3A+wontfix%22

[search-label-difficulty-easy]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22difficulty%3A+easy%22

[search-label-difficulty-medium]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22difficulty%3A+medium%22

[search-label-difficulty-hard]:  https://github.com/Kr1ptal/ethers-kt/issues?q=is%3Aissue+is%3Aopen+label%3A%22difficulty%3A+hard%22
