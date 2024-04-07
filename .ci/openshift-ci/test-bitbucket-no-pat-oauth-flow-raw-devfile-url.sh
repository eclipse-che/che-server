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

echo -e "\e[1;3;4;32m[INFO] OpenShift CI infrastructure is ready.\nTest is run.\e[0m"

export PUBLIC_REPO_RAW_PATH_URL=${PUBLIC_REPO_RAW_PATH_URL:-"https://bitbucket.org/chepullreq/public-repo/raw/746000bd63a54eaf8ea8aba9dfe6620e5c6c61d7/devfile.yaml"}
export PRIVATE_REPO_RAW_PATH_URL=${PRIVATE_REPO_URL:-"https://bitbucket.org/chepullreq/private-repo/raw/80b183d27c6c533462128b0c092208aad73b2906/devfile.yaml"}

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "catchFinish" EXIT SIGINT

setupTestEnvironment ${OCP_NON_ADMIN_USER_NAME}
testFactoryResolverNoPatOAuthRaw ${PUBLIC_REPO_RAW_PATH_URL} ${PRIVATE_REPO_RAW_PATH_URL}

testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

testCloneGitRepoNoProjectExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}
