# What is Che server
Che Server is a core component of the [Eclipse Che](https://github.com/eclipse/che/). This component is responsible for creation and managing of Che workspaces, but will some day be replaced by the [Dev Workspace Operator](https://github.com/devfile/devworkspace-operator). 

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

- [![build-next](https://github.com/eclipse-che/che-server/actions/workflows/next-build.yml/badge.svg)](https://github.com/eclipse-che/che-server/actions/workflows/next-build.yml)  
Builds Maven artifacts, builds container images and pushes them to [quay.io](https://quay.io/organization/eclipse) on each commit to [`main`](https://github.com/eclipse-che/che-server/tree/main) branch.
- [![Release Che Server](https://github.com/eclipse-che/che-server/actions/workflows/release.yml/badge.svg)](https://github.com/eclipse-che/che-server/actions/workflows/release.yml)  
Builds Maven artifacts and container images. Images are public and pushed to [quay.io](https://quay.io/organization/eclipse). See [RELEASE.md](https://github.com/eclipse-che/che-server/blob/master/RELEASE.md) for more information about this workflow.
- [![Release Changelog](https://github.com/eclipse-che/che-server/actions/workflows/release-changelog.yml/badge.svg)](https://github.com/eclipse-che/che-server/actions/workflows/release-changelog.yml)  
Creates a GitHub release which will include a generated changelog.
- [![Update Che docs variables](https://github.com/eclipse-che/che-server/actions/workflows/che-properties-docs-update.yml/badge.svg)](https://github.com/eclipse-che/che-server/actions/workflows/che-properties-docs-update.yml/badge.svg)  
Runs on each commit to [`main`](https://github.com/eclipse-che/che-server/tree/main) branch.
- [![build-pr-check](https://github.com/eclipse-che/che-server/actions/workflows/build-pr-check.yml/badge.svg)](https://github.com/eclipse-che/che-server/actions/workflows/build-pr-check.yml)  
Builds Maven artifacts and container images. This workflow is used as a check for all pull requests that are submitted to this project.
- [![Sonar](https://github.com/eclipse-che/che-server/actions/workflows/sonar.yaml/badge.svg)](https://github.com/eclipse-che/che-server/actions/workflows/sonar.yaml)  
Runs Sonar against the main branch. The result can be seen [here](https://sonarcloud.io/dashboard?id=org.eclipse.che%3Ache-server).
- [![Try in Web IDE](https://github.com/eclipse-che/che-server/actions/workflows/try-in-web-ide.yaml/badge.svg)](https://github.com/eclipse-che/che-server/actions/workflows/try-in-web-ide.yaml)  
Used as a check for pull requests that are submitted to this project. 

Downstream builds can be found at the link below, which is _internal to Red Hat_. Stable builds can be found by replacing the 3.x with a specific version like 3.2. 

- [server_3.x](https://main-jenkins-csb-crwqe.apps.ocp-c1.prod.psi.redhat.com/job/DS_CI/job/server_3.x/)

# License

- [Eclipse Public License 2.0](LICENSE)

# Join the community

The Eclipse Che community is globally reachable through public chat rooms, mailing list and weekly calls.
See https://www.eclipse.org/che/docs/che-7/overview/introduction-to-eclipse-che/#_joining_the_community

## Report issues

Issues are tracked on the main Eclipse Che Repository: https://github.com/eclipse/che/issues
