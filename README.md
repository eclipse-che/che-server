# What is Che server
Che Server provides an API for managing Kubernetes namespaces, and to retrieve devfile content from repositories,
hosted on GitHub, GitLab, Bitbucket, and Microsoft Azure Repos.

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

# Build requirements
- Apache Maven 3.6.3 or later
- JDK 11
- Podman or Docker (required for running integration tests)

# Sources build
Run `mvn clean install` to build. Activate a faster profile build by adding `-Pfast`.

# Image build and push
1. Go to the `dockerfiles` directory.
2. Run `./build.sh`.
3. Tag the **che-server** image with your account: `docker tag quay.io/eclipse/che-server:next <docker registry>/<your account>/che-server:next`.
4. Push the **che-server** image to your account: `docker push <docker registry>/<your account>/che-server:next`.

# Debug
1. Deploy Che to a [Red Hat OpenShift](https://www.eclipse.org/che/docs/stable/administration-guide/installing-che-on-openshift-using-cli/) or [Minikube](https://www.eclipse.org/che/docs/stable/administration-guide/installing-che-on-minikube/) cluster by using a previously built image: `chectl server:start --platform=<openshift / minikube> --cheimage=<docker registry>/<your account>/che-server:next`.
2. Enable local debugging of the Eclipse Che server: `chectl server:debug`.
3. In your IDE, create a new Remote JVM Debug configuration on `localhost:8000`.
4. Hit a breakpoint in the code and activate the debug configuration.


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
See the Eclipse Che Documentation about [how you can join our community](https://www.eclipse.org/che/docs/stable/overview/introduction-to-eclipse-che/#_joining_the_community).

## Report issues

Issues are tracked on the main Eclipse Che Repository: https://github.com/eclipse/che/issues
