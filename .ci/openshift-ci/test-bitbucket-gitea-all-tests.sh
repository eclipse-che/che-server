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

echo "======= [INFO] OpenShift CI infrastructure is ready. Running combined Bitbucket + Gitea tests. ======="

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "catchFinish" EXIT SIGINT

setupTestEnvironment ${OCP_NON_ADMIN_USER_NAME}

# ======= [TEST 1/5] bitbucket-no-pat-oauth-flow =======
echo ""
echo "========================================================================"
echo "======= [TEST 1/5] bitbucket-no-pat-oauth-flow ======="
echo "========================================================================"
export PUBLIC_REPO_URL=${PUBLIC_REPO_URL:-"https://chepullreq1@bitbucket.org/chepullreq/public-repo.git"}
export PRIVATE_REPO_URL=${PRIVATE_REPO_URL:-"https://chepullreq1@bitbucket.org/chepullreq/private-repo.git"}

testFactoryResolverNoPatOAuth ${PUBLIC_REPO_URL} ${PRIVATE_REPO_URL}
testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}
testCloneGitRepoNoProjectExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

# ======= [TEST 2/5] bitbucket-no-pat-oauth-flow-raw-devfile-url =======
echo ""
echo "========================================================================"
echo "======= [TEST 2/5] bitbucket-no-pat-oauth-flow-raw-devfile-url ======="
echo "========================================================================"
export PUBLIC_REPO_RAW_PATH_URL=${PUBLIC_REPO_RAW_PATH_URL:-"https://bitbucket.org/chepullreq/public-repo/raw/746000bd63a54eaf8ea8aba9dfe6620e5c6c61d7/devfile.yaml"}
export PRIVATE_REPO_RAW_PATH_URL="https://bitbucket.org/chepullreq/private-repo/raw/80b183d27c6c533462128b0c092208aad73b2906/devfile.yaml"

testFactoryResolverNoPatOAuthRaw ${PUBLIC_REPO_RAW_PATH_URL} ${PRIVATE_REPO_RAW_PATH_URL}

testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

testCloneGitRepoNoProjectExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

# ======= [TEST 3/5] bitbucket-no-pat-oauth-flow-ssh-url =======
echo ""
echo "========================================================================"
echo "======= [TEST 3/5] bitbucket-no-pat-oauth-flow-ssh-url ======="
echo "========================================================================"
export PUBLIC_REPO_SSH_URL=${PUBLIC_REPO_SSH_URL:-"git@bitbucket.org:chepullreq/public-repo.git"}
export PRIVATE_REPO_SSH_URL=${PRIVATE_REPO_SSH_URL:-"git@bitbucket.org:chepullreq/private-repo.git"}

set +x
setupSSHKeyPairs "${BITBUCKET_PRIVATE_KEY}" "${BITBUCKET_PUBLIC_KEY}"
set -x
testFactoryResolverNoPatOAuth ${PUBLIC_REPO_SSH_URL} ${PRIVATE_REPO_SSH_URL}

testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_SSH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}
testCloneGitRepoProjectShouldExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_SSH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

# ======= [TEST 4/5] gitea-no-pat-oauth-flow =======
echo ""
echo "========================================================================"
echo "======= [TEST 4/5] gitea-no-pat-oauth-flow ======="
echo "========================================================================"
export PUBLIC_REPO_SSH_URL="git@gitea.com:chepullreq1/public-repo.git"
export PRIVATE_REPO_SSH_URL="git@gitea.com:chepullreq1/private-repo.git"
export PUBLIC_REPO_RAW_PATH_URL="https://gitea.com/chepullreq1/public-repo/raw/branch/main/devfile.yaml"

set +x
setupSSHKeyPairs "${GITEA_PRIVATE_KEY}" "${GITEA_PUBLIC_KEY}"
set -x

testFactoryResolverResponse ${PUBLIC_REPO_SSH_URL} 500
testFactoryResolverResponse ${PRIVATE_REPO_SSH_URL} 500
testFactoryResolverResponse ${PUBLIC_REPO_RAW_PATH_URL} 200

testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_SSH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

testCloneGitRepoNoProjectExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

testCloneGitRepoProjectShouldExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_SSH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

# ======= [TEST 5/5] gitea-with-pat-setup-flow =======
echo ""
echo "========================================================================"
echo "======= [TEST 5/5] gitea-with-pat-setup-flow ======="
echo "========================================================================"
set +x
export PRIVATE_REPO_RAW_PATH_URL="https://${GITEA_PAT}@gitea.com/chepullreq1/private-repo/raw/branch/main/devfile.yaml"

testFactoryResolverResponse ${PRIVATE_REPO_RAW_PATH_URL} 200

testCloneGitRepoNoProjectExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
set -x
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}
