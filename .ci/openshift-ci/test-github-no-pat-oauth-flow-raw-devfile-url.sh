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

export PUBLIC_REPO_RAW_PATH_URL=${PUBLIC_REPO_RAW_PATH_URL:-"https://raw.githubusercontent.com/chepullreq1/public-repo/main/devfile.yaml"}
export PRIVATE_REPO_RAW_PATH_URL=${PRIVATE_REPO_URL:-"https://raw.githubusercontent.com/chepullreq1/private-repo/main/devfile.yaml"}

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "catchFinish" EXIT SIGINT

setupTestEnvironment ${OCP_NON_ADMIN_USER_NAME}
testFactoryResolverNoPatOAuth ${PUBLIC_REPO_RAW_PATH_URL} ${PRIVATE_REPO_RAW_PATH_URL}

testClonePublicRepoNoPatOAuth ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
testClonePrivateRepoNoPatOAuth ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
