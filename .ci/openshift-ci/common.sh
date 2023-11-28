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

set -e
# only exit with zero if all commands of the pipeline exit successfully
set -o pipefail

PR_IMAGE_TAG="pr-${PULL_NUMBER}"

export CHE_NAMESPACE=${CHE_NAMESPACE:-"eclipse-che"}
export CHE_SERVER_IMAGE=${CHE_SERVER_IMAGE:-"quay.io/eclipse/che-server:${PR_IMAGE_TAG}"}
export ARTIFACTS_DIR=${ARTIFACT_DIR:-"/tmp/artifacts"}
export CHE_FORWARDED_PORT="8081"
export OCP_ADMIN_USER_NAME=${OCP_ADMIN_USER_NAME:-"admin"}
export OCP_NON_ADMIN_USER_NAME=${OCP_NON_ADMIN_USER_NAME:-"user"}
export OCP_LOGIN_PASSWORD=${OCP_LOGIN_PASSWORD:-"passw"}
export ADMIN_CHE_NAMESPACE=${OCP_ADMIN_USER_NAME}"-che"
export USER_CHE_NAMESPACE=${OCP_NON_ADMIN_USER_NAME}"-che"
export GIT_PROVIDER_USERNAME=${GIT_PROVIDER_USERNAME:-"chepullreq1"}
export PUBLIC_REPO_WORKSPACE_NAME=${PUBLIC_REPO_WORKSPACE_NAME:-"public-repo-wksp-testname"}
export PRIVATE_REPO_WORKSPACE_NAME=${PRIVATE_REPO_WORKSPACE_NAME:-"private-repo-wksp-testname"}
export PUBLIC_PROJECT_NAME=${PUBLIC_PROJECT_NAME:-"public-repo"}
export PRIVATE_PROJECT_NAME=${PRIVATE_PROJECT_NAME:-"private-repo"}
export YAML_FILE_NAME=${YAML_FILE_NAME:-"devfile.yaml"}

provisionOpenShiftOAuthUser() {
  echo -e "[INFO] Provisioning Openshift OAuth user"
  htpasswd -c -B -b users.htpasswd ${OCP_ADMIN_USER_NAME} ${OCP_LOGIN_PASSWORD}
  htpasswd -b users.htpasswd ${OCP_NON_ADMIN_USER_NAME} ${OCP_LOGIN_PASSWORD}
  oc create secret generic htpass-secret --from-file=htpasswd="users.htpasswd" -n openshift-config
  oc apply -f ".ci/openshift-ci/htpasswdProvider.yaml"
  oc adm policy add-cluster-role-to-user cluster-admin ${OCP_ADMIN_USER_NAME}

  echo -e "[INFO] Waiting for htpasswd auth to be working up to 5 minutes"
  CURRENT_TIME=$(date +%s)
  ENDTIME=$((CURRENT_TIME + 300))
  while [ "$(date +%s)" -lt $ENDTIME ]; do
      if oc login -u=${OCP_ADMIN_USER_NAME} -p=${OCP_LOGIN_PASSWORD} --insecure-skip-tls-verify=false; then
          break
      fi
      sleep 10
  done
}

createCustomResourcesFile() {
  cat > custom-resources.yaml <<-END
apiVersion: org.eclipse.che/v2
spec:
  devEnvironments:
  maxNumberOfRunningWorkspacesPerUser: 10000
END

  echo "Generated custom resources file"
  cat custom-resources.yaml
}

deployChe() {
  chectl server:deploy --cheimage=$CHE_SERVER_IMAGE \
                       --che-operator-cr-patch-yaml=custom-resources.yaml \
                       --platform=openshift \
                       --telemetry=off \
                       --batch
}

# this command starts port forwarding between the local machine and the che-host service in the OpenShift cluster.
forwardPortToService() {
  oc port-forward service/che-host ${CHE_FORWARDED_PORT}:8080 -n ${CHE_NAMESPACE} &
  sleep 3s
}

killProcessByPort() {
  fuser -k ${CHE_FORWARDED_PORT}/tcp
}

requestFactoryResolverGitRepoUrl() {
  GIT_REPO_URL=$1
  CLUSTER_ACCESS_TOKEN=$(oc whoami -t)

  curl -i -X 'POST' \
    http://localhost:${CHE_FORWARDED_PORT}/api/factory/resolver \
    -H 'accept: */*' \
    -H "Authorization: Bearer ${CLUSTER_ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d '{
    "url": "'${GIT_REPO_URL}'"
  }'
}

# check that factory resolver returns correct value without any PAT/OAuth setup
testFactoryResolverNoPatOAuth() {
  echo "[INFO] Check factory resolver for public repository with NO PAT/OAuth setup"
  testFactoryResolverResponse $1 200

  echo "[INFO] Check factory resolver for private repository with NO PAT/OAuth setup"
  testFactoryResolverResponse $2 500
}

# check that raw devfile url factory resolver returns correct value without any PAT/OAuth setup
testFactoryResolverNoPatOAuthRaw() {
  echo "[INFO] Check factory resolver for public repository with NO PAT/OAuth setup"
  testFactoryResolverResponse $1 200

  echo "[INFO] Check factory resolver for private repository with NO PAT/OAuth setup"
  testFactoryResolverResponse $2 400
}

# check that factory resolver returns correct value with PAT/OAuth setup
testFactoryResolverWithPatOAuth() {
  echo "[INFO] Check factory resolver for public repository with PAT/OAuth setup"
  testFactoryResolverResponse $1 200

  echo "[INFO] Check factory resolver for private repository with PAT/OAuth setup"
  testFactoryResolverResponse $2 200
}

testFactoryResolverResponse() {
  URL=$1
  RESPONSE_CODE=$2

  echo "[INFO] Check factory resolver"
  if [ "$(requestFactoryResolverGitRepoUrl ${URL} | grep "HTTP/1.1 ${RESPONSE_CODE}")" ]; then
    echo "[INFO] Factory resolver returned 'HTTP/1.1 ${RESPONSE_CODE}' status code. Expected client side response."
  else
    echo "[ERROR] Factory resolver returned wrong status code. Expected: HTTP/1.1 ${RESPONSE_CODE}."
    exit 1
  fi
}

requestProvisionNamespace() {
  CLUSTER_ACCESS_TOKEN=$(oc whoami -t)

  curl -i -X 'POST' \
    http://localhost:${CHE_FORWARDED_PORT}/api/kubernetes/namespace/provision \
    -H 'accept: application/json' \
    -H "Authorization: Bearer ${CLUSTER_ACCESS_TOKEN}" \
    -d ''
}

initUserNamespace() {
  OCP_USER_NAME=$1

  echo "[INFO] Initialize user namespace"
  oc login -u=${OCP_USER_NAME} -p=${OCP_LOGIN_PASSWORD} --insecure-skip-tls-verify=false
  if [ "$(requestProvisionNamespace | grep "HTTP/1.1 200")" ]; then
    echo "[INFO] Request provision user namespace returned 'HTTP/1.1 200' status code."
  else
    echo "[ERROR] Request provision user namespace returned wrong status code. Expected: HTTP/1.1 200"
    exit 1
  fi
}

setupPersonalAccessToken() {
  GIT_PROVIDER_TYPE=$1
  GIT_PROVIDER_URL=$2
  GIT_PROVIDER_PAT=$3

  echo "[INFO] Setup Personal Access Token Secret"
  oc project ${USER_CHE_NAMESPACE}
  CHE_USER_ID=$(oc get secret user-profile -o jsonpath='{.data.id}' | base64 -d)
  ENCODED_PAT=$(echo -n ${GIT_PROVIDER_PAT} | base64)
  cat .ci/openshift-ci/pat-secret.yaml > pat-secret.yaml

  # patch the pat-secret.yaml file
  sed -i "s#che-user-id#${CHE_USER_ID}#g" pat-secret.yaml
  sed -i "s#git-provider-name#${GIT_PROVIDER_TYPE}#g" pat-secret.yaml
  sed -i "s#git-provider-url#${GIT_PROVIDER_URL}#g" pat-secret.yaml
  sed -i "s#encoded-access-token#${ENCODED_PAT}#g" pat-secret.yaml

  if [ "${GIT_PROVIDER_TYPE}" == "azure-devops" ]; then
    sed -i "s#''#${GIT_PROVIDER_USERNAME}#g" pat-secret.yaml
  fi

  cat pat-secret.yaml

  oc apply -f pat-secret.yaml -n ${USER_CHE_NAMESPACE}
}

setupSSHKeyPairs() {
  GIT_PRIVATE_KEY=$1
  GIT_PUBLIC_KEY=$2

  echo "[INFO] Setup SSH Key Pairs Secret"
  oc project ${USER_CHE_NAMESPACE}
  ENCODED_GIT_PRIVATE_KEY=$(echo "${GIT_PRIVATE_KEY}" | base64 -w 0)
  ENCODED_GIT_PUBLIC_KEY=$(echo "${GIT_PUBLIC_KEY}" | base64 -w 0)
  cat .ci/openshift-ci/ssh-secret.yaml > ssh-secret.yaml

  # patch the ssh-secret.yaml file
  sed -i "s#ssh_private_key#${ENCODED_GIT_PRIVATE_KEY}#g" ssh-secret.yaml
  sed -i "s#ssh_public_key#${ENCODED_GIT_PUBLIC_KEY}#g" ssh-secret.yaml

  cat ssh-secret.yaml

  oc apply -f ssh-secret.yaml -n ${USER_CHE_NAMESPACE}
}

runTestWorkspaceWithGitRepoUrl() {
  WS_NAME=$1
  PROJECT_NAME=$2
  GIT_REPO_URL=$3
  OCP_USER_NAMESPACE=$4

  oc project ${OCP_USER_NAMESPACE}
  cat .ci/openshift-ci/devworkspace-test.yaml > devworkspace-test.yaml

  # patch the devworkspace-test.yaml file
  sed -i "s#ws-name#${WS_NAME}#g" devworkspace-test.yaml
  sed -i "s#project-name#${PROJECT_NAME}#g" devworkspace-test.yaml
  sed -i "s#git-repo-url#${GIT_REPO_URL}#g" devworkspace-test.yaml

  cat devworkspace-test.yaml

  oc apply -f devworkspace-test.yaml -n ${OCP_USER_NAMESPACE}
  oc wait -n ${OCP_USER_NAMESPACE} --for=condition=Ready dw ${WS_NAME} --timeout=360s
  echo "[INFO] Test workspace is run"
}

testProjectIsCloned() {
  PROJECT_NAME=$1
  OCP_USER_NAMESPACE=$2

  WORKSPACE_POD_NAME=$(oc get pods -n ${OCP_USER_NAMESPACE} | grep workspace | awk '{print $1}')
  if oc exec -it -n ${OCP_USER_NAMESPACE} ${WORKSPACE_POD_NAME} -- test -f /projects/${PROJECT_NAME}/${YAML_FILE_NAME}; then
    echo "[INFO] Project file /projects/${PROJECT_NAME}/${YAML_FILE_NAME} exists."
  else
    echo "[INFO] Project file /projects/${PROJECT_NAME}/${YAML_FILE_NAME} is absent."
    return 1
  fi
}

testGitCredentialsData() {
  OCP_USER_NAMESPACE=$1
  GIT_PROVIDER_PAT=$2
  GIT_PROVIDER_URL=$3

  echo "[INFO] Check the 'git credentials' is in a workspace"
  hostName="${GIT_PROVIDER_URL#https://}"

  if [ "${GIT_PROVIDER_TYPE}" == "azure-devops" ]; then
    userName="username"
  else
    userName=${GIT_PROVIDER_USERNAME}
  fi

  gitCredentials="https://${userName}:${GIT_PROVIDER_PAT}@${hostName}"
  WORKSPACE_POD_NAME=$(oc get pods -n ${OCP_USER_NAMESPACE} | grep workspace | awk '{print $1}')
  if oc exec -it -n ${OCP_USER_NAMESPACE} ${WORKSPACE_POD_NAME} -- cat /.git-credentials/credentials | grep -q ${gitCredentials}; then
    echo "[INFO] Git credentials file '/.git-credentials/credentials' exists and has the expected content."
  else
    echo "[ERROR] Git credentials file '/.git-credentials/credentials' does not exist or has incorrect content."
    exit 1
  fi
}

deleteTestWorkspace() {
  WS_NAME=$1
  OCP_USER_NAMESPACE=$2

  oc delete dw ${WS_NAME} -n ${OCP_USER_NAMESPACE}
}

# Catch the finish of the job and write logs in artifacts.
catchFinish() {
  local RESULT=$?
  killProcessByPort
  if [ "$RESULT" != "0" ]; then
    set +e
    collectEclipseCheLogs
    set -e
  fi

  [[ "${RESULT}" != "0" ]] && echo "[ERROR] Job failed." || echo "[INFO] Job completed successfully."
  exit $RESULT
}

collectEclipseCheLogs() {
  mkdir -p ${ARTIFACTS_DIR}/che-logs

  # Collect all Eclipse Che logs and cluster CR
  chectl server:logs -n $CHE_NAMESPACE --directory ${ARTIFACTS_DIR}/che-logs --telemetry off
  oc get checluster -o yaml -n $CHE_NAMESPACE > "${ARTIFACTS_DIR}/che-cluster.yaml"
}

testCloneGitRepoNoProjectExists() {
    WS_NAME=$1
    PROJECT_NAME=$2
    GIT_REPO_URL=$3
    OCP_USER_NAMESPACE=$4

    runTestWorkspaceWithGitRepoUrl ${WS_NAME} ${PROJECT_NAME} ${GIT_REPO_URL} ${OCP_USER_NAMESPACE}
    echo "[INFO] Check the private repository is NOT cloned with NO PAT/OAuth setup"
    testProjectIsCloned ${PROJECT_NAME} ${OCP_USER_NAMESPACE} && \
    { echo "[ERROR] Project file /projects/${PROJECT_NAME}/${YAML_FILE_NAME} should NOT be present" && exit 1; }
    echo "[INFO] Project file /projects/${PROJECT_NAME}/${YAML_FILE_NAME} is NOT present. This is EXPECTED"
}

# Test that the repository is cloned when PAT, OAuth or SSH is configured
testCloneGitRepoProjectShouldExists() {
  WS_NAME=$1
  PROJECT_NAME=$2
  GIT_REPO_URL=$3
  OCP_USER_NAMESPACE=$4

  runTestWorkspaceWithGitRepoUrl ${WS_NAME} ${PROJECT_NAME} ${GIT_REPO_URL} ${OCP_USER_NAMESPACE}
  echo "[INFO] Check the repository is cloned"
  testProjectIsCloned ${PROJECT_NAME} ${OCP_USER_NAMESPACE} || \
  { echo "[ERROR] Project file /projects/${PROJECT_NAME}/${YAML_FILE_NAME} should be present." && exit 1; }
}

setupTestEnvironment() {
  OCP_USER_NAME=$1

  provisionOpenShiftOAuthUser
  createCustomResourcesFile
  deployChe
  forwardPortToService
  initUserNamespace ${OCP_USER_NAME}
}
