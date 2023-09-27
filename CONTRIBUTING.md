# Build requirements
- Apache Maven 3.9 or later
- JDK 11
- Podman or Docker (required for running integration tests)
A Che workspace environment allows to build the image internaly, using the workspace terminal.

# Sources build
Run `mvn clean install` to build. Activate a faster profile build by adding `-Pfast`.

# Image build and push
1. Go to the `dockerfiles` directory.
2. Run `./build.sh`.
3. Tag the **che-server** image with your account: `docker tag quay.io/eclipse/che-server:next <docker registry>/<your account>/che-server:next`.
4. Push the **che-server** image to your account: `docker push <docker registry>/<your account>/che-server:next`.

# Start and debug
1. Deploy Che to a [Red Hat OpenShift](https://www.eclipse.org/che/docs/stable/administration-guide/installing-che-on-openshift-using-cli/) or [Minikube](https://www.eclipse.org/che/docs/stable/administration-guide/installing-che-on-minikube/) cluster by using a previously built image: `chectl server:start --platform=<openshift / minikube> --cheimage=<docker registry>/<your account>/che-server:next`.
2. Enable local debugging of the Eclipse Che server: `chectl server:debug`.
3. In your IDE, create a new Remote JVM Debug configuration on `localhost:8000`.
4. Hit a breakpoint in the code and activate the debug configuration.

# Contributing an SCM provider
An SCM provider support has to be provided by adding new maven modules to the wsmaster directory.

## Implementing che-core-api-oauth-<SCM provider name> module
This module is responsible for Oauth requests to the SCM provider and contans next implementations:
1. `<SCM provider name>OAuthAuthenticator` contains specific implementation of Oauth token requestand authentication endpoint.
2. `<SCM provider name>OAuthAuthenticatorProvider` a provider of the `OAuthAuthenticator`.

## Implementing coresponding che-core-api-factory-<SCM provider name> module
This module is responsible for API operations of the specific SCM provider.
1. `<SCM provider name>ApiClient` contains all necessary HTTP API calls like `getUser()`.
2. `<SCM provider name>AuthorisingFileContentProvider` overrides the common `AuthorisingFileContentProvider` to define the specific `isPublicRepository()` function.
3. `<SCM provider name>FactoryParameterResolver` validates SCM URL if it corresponds to the SCM provider. Also Provides specific Factory Parameters resolver for the SCM repository.
4. `<SCM provider name>PersonalAccessTokenFetcher` fetches Personal Access Token from the SCM provider by reqesting the OAuth API of the provider. Validates the token by comparing the token access scopes with the predefined scope list.
5. `<SCM provider name>FileResolver` Implementation of a resolver that can return particular file content from specified SCM repository.
6. `<SCM provider name>URLParser` Parses the string representation of the URL to an SCM specific object
7. `<SCM provider name>UserDataFetcher` Implementation of a resolver that can return particular file content from specified SCM repository.

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

# Report issues
Issues are tracked on the main Eclipse Che Repository: https://github.com/eclipse/che/issues
