# Contributing Guidelines

There are two main ways to contribute to the project: by submitting issues and by submitting 
fixes/changes/improvements via pull requests.

## Submitting issues

Both bug reports and feature requests are welcome. Submit issues 
[here](https://github.com/ealva-com/welite/issues).

* Search for existing issues to avoid reporting duplicates.
* When submitting a defect report:
  * Test it against the most recently released version, including SNAPSHOT. It may have already 
  been fixed.
  * Include the code that reproduces the problem. Provide the complete reproducer code, yet 
  minimize it as much as possible.
  * Don't put off reporting any weird or rarely appearing issues just because you cannot 
  consistently reproduce them.
  * If the bug is in behavior, then explain expected behavior vs current behavior.  
* When submitting a feature request:
  * Explain why you need the feature: what iss your use-case, what's your domain.
  * Explaining the problem you face is more important than suggesting a solution. 
    Report your problem even if you don't have any proposed solution.
  * If there is an alternative way to do what you need, then show the code of the alternative.

## Submitting PRs

Submit PRs [here](https://github.com/ealva-com/welite/pulls).
However, please keep in mind that maintainers will have to support the resulting code of the 
project, so familiarize yourself with the following guidelines and use Detekt with the
configuration found [here](https://github.com/ealva-com/welite/blob/main/config/detekt/detekt.yml).

* All development (both new features and bug fixes) is performed in the `develop` branch.
  * The `main` branch always contains sources of the most recently released version.
  * Base PRs against the `develop` branch.
  * The `develop` branch is merged with the `main` branch during release.
* If you make any code changes:
  * Follow the [project coding conventions](https://github.com/ealva-com/welite/blob/main/config/detekt/detekt.yml). 
    Use 2 spaces for indentation, 4 spaces for continuation indents.
  * Build the project to make sure it all works and passes the tests.
* If you fix a bug:
  * Write a test(s) that reproduces the bug.
  * Fixes without tests are unlikely to be accepted.
  * Follow the style of writing tests in this project. Most tests are in a shared area
  to better facilitate unit testing and testing against various Android versions in androidTest.
* If you introduce any new public APIs:
  * All new APIs must come with documentation and tests.
  * If you plan large API additions, then please start by submitting an issue with the proposed API 
  design to gather feedback.
  * Contact maintainers to coordinate any big piece of work in advance.
* Comment on the existing issue if you want to work on it. Ensure that the issue not only describes 
  a problem, but also describes a solution that has received positive feedback. Propose a solution 
  if there isn't any.

## Releases

* Full release procedure checklist is [here](RELEASING.md) and is performed by a library maintainer.

## Contacting maintainers

* If something cannot be done, not convenient, or does not work &mdash; submit an issue.
