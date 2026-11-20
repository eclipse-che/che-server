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
set -e
# only exit with zero if all commands of the pipeline exit successfully
set -o pipefail

echo "======= [INFO] OpenShift CI infrastructure is ready. Running combined Azure tests. ======="

# import common test functions
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${SCRIPT_DIR}"/common.sh

trap "catchFinish" EXIT SIGINT

# Credential files diagnostic (names and sizes only, no values)
{
  echo "Credential files diagnostic:"
  for f in /usr/local/ci-secrets/*/*; do
    [ -f "$f" ] && echo "  $(basename $(dirname $f))/$(basename $f): $(wc -c < "$f") bytes"
  done
} > ${ARTIFACTS_DIR}/credential-diagnostic.txt 2>&1 || true

# Azure SSH key fingerprints: a SHA256 fingerprint is not the secret value,
# so it is safe to log — it survives secret censoring and identifies the key
# unambiguously (compare with the fingerprint of the key registered in the
# Azure account / stored in the GSM collection). Key material is never
# printed. Printed to stdout and to the artifacts file.
{
  echo "Azure SSH key fingerprints:"
  if [ -n "${AZURE_PUBLIC_KEY:-}" ]; then
    PUB_KEY_FILE=$(mktemp)
    printf '%s\n' "${AZURE_PUBLIC_KEY}" > "${PUB_KEY_FILE}"
    echo "  public:  $(ssh-keygen -lf "${PUB_KEY_FILE}" 2>/dev/null || echo 'failed to compute')"
    rm -f "${PUB_KEY_FILE}"
  else
    echo "  public:  AZURE_PUBLIC_KEY is not set"
  fi
  if [ -n "${AZURE_PRIVATE_KEY:-}" ]; then
    PRIV_KEY_FILE=$(mktemp)
    chmod 600 "${PRIV_KEY_FILE}"
    printf '%s\n' "${AZURE_PRIVATE_KEY}" > "${PRIV_KEY_FILE}"
    echo "  private: $(ssh-keygen -yf "${PRIV_KEY_FILE}" 2>/dev/null | ssh-keygen -lf - 2>/dev/null || echo 'failed to compute')"
    rm -f "${PRIV_KEY_FILE}"
  else
    echo "  private: AZURE_PRIVATE_KEY is not set"
  fi
} | tee -a ${ARTIFACTS_DIR}/credential-diagnostic.txt

setupTestEnvironment ${OCP_NON_ADMIN_USER_NAME}

# ======= [TEST 1/4] azure-no-pat-oauth-flow =======
echo ""
echo "========================================================================"
echo "======= [TEST 1/4] azure-no-pat-oauth-flow ======="
echo "========================================================================"
export PUBLIC_REPO_URL=${PUBLIC_REPO_URL:-"https://chepullreq1@dev.azure.com/chepullreq1/che-pr-public/_git/public-repo"}
export PRIVATE_REPO_URL=${PRIVATE_REPO_URL:-"https://dev.azure.com/chepullreq1/che-pr-private/_git/private-repo"}

testFactoryResolverNoPatOAuth ${PUBLIC_REPO_URL} ${PRIVATE_REPO_URL}
testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}
testCloneGitRepoNoProjectExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

# ======= [TEST 2/4] azure-no-pat-oauth-flow-raw-devfile-url =======
echo ""
echo "========================================================================"
echo "======= [TEST 2/4] azure-no-pat-oauth-flow-raw-devfile-url ======="
echo "========================================================================"
export PUBLIC_REPO_RAW_PATH_URL=${PUBLIC_REPO_RAW_PATH_URL:-"https://dev.azure.com/chepullreq1/che-pr-public/_apis/git/repositories/public-repo/items?path=/devfile.yaml"}
export PRIVATE_REPO_RAW_PATH_URL="https://dev.azure.com/chepullreq1/che-pr-private/_apis/git/repositories/private-repo/items?path=/devfile.yaml"

testFactoryResolverResponse ${PUBLIC_REPO_RAW_PATH_URL} 200
testFactoryResolverResponse ${PRIVATE_REPO_RAW_PATH_URL} 400

testCloneGitRepoNoProjectExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

testCloneGitRepoNoProjectExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_RAW_PATH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

# ======= [TEST 3/4] azure-no-pat-oauth-flow-ssh-url =======
echo ""
echo "========================================================================"
echo "======= [TEST 3/4] azure-no-pat-oauth-flow-ssh-url ======="
echo "========================================================================"
export PUBLIC_REPO_SSH_URL=${PUBLIC_REPO_SSH_URL:-"git@ssh.dev.azure.com:v3/chepullreq1/che-pr-public/public-repo"}
export PRIVATE_REPO_SSH_URL=${PRIVATE_REPO_SSH_URL:-"git@ssh.dev.azure.com:v3/chepullreq1/che-pr-private/private-repo"}

setupSSHKeyPairs "${AZURE_PRIVATE_KEY}" "${AZURE_PUBLIC_KEY}"
testFactoryResolverResponse ${PUBLIC_REPO_SSH_URL} 200
testFactoryResolverResponse ${PRIVATE_REPO_SSH_URL} 500

testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_SSH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

testCloneGitRepoProjectShouldExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_SSH_URL} ${USER_CHE_NAMESPACE}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

echo "------- [INFO] Cleaning up SSH secret before PAT test -------"
oc delete secret git-ssh-key -n ${USER_CHE_NAMESPACE} 2>/dev/null || true

# ======= [TEST 4/4] azure-with-pat-setup-flow =======
echo ""
echo "========================================================================"
echo "======= [TEST 4/4] azure-with-pat-setup-flow ======="
echo "========================================================================"
export PUBLIC_REPO_URL=${PUBLIC_REPO_URL:-"https://chepullreq1@dev.azure.com/chepullreq1/che-pr-public/_git/public-repo"}
export PRIVATE_REPO_URL=${PRIVATE_REPO_URL:-"https://dev.azure.com/chepullreq1/che-pr-private/_git/private-repo"}
export GIT_PROVIDER_TYPE=${GIT_PROVIDER_TYPE:-"azure-devops"}
export GIT_PROVIDER_URL=${GIT_PROVIDER_URL:-"https://dev.azure.com"}
setupPersonalAccessToken  ${GIT_PROVIDER_TYPE} ${GIT_PROVIDER_URL} ${AZURE_PAT}
requestProvisionNamespace
testFactoryResolverWithPatOAuth ${PUBLIC_REPO_URL} ${PRIVATE_REPO_URL}

echo "------- [INFO] Check clone public repository with PAT setup -------"
testCloneGitRepoProjectShouldExists ${PUBLIC_REPO_WORKSPACE_NAME} ${PUBLIC_PROJECT_NAME} ${PUBLIC_REPO_URL} ${USER_CHE_NAMESPACE}
testGitCredentialsData ${USER_CHE_NAMESPACE} ${AZURE_PAT} ${GIT_PROVIDER_URL}
deleteTestWorkspace ${PUBLIC_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

echo "------- [INFO] Check clone private repository with PAT setup -------"
testCloneGitRepoProjectShouldExists ${PRIVATE_REPO_WORKSPACE_NAME} ${PRIVATE_PROJECT_NAME} ${PRIVATE_REPO_URL} ${USER_CHE_NAMESPACE}
testGitCredentialsData ${USER_CHE_NAMESPACE} ${AZURE_PAT} ${GIT_PROVIDER_URL}
deleteTestWorkspace ${PRIVATE_REPO_WORKSPACE_NAME} ${USER_CHE_NAMESPACE}

