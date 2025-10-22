#!/bin/bash
#
# Copyright (c) 2023 Red Hat, Inc.
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

echo "======= [INFO] OpenShift CI infrastructure is ready. Running test. ======="

export PUBLIC_REPO_URL=${PUBLIC_REPO_URL:-"https://gitlab.com/chepullreq1/public-repo.git"}
export PRIVATE_REPO_URL=${PRIVATE_REPO_URL:-"https://gitlab.com/chepullreq1/private-repo.git"}

export PUBLIC_REPO_WITH_DOT_DEFILE_URL=${PUBLIC_REPO_WITH_DOT_DEFILE_URL:-"https://gitlab.com/chepullreq1/public-repo-dot-devfile.git"}
export PRIVATE_REPO_WITH_DOT_DEFILE_URL=${PRIVATE_REPO_WITH_DOT_DEFILE_URL:-"https://gitlab.com/chepullreq1/private-repo-dot-devfile.git"}

export NAME_OF_PUBLIC_REPO_WITH_DOT_DEFILE=${NAME_OF_PUBLIC_REPO_WITH_DOT_DEFILE:-"public-repo-dot-devfile"}
export NAME_OF_PRIVATE_REPO_WITH_DOT_DEFILE=${NAME_OF_PRIVATE_REPO_WITH_DOT_DEFILE:-"private-repo-dot-devfile"}

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "catchFinish" EXIT SIGINT

setupTestEnvironment ${OCP_NON_ADMIN_USER_NAME}
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
