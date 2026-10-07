#!/bin/bash
#
# Copyright (c) 2023-2026 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#   Red Hat, Inc. - initial API and implementation
#

# exit immediately when a command fails
set -ex
# only exit with zero if all commands of the pipeline exit successfully
set -o pipefail

echo "======= [INFO] OpenShift CI infrastructure is ready. Running combined GitLab tests. ======="

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "catchFinish" EXIT SIGINT

setupTestEnvironment ${OCP_NON_ADMIN_USER_NAME}

# ======= [TEST 1/4] gitlab-no-pat-oauth-flow =======
echo ""
echo "========================================================================"
echo "======= [TEST 1/4] gitlab-no-pat-oauth-flow ======="
echo "========================================================================"
export PUBLIC_REPO_URL=${PUBLIC_REPO_URL:-"https://gitlab.com/chepullreq1/public-repo.git"}
export PRIVATE_REPO_URL=${PRIVATE_REPO_URL:-"https://gitlab.com/chepullreq1/private-repo.git"}

export PUBLIC_REPO_WITH_DOT_DEFILE_URL=${PUBLIC_REPO_WITH_DOT_DEFILE_URL:-"https://gitlab.com/chepullreq1/public-repo-dot-devfile.git"}
export PRIVATE_REPO_WITH_DOT_DEFILE_URL=${PRIVATE_REPO_WITH_DOT_DEFILE_URL:-"https://gitlab.com/chepullreq1/private-repo-dot-devfile.git"}

export NAME_OF_PUBLIC_REPO_WITH_DOT_DEFILE=${NAME_OF_PUBLIC_REPO_WITH_DOT_DEFILE:-"public-repo-dot-devfile"}
export NAME_OF_PRIVATE_REPO_WITH_DOT_DEFILE=${NAME_OF_PRIVATE_REPO_WITH_DOT_DEFILE:-"private-repo-dot-devfile"}

testFactoryResolverNoPatOAuth ${PUBLIC_REPO_URL} ${PRIVATE_REPO_URL}
testFactoryResolverNoPatOAuth ${PUBLIC_REPO_WITH_DOT_DEFILE_URL} ${PRIVATE_REPO_WITH_DOT_DEFILE_URL}

echo "------- [INFO] Check clone a public repository without PAT -------"
testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

echo "------- [INFO] Check clone a public repository with .devfile.yaml and without PAT -------"
testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${NAME_OF_PUBLIC_REPO_WITH_DOT_DEFILE} ${PUBLIC_REPO_WITH_DOT_DEFILE_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

echo "------- [INFO] Check clone a private repository without PAT is not available -------"
testCloneGitRepoNoProjectExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

echo "------- [INFO] Check clone a private repository with .devfile.yaml and without PAT is not available -------"
testCloneGitRepoNoProjectExists ${PRIVATE_REPO_WORKSPACE_NAME} ${NAME_OF_PRIVATE_REPO_WITH_DOT_DEFILE} ${PRIVATE_REPO_WITH_DOT_DEFILE_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

# ======= [TEST 2/4] gitlab-no-pat-oauth-flow-raw-devfile-url =======
echo ""
echo "========================================================================"
echo "======= [TEST 2/4] gitlab-no-pat-oauth-flow-raw-devfile-url ======="
echo "========================================================================"
export PUBLIC_REPO_RAW_PATH_URL=${PUBLIC_REPO_RAW_PATH_URL:-"https://gitlab.com/chepullreq1/public-repo/-/raw/main/devfile.yaml"}
export PRIVATE_REPO_RAW_PATH_URL="https://gitlab.com/chepullreq1/private-repo/-/raw/main/devfile.yaml"

testFactoryResolverNoPatOAuth ${PUBLIC_REPO_RAW_PATH_URL} ${PRIVATE_REPO_RAW_PATH_URL}

testCloneGitRepoNoProjectExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

testCloneGitRepoNoProjectExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

# ======= [TEST 3/4] gitlab-no-pat-oauth-flow-ssh-url =======
echo ""
echo "========================================================================"
echo "======= [TEST 3/4] gitlab-no-pat-oauth-flow-ssh-url ======="
echo "========================================================================"
export PUBLIC_REPO_SSH_URL=${PUBLIC_REPO_SSH_URL:-"git@gitlab.com:chepullreq1/public-repo.git"}
export PRIVATE_REPO_SSH_URL=${PRIVATE_REPO_SSH_URL:-"git@gitlab.com:chepullreq1/private-repo.git"}

set +x
setupSSHKeyPairs "${GITLAB_PRIVATE_KEY}" "${GITLAB_PUBLIC_KEY}"
set -x
testFactoryResolverNoPatOAuth ${PUBLIC_REPO_SSH_URL} ${PRIVATE_REPO_SSH_URL}

testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_SSH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

testCloneGitRepoProjectShouldExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_SSH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

# ======= [TEST 4/4] gitlab-with-pat-setup-flow =======
echo ""
echo "========================================================================"
echo "======= [TEST 4/4] gitlab-with-pat-setup-flow ======="
echo "========================================================================"
export PUBLIC_REPO_URL=${PUBLIC_REPO_URL:-"https://gitlab.com/chepullreq1/public-repo.git"}
export PRIVATE_REPO_URL=${PRIVATE_REPO_URL:-"https://gitlab.com/chepullreq1/private-repo.git"}
export GIT_PROVIDER_TYPE=${GIT_PROVIDER_TYPE:-"gitlab"}
export GIT_PROVIDER_URL=${GIT_PROVIDER_URL:-"https://gitlab.com"}
export PUBLIC_REPO_WITH_DOT_DEFILE_URL=${PUBLIC_REPO_WITH_DOT_DEFILE_URL:-"https://gitlab.com/chepullreq1/public-repo-dot-devfile.git"}
export PRIVATE_REPO_WITH_DOT_DEFILE_URL=${PRIVATE_REPO_WITH_DOT_DEFILE_URL:-"https://gitlab.com/chepullreq1/private-repo-dot-devfile.git"}

export NAME_OF_PUBLIC_REPO_WITH_DOT_DEFILE=${NAME_OF_PUBLIC_REPO_WITH_DOT_DEFILE:-"public-repo-dot-devfile"}
export NAME_OF_PRIVATE_REPO_WITH_DOT_DEFILE=${NAME_OF_PRIVATE_REPO_WITH_DOT_DEFILE:-"private-repo-dot-devfile"}

set +x
setupPersonalAccessToken  ${GIT_PROVIDER_TYPE} ${GIT_PROVIDER_URL} ${GITLAB_PAT}
set -x
requestProvisionNamespace
testFactoryResolverWithPatOAuth ${PUBLIC_REPO_URL} ${PRIVATE_REPO_URL}
testFactoryResolverWithPatOAuth ${PUBLIC_REPO_WITH_DOT_DEFILE_URL} ${PRIVATE_REPO_WITH_DOT_DEFILE_URL}

echo "------- [INFO] Check clone public repository with PAT setup -------"
testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_URL} ${USER_CHE_NAMESPACE}
set +x
testGitCredentialsData ${USER_CHE_NAMESPACE} ${GITLAB_PAT} ${GIT_PROVIDER_URL}
set -x
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

echo "------- [INFO] Check clone public repository that has .devfile.yaml and with PAT setup -------"
testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${NAME_OF_PUBLIC_REPO_WITH_DOT_DEFILE} ${PUBLIC_REPO_WITH_DOT_DEFILE_URL} ${USER_CHE_NAMESPACE}
set +x
testGitCredentialsData ${USER_CHE_NAMESPACE} ${GITLAB_PAT} ${GIT_PROVIDER_URL}
set -x
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

echo "------- [INFO] Check clone private repository with PAT setup -------"
testCloneGitRepoProjectShouldExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_URL} ${USER_CHE_NAMESPACE}
set +x
testGitCredentialsData ${USER_CHE_NAMESPACE} ${GITLAB_PAT} ${GIT_PROVIDER_URL}
set -x
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

echo "------- [INFO] Check clone private repository that has .devfile.yaml and with PAT setup -------"
testCloneGitRepoProjectShouldExists ${PRIVATE_REPO_WORKSPACE_NAME} ${NAME_OF_PRIVATE_REPO_WITH_DOT_DEFILE} ${PRIVATE_REPO_WITH_DOT_DEFILE_URL} ${USER_CHE_NAMESPACE}
set +x
testGitCredentialsData ${USER_CHE_NAMESPACE} ${GITLAB_PAT} ${GIT_PROVIDER_URL}
set -x
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

