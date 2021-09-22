[![Next build Status](https://github.com/eclipse-che/che-theia/actions/workflows/next-build.yml/badge.svg)]
[![Release build Status](https://github.com/eclipse-che/che-theia/actions/workflows/release.yml/badge.svg)]

# What is Che server
Che Server is a core component of the [Eclipse Che](https://github.com/eclipse/che/). This component is responsibe for creation and managing of Che workspaces, but will some day be replaced by the [Dev Workspace Operator](https://github.com/devfile/devworkspace-operator). 

# Project structure
Che Server is mostly a Java web application deployed on a Apache Tomcat server in a container. 
- ['pom.xml'](https://github.com/eclipse-che/che-server/tree/main/pom.xml) The root Maven module, that lists all dependencies and structure. 
- ['assembly'](https://github.com/eclipse-che/che-server/tree/main/assembly) - module for final assemblies of Che web applications
- ['dockerfiles'](https://github.com/eclipse-che/che-server/tree/main/dockerfiles) - directory contains image Dockerfile for Che Server, as well as additional images.
- ['core'](https://github.com/eclipse-che/che-server/tree/main/core) - core and utility modules for Che.
- ['wsmaster'](https://github.com/eclipse-che/che-server/tree/main/wsmaster) - primary modules of the Che Server API.
- ['multiuser'](https://github.com/eclipse-che/che-server/tree/main/multiuser) - modules related to multiuser implementation of Che.
- ['infrastructure'](https://github.com/eclipse-che/che-server/tree/main/infrastructure) - implementations for the underlying infrastructure, on which Che is running (Kubernetes, Openshift, etc.)
- ['deploy'](https://github.com/eclipse-che/che-server/tree/main/deploy) - deployment files for Helm installation.
- ['typescript-dto'](https://github.com/eclipse-che/che-server/tree/main/typescript-dto) module, that provides DTO objects for typescript projects that may depend on Che Server, such as Che Theia.

# Build requirements
- Apache Maven 3.6.3 or Higher
- JDK Version 11
- Podman or Docker (required for running integration tests)

# Build and debug
Run `mvn clean install` to build 
Activate a faster profile build by adding `-Pfast`
To debug, run `mvn clean install -X` and connect your IDE to the debug port

# CI
There are several [GitHub Actions](https://github.com/eclipse-che/che-server/actions) workflows implemented for this repository:

- [`next`](https://github.com/eclipse-che/che-server/actions/workflows/next-build.yml) - builds Maven artifacts, builds container images and pushes them to [quay.io](https://quay.io/organization/eclipse) on each commit to [`main`](https://github.com/eclipse-che/che-server/tree/main) branch.
- [`release`](https://github.com/eclipse-che/che-server/actions/workflows/release.yml) - builds Maven artifacts and container images. Images are public and pushed to [quay.io](https://quay.io/organization/eclipse). See [RELEASE.md](https://github.com/eclipse-che/che-server/blob/master/RELEASE.md) for more information about this workflow.
- [`release-changelog`](https://github.com/eclipse-che/che-server/actions/workflows/release-changelog.yml) - create GitHub release and generate, which will include a generated changelog
- [`build-pr-check`](https://github.com/eclipse-che/che-server/actions/workflows/build-pr-check.yml) - builds Maven artifacts and container images. This workflow is used as a pull request check for all pull requests, that are submitted to this project 
- [`sonar`](https://github.com/eclipse-che/che-server/actions/workflows/sonar.yml) - This check runs Sonar against the main branch. The result can be seen here https://sonarcloud.io/dashboard?id=org.eclipse.che%3Ache-server
# License

- [Eclipse Public License 2.0](LICENSE)

# Join the community

The Eclipse Che community is globally reachable through public chat rooms, mailing list and weekly calls.
See https://www.eclipse.org/che/docs/che-7/overview/introduction-to-eclipse-che/#_joining_the_community

## Report issues

Issues are tracked on the main Eclipse Che Repository: https://github.com/eclipse/che/issues
