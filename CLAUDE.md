# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Che Server is the backend component of Eclipse Che - a Kubernetes-native IDE platform. It provides REST APIs for managing Kubernetes namespaces and retrieving devfile content from various Git hosting services (GitHub, GitLab, Bitbucket, Azure DevOps).

**Tech Stack:** Java 11, Apache Maven, Jakarta EE, Google Guice (dependency injection), Fabric8 Kubernetes Client, deployed as a WAR on Apache Tomcat.

## Build Commands

### Standard Build (with tests)
```bash
mvn clean install
```

### Fast Build (skip tests and validation)
```bash
mvn clean install -V -e -Pfast -DskipTests -Dskip-validate-sources -Denforcer.skip=true
```

### Run Specific Test
```bash
# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName
```

### Build Container Image
```bash
./build/build.sh
```

### Run Integration Tests
Integration tests require Podman or Docker to be running. They use TestNG framework.

```bash
mvn verify -Pintegration
```

## Architecture

### Module Structure

The project is organized into five main Maven modules:

#### 1. **core/** - Foundation Layer
- `che-core-api-core`: Core APIs, DTOs, REST framework, WebSocket support
- `che-core-api-dto`: DTO serialization/deserialization framework
- `che-core-api-model`: Shared data models
- `commons/`: Common utilities (JSON, logging, dependency injection, tracing)
- `che-core-metrics-core`: Metrics collection (Micrometer/Prometheus)
- `che-core-tracing-*`: Distributed tracing (OpenTracing/Jaeger)

#### 2. **wsmaster/** - Business Logic Layer
Contains all REST API implementations organized by feature domain:

**OAuth/Authentication Modules** (pattern: `che-core-api-auth-<provider>`):
- `che-core-api-auth`: Base OAuth framework
- `che-core-api-auth-github`, `che-core-api-auth-gitlab`, `che-core-api-auth-bitbucket`, `che-core-api-auth-azure-devops`: Provider-specific OAuth implementations
- Each contains an `OAuthAuthenticator` and `OAuthAuthenticatorProvider`

**Factory Modules** (pattern: `che-core-api-factory-<provider>`):
- `che-core-api-factory-shared`: Common factory interfaces
- `che-core-api-factory-github`, `che-core-api-factory-gitlab`, `che-core-api-factory-bitbucket*`, `che-core-api-factory-azure-devops`: Provider-specific factory resolvers
- Each implements: `ApiClient`, `FactoryParametersResolver`, `PersonalAccessTokenFetcher`, `ScmFileResolver`, `URLParser`, `UserDataFetcher`

**Other wsmaster Modules**:
- `che-core-api-devfile`: Devfile parsing and validation
- `che-core-api-workspace`: Workspace lifecycle management
- `che-core-api-user`: User management APIs
- `che-core-api-ssh`: SSH key management
- `che-core-sql-schema`: Database schema definitions

#### 3. **infrastructures/** - Kubernetes Orchestration
- `kubernetes/`: Base Kubernetes infrastructure implementation (uses Fabric8 client)
- `openshift/`: OpenShift-specific extensions
- `infrastructure-factory`: Infrastructure abstraction layer
- `infrastructure-metrics`: Infrastructure-specific metrics

#### 4. **multiuser/** - Multi-tenancy
Permission and authentication modules for multi-user deployments.

#### 5. **assembly/** - Packaging
Assembles all modules into deployable WAR files.

### Key Architectural Patterns

**Dependency Injection**: Google Guice is used throughout. Each module defines a `*Module.java` class that binds interfaces to implementations.

**SCM Provider Pattern**: To add support for a new Git hosting provider, you must implement TWO modules:
1. `che-core-api-auth-<provider>` for OAuth authentication
2. `che-core-api-factory-<provider>` for devfile resolution and API operations

**Shared vs Provider-Specific**: Some providers have `-common` modules (e.g., `che-core-api-auth-github-common`) that contain shared logic between multiple instances of the same provider (e.g., github.com vs GitHub Enterprise).

## Development Workflow

### Running Tests
The project uses TestNG (not JUnit). Test classes follow the pattern `*Test.java`.

### Code Style
- Enforcer plugin validates dependencies and versions
- Source validation can be skipped with `-Dskip-validate-sources`

### Container Image Development
1. Build sources with fast profile
2. Run `./build/build.sh` to create container image `quay.io/eclipse/che-server:next`
3. Tag and push to your registry
4. Deploy with `chectl server:deploy --platform=<openshift|minikube> --cheimage=<your-image> --debug`

### Debugging
1. Deploy Che with `--debug` flag
2. Run `chectl server:debug` to enable remote debugging
3. Attach debugger to port exposed by chectl (default: 8000)

## Project-Specific Conventions

### Adding a New SCM Provider
Follow the established pattern in wsmaster/:
- Create `che-core-api-auth-<provider>` with `<Provider>OAuthAuthenticator` and `<Provider>OAuthAuthenticatorProvider`
- Create `che-core-api-factory-<provider>` with the six required classes: `ApiClient`, `FactoryParametersResolver`, `PersonalAccessTokenFetcher`, `ScmFileResolver`, `URLParser`, `UserDataFetcher`
- Each module needs a Guice `Module` class to register bindings

### Working with Kubernetes Resources
The `infrastructures/kubernetes` module uses the Fabric8 Kubernetes Java client. Infrastructure provisioning is abstracted through factory interfaces in `infrastructure-factory`.

### Database Schema Changes
Schema definitions are in `wsmaster/che-core-sql-schema`. Liquibase is used for migrations.

## CI/CD

GitHub Actions workflows handle:
- PR builds: `.github/workflows/build-pr-check.yml`
- Main branch builds: `.github/workflows/next-build.yml` (pushes to quay.io)
- Releases: `.github/workflows/release.yml`
- Sonar analysis: `.github/workflows/sonar.yaml`

## Resources

- **Issues**: https://github.com/eclipse/che/issues
- **Documentation**: https://www.eclipse.org/che/docs/
- **Devfile**: `devfile.yaml` in project root defines development container with Maven, JDK 17, and Podman
