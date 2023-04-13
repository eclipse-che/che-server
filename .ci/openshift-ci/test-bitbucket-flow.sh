#!/bin/bash
#
# Copyright (c) 2019-2021 Red Hat, Inc.
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

export PUBLIC_REPO_URL=${PUBLIC_REPO_URL:-"https://chepullreq1@bitbucket.org/chepullreq/public-repo.git"}
export PRIVATE_REPO_URL=${PRIVATE_REPO_URL:-"https://chepullreq1@bitbucket.org/chepullreq/private-repo.git"}

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "catchFinish" EXIT SIGINT

setupTestEnvironment
testFactoryResolverNoPatOAuth ${PUBLIC_REPO_URL} ${PRIVATE_REPO_URL}
testClonePublicRepoNoPatOAuth ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_GITLAB_NAME} ${PUBLIC_REPO_URL}
testClonePrivateRepoNoPatOAuth ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_GITLAB_NAME} ${PRIVATE_REPO_URL}
