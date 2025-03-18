# What is Che server
Che Server provides an API for managing Kubernetes namespaces and to retrieve devfile content from repositories,
hosted on GitHub, GitLab, Bitbucket and Microsoft Azure Repos.

# Project structure
Che Server is mostly a Java web application deployed on an Apache Tomcat server in a container. Che Server uses the following modules:     
### OAuth1 / OAuth2 API implementations
- wsmaster/che-core-api-auth
- wsmaster/che-core-api-azure-devops
- wsmaster/che-core-api-bitbucket
- wsmaster/che-core-api-github
- wsmaster/che-core-api-gitlab
### Factory flow implementations
- wsmaster/che-core-api-factory-azure-devops
- wsmaster/che-core-api-factory-bitbucket
- wsmaster/che-core-api-factory-bitbucket-server
- wsmaster/che-core-api-factory-github
- wsmaster/che-core-api-factory-gitlab
- wsmaster/che-core-api-factory-shared
### Kubernetes namespace provisioning
- infrastructures/kubernetes
- infrastructure/openshift
- infrastructures/infrastructure-factory

Other modules are deprecated and will be removed in the future.

# License

- [Eclipse Public License 2.0](LICENSE)

# Join the community

The Eclipse Che community is globally reachable through public chat rooms, mailing list and weekly calls.
See the Eclipse Che Documentation about [how you can join our community](https://www.eclipse.org/che/docs/stable/overview/introduction-to-eclipse-che/#_joining_the_community).

## Builds

* [![release latest stable](https://github.com/eclipse-che/che-server/actions/workflows/release.yml/badge.svg)](https://github.com/eclipse-che/che-server/actions/workflows/release.yml)

### Develocity

[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://develocity-staging.eclipse.org/)

The `che-server` builds are revved up by [Develocity](https://develocity-staging.eclipse.org/scans), which enhances developer productivity through features like Build Scan for build insights and collaborative troubleshooting and remote Build Cache for build acceleration. It further provides comprehensive reporting and visualization of build data.

You can find the Build Scan produced by a build by clicking on the generated link at the end of the build, both locally and on CI (given that you are authenticated to publish). You can find the all scans published by the `che-server` builds on the [Eclipse Develocity dashboard](https://develocity-staging.eclipse.org) by [searching](https://develocity-staging.eclipse.org/scans?search.rootProjectNames=che%20server) for the `che server` project.

Here are also some other useful links with the `che-server` builds data:
- [Build Scans](https://develocity-staging.eclipse.org/scans?search.rootProjectNames=che%20server)
- [Trends dashboard](https://develocity-staging.eclipse.org/scans/trends?search.rootProjectNames=che%20server)
- [Failures dashboard](https://develocity-staging.eclipse.org/scans/failures?search.rootProjectNames=che%20server)
- Failed and flaky [Tests dashboard](https://develocity-staging.eclipse.org/scans/tests?search.rootProjectNames=che%20server)
