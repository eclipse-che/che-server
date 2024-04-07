#!/bin/bash
#
# Copyright (c) 2024  Red Hat, Inc.
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

export TEST_POD_NAME=${TEST_POD_NAME:-"che-smoke-test"}

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "collectLogs" EXIT SIGINT

provisionOpenShiftOAuthUser
createCustomResourcesFile
deployChe
startSmokeTest
