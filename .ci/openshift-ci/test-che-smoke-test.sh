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

echo "======= [INFO] OpenShift CI infrastructure is ready. Running test. ======="

export TEST_POD_NAME=${TEST_POD_NAME:-"che-smoke-test"}

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "collectLogs" EXIT SIGINT

provisionOpenShiftOAuthUser
waitForPRImage
createCustomResourcesFile
deployChe

# First attempt
startSmokeTest

echo "------- [INFO] Waiting for smoke test (attempt 1/2) to complete. -------"
set +e
oc logs -n ${CHE_NAMESPACE} ${TEST_POD_NAME} -c test -f 2>/dev/null
sleep 3
FIRST_EXIT=$(oc logs -n ${CHE_NAMESPACE} ${TEST_POD_NAME} -c test 2>/dev/null | grep "EXIT_CODE" || true)
set -e

if [[ "${FIRST_EXIT}" == "+ EXIT_CODE=0" ]]; then
  export SKIP_LOG_FOLLOW=true
  echo "======= [INFO] Smoke test passed on first attempt. ======="
else
  echo ""
  echo "========================================================================"
  echo "======= [WARNING] Smoke test FAILED on first attempt. Retrying (attempt 2/2)... ======="
  echo "========================================================================"

  set +e
  mkdir -p ${ARTIFACTS_DIR}/e2e-attempt-1
  oc rsync -n ${CHE_NAMESPACE} ${TEST_POD_NAME}:/tmp/e2e/report/ ${ARTIFACTS_DIR}/e2e-attempt-1 -c download-reports 2>/dev/null
  oc exec -n ${CHE_NAMESPACE} ${TEST_POD_NAME} -c download-reports -- touch /tmp/done 2>/dev/null
  sleep 5
  oc delete pod ${TEST_POD_NAME} -n ${CHE_NAMESPACE} --wait=true 2>/dev/null
  set -e

  startSmokeTest
fi
