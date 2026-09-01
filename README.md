# Eclipse Che Server

Che Server is the backend component of Eclipse Che - a Kubernetes-native IDE platform that provides REST APIs for managing Kubernetes namespaces and retrieving devfile content from various Git hosting services.

## Overview

Eclipse Che Server is a Java web application deployed on Apache Tomcat that enables:
- **Kubernetes Namespace Management**: Provision and manage workspaces across Kubernetes/OpenShift clusters
- **Multi-SCM Support**: Integrate with GitHub, GitLab, Bitbucket, and Azure DevOps repositories
- **OAuth Authentication**: Secure OAuth1/OAuth2 flows for all supported Git providers
- **Devfile Resolution**: Fetch and parse devfile content from various repository sources
- **Factory Flow**: Convert repository URLs into ready-to-use development workspaces

### Tech Stack
- **Java 17** with Jakarta EE
- **Apache Maven** for build automation
- **Google Guice** for dependency injection
- **Fabric8 Kubernetes Client** for cluster orchestration
- **Apache Tomcat** as the servlet container
- **Micrometer/Prometheus** for metrics
- **OpenTracing/Jaeger** for distributed tracing

## Table of Contents

- [Installation & Setup](#installation--setup)
- [Building the Project](#building-the-project)
- [Project Structure](#project-structure)
- [Usage & API Examples](#usage--api-examples)
- [Contributing](#contributing)
- [CI/CD](#cicd)
- [Resources](#resources)
- [License](#license)

## Installation & Setup

### Prerequisites

- **Java 17**
- **Apache Maven 3.6+**
- **Podman** or **Docker** (for container builds and integration tests)
- **Kubernetes** or **OpenShift** cluster (for deployment)

### Quick Start

1. **Clone the repository**
   ```bash
   git clone https://github.com/eclipse-che/che-server.git
   cd che-server
   ```

2. **Build the project**
   ```bash
   mvn clean install
   ```

3. **Build container image**
   ```bash
   ./build/build.sh --organization:quay.io/<your quay account>
   ```
   This creates the image: `quay.io/<your quay account>/che-server:next`

4. **Deploy to Kubernetes/OpenShift**
   ```bash
   # Install chectl (Che CLI) if not already installed
   # See: https://github.com/che-incubator/chectl
   
   chectl server:deploy --platform=minikube --cheimage=quay.io/<your quay account>/che-server:next
   ```

## Building the Project

### Standard Build (with tests)
```bash
mvn clean install
```

### Fast Build (skip tests and validation)
```bash
mvn clean install -V -e -Pfast -DskipTests -Dskip-validate-sources -Denforcer.skip=true
```

### Build Specific Module
```bash
cd wsmaster/che-core-api-factory-github
mvn clean install
```

### Build Container Image
```bash
./build/build.sh
```

## Project Structure

Che Server is organized into five main Maven modules:

### 1. **core/** - Foundation Layer
Core APIs, DTOs, REST framework, WebSocket support, and common utilities.
- `che-core-api-core`: REST framework and core APIs
- `che-core-api-dto`: DTO serialization framework
- `che-core-api-model`: Shared data models
- `commons/`: JSON, logging, dependency injection utilities
- `che-core-metrics-core`: Micrometer/Prometheus metrics
- `che-core-tracing-*`: OpenTracing/Jaeger distributed tracing

### 2. **wsmaster/** - Business Logic Layer
REST API implementations organized by feature domain.

#### OAuth/Authentication Modules
- `che-core-api-auth`: Base OAuth framework
- `che-core-api-auth-github`: GitHub OAuth implementation
- `che-core-api-auth-gitlab`: GitLab OAuth implementation
- `che-core-api-auth-bitbucket`: Bitbucket OAuth implementation
- `che-core-api-auth-azure-devops`: Azure DevOps OAuth implementation

#### Factory Modules
- `che-core-api-factory-shared`: Common factory interfaces
- `che-core-api-factory-github`: GitHub factory resolver
- `che-core-api-factory-gitlab`: GitLab factory resolver
- `che-core-api-factory-bitbucket`: Bitbucket Cloud factory resolver
- `che-core-api-factory-bitbucket-server`: Bitbucket Server factory resolver
- `che-core-api-factory-azure-devops`: Azure DevOps factory resolver

#### Other wsmaster Modules
- `che-core-api-devfile`: Devfile parsing and validation
- `che-core-api-workspace`: Workspace lifecycle management
- `che-core-api-user`: User management APIs
- `che-core-api-ssh`: SSH key management
- `che-core-sql-schema`: Database schema definitions

### 3. **infrastructures/** - Kubernetes Orchestration
- `kubernetes/`: Base Kubernetes infrastructure (Fabric8 client)
- `openshift/`: OpenShift-specific extensions
- `infrastructure-factory`: Infrastructure abstraction layer
- `infrastructure-metrics`: Infrastructure-specific metrics

### 4. **multiuser/** - Multi-tenancy
Permission and authentication modules for multi-user deployments.

### 5. **assembly/** - Packaging
Assembles all modules into deployable WAR files.

## Usage & API Examples

### REST API Endpoints

Once deployed, Che Server exposes REST APIs for workspace and factory management:

#### Get User Information
```bash
curl -X GET http://<che-host>/api/user
```

#### Resolve Factory from Repository URL
```bash
curl -X POST http://<che-host>/api/factory/resolver \
  -H "Content-Type: application/json" \
  -d '{"url": "https://github.com/eclipse-che/che-server"}'
```

#### List Kubernetes Namespaces
```bash
curl -X GET http://<che-host>/api/kubernetes/namespace
```

### OAuth Flow

Che Server handles OAuth authentication for SCM providers:

1. User initiates OAuth: `GET /api/oauth/authenticate?oauth_provider=github&redirect_after_login=<url>`
2. User authorizes on provider's site
3. Callback: `GET /api/oauth/callback?code=<auth_code>&state=<state>`
4. Token stored for API operations

## Contributing

We welcome contributions to Eclipse Che Server! Whether you're fixing bugs, adding features, or improving documentation, your help is appreciated.

**To get started:**
- Read our [CONTRIBUTING.md](CONTRIBUTING.md) guide for detailed instructions on:
  - Setting up your development environment
  - Building and testing the project
  - Debugging techniques
  - Code style and conventions
  - How to add support for new SCM providers
  - Submitting pull requests

**Quick Links:**
- **Report Issues**: https://github.com/eclipse/che/issues
- **Community**: See the [Eclipse Che Documentation](https://www.eclipse.org/che/docs/stable/overview/introduction-to-eclipse-che/#_joining_the_community) for chat, mailing lists, and community meetings

[![Contribute](https://www.eclipse.org/che/contribute.svg)](https://workspaces.openshift.com#https://github.com/eclipse-che/che-server)

## CI/CD

GitHub Actions workflows handle automated builds and releases:

- **PR Builds**: Validates pull requests (`.github/workflows/build-pr-check.yml`)
- **Main Branch Builds**: Builds and pushes to quay.io (`.github/workflows/next-build.yml`)
- **Releases**: Handles version releases (`.github/workflows/release.yml`)
- **Sonar Analysis**: Code quality checks (`.github/workflows/sonar.yaml`)

[![release latest stable](https://github.com/eclipse-che/che-server/actions/workflows/release.yml/badge.svg)](https://github.com/eclipse-che/che-server/actions/workflows/release.yml)

For detailed information about CI/CD workflows, see [CONTRIBUTING.md](CONTRIBUTING.md#cicd).

## Resources

- **Documentation**: https://www.eclipse.org/che/docs/
- **Issues & Bug Reports**: https://github.com/eclipse/che/issues
- **Eclipse Che Main Repository**: https://github.com/eclipse/che
- **Container Images**: https://quay.io/repository/eclipse/che-server

## SBOM

To enhance supply chain security and offer users clear insight into project components, Eclipse Che generates a Software Bill of Materials (SBOM) for every release. These are published to the Eclipse Foundation SBOM registry, with access instructions and usage details available in this [documentation](https://eclipse-csi.github.io/security-handbook/sbom/registry.html).

## License

[Eclipse Public License 2.0](LICENSE)

---

**Eclipse Che** - Cloud-native, in-browser IDE for rapid cloud application development.
