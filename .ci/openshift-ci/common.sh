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

git remote add origin https://github.com/eclipse-che/che-server.git
git fetch
BRANCH_NAME=$(git rev-parse --abbrev-ref HEAD)
PR_IMAGE_TAG=$(git show origin/pr-number-${BRANCH_NAME}:shared-files/pr-image-tag)

export CHE_NAMESPACE=${CHE_NAMESPACE:-"eclipse-che"}
export CHE_SERVER_IMAGE=${CHE_SERVER_IMAGE:-"quay.io/eclipse/che-server:${PR_IMAGE_TAG}"}
export ARTIFACTS_DIR=${ARTIFACT_DIR:-"/tmp/artifacts"}
export CHE_FORWARDED_PORT="8081"
export OCP_ADMIN_USER_NAME=${OCP_ADMIN_USER_NAME:-"admin"}
export OCP_NON_ADMIN_USER_NAME=${OCP_NON_ADMIN_USER_NAME:-"user"}
export OCP_LOGIN_PASSWORD=${OCP_LOGIN_PASSWORD:-"passw"}
export ADMIN_PROJECT_NAME=${OCP_ADMIN_USER_NAME}"-che"
export USER_PROJECT_NAME=${OCP_NON_ADMIN_USER_NAME}"-che"
export PUBLIC_REPO_WORKSPACE_NAME=${PUBLIC_REPO_WORKSPACE_NAME:-"public-repo-wksp-testname"}
export PRIVATE_REPO_WORKSPACE_NAME=${PRIVATE_REPO_WORKSPACE_NAME:-"private-repo-wksp-testname"}
export PUBLIC_PROJECT_GITLAB_NAME=${PUBLIC_PROJECT_NAME:-"public-repo"}
export PRIVATE_PROJECT_GITLAB_NAME=${PRIVATE_PROJECT_NAME:-"private-repo"}
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
      if oc login -u ${OCP_ADMIN_USER_NAME} -p ${OCP_LOGIN_PASSWORD} --insecure-skip-tls-verify=false; then
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

# check that factory resolver returns correct value
testFactoryResolverNoPatOAuth() {
  PUBLIC_REPO_URL=$1
  PRIVATE_REPO_URL=$2

  echo "[INFO] Check factory resolver with NO PAT/OAuth setup"
  # for public repo
  if [ "$(requestFactoryResolverGitRepoUrl ${PUBLIC_REPO_URL} | grep "HTTP/1.1 200")" ]; then
    echo "[INFO] Factory resolver returned 'HTTP/1.1 200' status code."
  else
    echo "[ERROR] Factory resolver returned wrong status code. Expected: HTTP/1.1 200."
    exit 1
  fi

  # for private repo
  if [ "$(requestFactoryResolverGitRepoUrl ${PRIVATE_REPO_URL} | grep "HTTP/1.1 400")" ]; then
    echo "[INFO] Factory resolver returned 'HTTP/1.1 400' status code. Expected client side error."
  else
    echo "[ERROR] Factory resolver returned wrong status code. Expected 'HTTP/1.1 400'."
    exit 1
  fi
}

testFactoryResolverWithPatOAuth() {
  PUBLIC_REPO_URL=$1
  PRIVATE_REPO_URL=$2

  echo "[INFO] Check factory resolver with PAT/OAuth setup"
  # for public repo
  if [ "$(requestFactoryResolverGitRepoUrl ${PUBLIC_REPO_URL} | grep "HTTP/1.1 200")" ]; then
    echo "[INFO] Factory resolver returned 'HTTP/1.1 200' status code."
  else
    echo "[ERROR] Factory resolver returned wrong status code. Expected: HTTP/1.1 200"
    exit 1
  fi

  # for private repo
  if [ "$(requestFactoryResolverGitRepoUrl ${PRIVATE_REPO_URL} | grep "HTTP/1.1 200")" ]; then
    echo "[INFO] Factory resolver returned 'HTTP/1.1 200' status code."
  else
    echo "[ERROR] Factory resolver returned wrong status code. Expected: HTTP/1.1 200"
    exit 1
  fi
}

runTestWorkspaceWithGitRepoUrl() {
  WS_NAME=$1
  PROJECT_NAME=$2
  GIT_REPO_URL=$3

  oc new-project ${USER_PROJECT_NAME} || true
  oc project ${USER_PROJECT_NAME}
  cat .ci/openshift-ci/devworkspace-test.yaml > devworkspace-test.yaml

  # patch the devworkspace-test.yaml file
  sed -i "s#ws-name#${WS_NAME}#g" devworkspace-test.yaml
  sed -i "s#project-name#${PROJECT_NAME}#g" devworkspace-test.yaml
  sed -i "s#git-repo-url#${GIT_REPO_URL}#g" devworkspace-test.yaml

  cat devworkspace-test.yaml

  oc apply -f devworkspace-test.yaml -n ${USER_PROJECT_NAME}
  oc wait -n ${USER_PROJECT_NAME} --for=condition=Ready dw ${WS_NAME} --timeout=360s
}

testProjectIsCloned() {
  PROJECT_NAME=$1
  WORKSPACE_POD_NAME=$(oc get pods -n ${USER_PROJECT_NAME} | grep workspace | awk '{print $1}')
  if oc exec -it -n ${USER_PROJECT_NAME} ${WORKSPACE_POD_NAME} -- test -f /projects/${PROJECT_NAME}/${YAML_FILE_NAME}; then
    echo "[INFO] Project file /projects/${PROJECT_NAME}/${YAML_FILE_NAME} exists."
  else
    echo "[ERROR] Project file /projects/${PROJECT_NAME}/${YAML_FILE_NAME} does not exist."
    exit 1
  fi
}

# check a project is not cloned for private repo with no OAuth/PAT setup
testProjectIsNotCloned() {
  PROJECT_NAME=$1
  WORKSPACE_POD_NAME=$(oc get pods -n ${USER_PROJECT_NAME} | grep workspace | awk '{print $1}')
  if oc exec -it -n ${USER_PROJECT_NAME} ${WORKSPACE_POD_NAME} -- test -e /projects/${PROJECT_NAME}; then
    echo "[ERROR] Project /projects/${PROJECT_NAME} exists."
    exit 1
  else
    echo "[INFO] Project /projects/${PROJECT_NAME} does not exist."
  fi
}

deleteTestWorkspace() {
  WS_NAME=$1
  oc delete dw ${WS_NAME} -n ${USER_PROJECT_NAME}
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

testClonePublicRepoNoPatOAuth() {
  WS_NAME=$1
  PROJECT_NAME=$2
  GIT_REPO_URL=$3

  runTestWorkspaceWithGitRepoUrl ${WS_NAME} ${PROJECT_NAME} ${GIT_REPO_URL}
  testProjectIsCloned ${PROJECT_NAME}
  deleteTestWorkspace ${WS_NAME}
}

testClonePrivateRepoNoPatOAuth() {
  WS_NAME=$1
  PROJECT_NAME=$2
  GIT_REPO_URL=$3

  runTestWorkspaceWithGitRepoUrl ${WS_NAME} ${PROJECT_NAME} ${GIT_REPO_URL}
  testProjectIsNotCloned ${PROJECT_NAME}
  deleteTestWorkspace ${WS_NAME}
}

setupTestEnvironment() {
  provisionOpenShiftOAuthUser
  createCustomResourcesFile
  deployChe
  forwardPortToService
}
