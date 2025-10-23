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

export TEST_POD_NAME=${TEST_POD_NAME:-"oauth-factory-test"}
export GIT_PROVIDER_TYPE=${GIT_PROVIDER_TYPE:-"gitlab"}
export GIT_PROVIDER_URL=${GIT_PROVIDER_URL:-"https://gitlab-gitlab-system.apps.git.crw-qe.com"}
export GIT_PROVIDER_LOGIN=${GIT_PROVIDER_LOGIN:-"admin-user"}
export PRIVATE_REPO_URL=${PRIVATE_REPO_URL:-"https://gitlab-gitlab-system.apps.git.crw-qe.com/admin-user/private-repo.git"}
export GIT_REPO_BRANCH=${PRIVATE_REPO_BRANCH:-"main"}
export APPLICATION_NAME=${APPLICATION_NAME:-"TestApp"}
export OAUTH_ID=""
export APPLICATION_ID=""
export APPLICATION_SECRET=""

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "collectLogs" EXIT SIGINT

setupTestEnvironmentOAuthFlow ${ADMIN_ACCESS_TOKEN} ${APPLICATION_NAME} ${APPLICATION_ID} ${APPLICATION_SECRET}
startOAuthFactoryTest
