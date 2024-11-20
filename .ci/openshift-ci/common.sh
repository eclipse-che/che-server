#!/bin/bash
#
# Copyright (c) 2023-2024 Red Hat, Inc.
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
export TEST_FILE_NAME=${TEST_FILE_NAME:-"Date.txt"}
export CUSTOM_CONFIG_MAP_NAME=${CUSTOM_CONFIG_MAP_NAME:-"custom-ca-certificates"}
export GIT_SSL_CONFIG_MAP_NAME=${GIT_SSL_CONFIG_MAP_NAME:-"che-self-signed-cert"}

provisionOpenShiftOAuthUser() {
  echo "------- [INFO] Start provisioning Openshift OAuth user -------"
  htpasswd -c -B -b users.htpasswd ${OCP_ADMIN_USER_NAME} ${OCP_LOGIN_PASSWORD}
  htpasswd -b users.htpasswd ${OCP_NON_ADMIN_USER_NAME} ${OCP_LOGIN_PASSWORD}
  oc create secret generic htpass-secret --from-file=htpasswd="users.htpasswd" -n openshift-config
  oc apply -f ".ci/openshift-ci/htpasswdProvider.yaml"
  oc adm policy add-cluster-role-to-user cluster-admin ${OCP_ADMIN_USER_NAME}

  echo "------- [INFO] Waiting for htpasswd auth to be working up to 5 minutes -------"
  CURRENT_TIME=$(date +%s)
  ENDTIME=$((CURRENT_TIME + 300))
  while [ "$(date +%s)" -lt $ENDTIME ]; do
      if oc login -u=${OCP_ADMIN_USER_NAME} -p=${OCP_LOGIN_PASSWORD} --insecure-skip-tls-verify=false; then
          echo "======= [INFO] OpenShift OAuth htpasswd is configured. =======
======= [INFO] Login to OCP cluster with admin user credentials is success.======="
          return 0
      fi
      sleep 5
  done

  echo "####### [ERROR] Error occurred while waiting OpenShift OAuth htpasswd setup. Try to rerun test. #######"
  exit 1
}

configureGitSelfSignedCertificate() {
  echo "------- [INFO] Configure self-signed certificate for Git provider -------"
  oc adm new-project ${CHE_NAMESPACE}
  oc project ${CHE_NAMESPACE}

  echo "------- [INFO] Create ConfigMap with the required TLS certificate -------"
  oc create configmap ${CUSTOM_CONFIG_MAP_NAME} --from-file=.ci/openshift-ci/ca.crt
  oc label configmap ${CUSTOM_CONFIG_MAP_NAME} app.kubernetes.io/part-of=che.eclipse.org app.kubernetes.io/component=ca-bundle

  echo "------- [INFO] Create ConfigMap to support Git repositories with self-signed certificates -------"
  oc create configmap ${GIT_SSL_CONFIG_MAP_NAME} --from-file=.ci/openshift-ci/ca.crt --from-literal=githost=${GIT_PROVIDER_URL}
  oc label configmap ${GIT_SSL_CONFIG_MAP_NAME} app.kubernetes.io/part-of=che.eclipse.org

  echo "======= [INFO] ConfigMaps are configured ======="
}

createCustomResourcesFile() {
  echo "------- [INFO] Create custom resourses file -------"
  cat > custom-resources.yaml <<-END
apiVersion: org.eclipse.che/v2
spec:
  devEnvironments:
    maxNumberOfRunningWorkspacesPerUser: 10000
END

  echo "======= [INFO] Generated custom resources file ======="
  cat custom-resources.yaml
}

patchCustomResourcesFile() {
  echo "------- [INFO] Edit the custom resources file to add 'gitTrustedCertsConfigMapName' -------"
  yq -y '.spec.devEnvironments.trustedCerts += {"gitTrustedCertsConfigMapName": "'${GIT_SSL_CONFIG_MAP_NAME}'"}' custom-resources.yaml -i

  echo "======= [INFO] Patched custom resources file ======="
  cat custom-resources.yaml
}

deployChe() {
  echo "------- [INFO] Start installing Eclipse Che -------"
  chectl server:deploy --cheimage=$CHE_SERVER_IMAGE \
                       --che-operator-cr-patch-yaml=custom-resources.yaml \
                       --platform=openshift \
                       --telemetry=off \
                       --batch

  waitFinishDeploymentCheServer
  echo "======= [INFO] Eclipse Che is successfully installed ======="
}

waitFinishDeploymentCheServer() {
          CURRENT_TIME=$(date +%s)
          ENDTIME=$((CURRENT_TIME + 60))

          while [ "$(date +%s)" -lt $ENDTIME ]; do
              podCheServerName=$(oc get pod -n ${CHE_NAMESPACE} -l component=che | grep "che" | awk '{ print $1 }')
              echo "Pod Che_Server: $podCheServerName"
              count=$(echo "$podCheServerName" | wc -l)
              if [ $count -eq 1 ]; then
                  echo "------- [INFO] Only one Che Server pod is left. -------"
                  return 0
              fi
              echo "------- [INFO] Waiting until only one Che Server pod remains. -------"
              sleep 5
          done

          echo "####### [ERROR] Error occurred while waiting for only one Che Server pod. #######"
          exit 1
}

# this command starts port forwarding between the local machine and the che-host service in the OpenShift cluster.
forwardPortToService() {
  echo "------- [INFO] Start forwarding between the local machine and the che-host service -------"
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
  echo "------- [INFO] Check factory resolver for public repository with NO PAT/OAuth setup -------"
  testFactoryResolverResponse $1 200

  echo "------- [INFO] Check factory resolver for private repository with NO PAT/OAuth setup -------"
  testFactoryResolverResponse $2 500
}

# check that raw devfile url factory resolver returns correct value without any PAT/OAuth setup
testFactoryResolverNoPatOAuthRaw() {
  echo "------- [INFO] Check factory resolver for public repository with NO PAT/OAuth setup -------"
  testFactoryResolverResponse $1 200

  echo "------- [INFO] Check factory resolver for private repository with NO PAT/OAuth setup -------"
  testFactoryResolverResponse $2 400
}

# check that factory resolver returns correct value with PAT/OAuth setup
testFactoryResolverWithPatOAuth() {
  echo "------- [INFO] Check factory resolver for public repository with PAT/OAuth setup -------"
  testFactoryResolverResponse $1 200

  echo "------- [INFO] Check factory resolver for private repository with PAT/OAuth setup -------"
  testFactoryResolverResponse $2 200
}

testFactoryResolverResponse() {
  URL=$1
  RESPONSE_CODE=$2

  if [ "$(requestFactoryResolverGitRepoUrl ${URL} | grep "HTTP/1.1 ${RESPONSE_CODE}")" ]; then
    echo "======= [INFO] Factory resolver returned 'HTTP/1.1 ${RESPONSE_CODE}' status code. Expected client side response. ======="
  else
    echo "####### [ERROR] Factory resolver returned wrong status code. Expected: HTTP/1.1 ${RESPONSE_CODE}. #######
####### Cause possible: PR code regress or service is changed. Need to investigate it. #######"
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

  echo "------- [INFO] Initialize user namespace -------"
  oc login -u=${OCP_USER_NAME} -p=${OCP_LOGIN_PASSWORD} --insecure-skip-tls-verify=false

  request_result=$(requestProvisionNamespace) || true

  if [ -z "$request_result" ]; then
    echo "####### [ERROR] Cause possible: lost connection to pod, this is an infrastructure problem. Try to rerun the test. #######"
    exit 1
  elif [ "$(echo "$request_result" | grep "HTTP/1.1 200")" ]; then
    echo "======= [INFO] Request provision user namespace returned 'HTTP/1.1 200' status code. User namespace is created. ======="
  else
    echo "####### [ERROR] Request provision user namespace returned wrong status code. Expected: HTTP/1.1 200. #######
####### User namespace creation failed. Cause possible: PR code regression or service is changed. Need to investigate. #######"
    exit 1
  fi
}

setupPersonalAccessToken() {
  GIT_PROVIDER_TYPE=$1
  GIT_PROVIDER_URL=$2
  GIT_PROVIDER_PAT=$3

  echo "------- [INFO] Setup Personal Access Token Secret -------"
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
  echo "======= [INFO] Personal Access Token is created. ======="
}

setupSSHKeyPairs() {
  GIT_PRIVATE_KEY=$1
  GIT_PUBLIC_KEY=$2

  echo "------- [INFO] Setup SSH Key Pairs Secret "-------
  oc project ${USER_CHE_NAMESPACE}
  ENCODED_GIT_PRIVATE_KEY=$(echo "${GIT_PRIVATE_KEY}" | base64 -w 0)
  ENCODED_GIT_PUBLIC_KEY=$(echo "${GIT_PUBLIC_KEY}" | base64 -w 0)
  cat .ci/openshift-ci/ssh-secret.yaml > ssh-secret.yaml

  # patch the ssh-secret.yaml file
  sed -i "s#ssh_private_key#${ENCODED_GIT_PRIVATE_KEY}#g" ssh-secret.yaml
  sed -i "s#ssh_public_key#${ENCODED_GIT_PUBLIC_KEY}#g" ssh-secret.yaml

  cat ssh-secret.yaml

  oc apply -f ssh-secret.yaml -n ${USER_CHE_NAMESPACE}
  echo "======= [INFO] SSH Secret is created. ======="
}

# Only for GitLab server administrator users
createOAuthApplicationGitLabServer() {
  ADMIN_ACCESS_TOKEN=$1

  CHE_URL=https://$(oc get route -n ${CHE_NAMESPACE} che -o jsonpath='{.spec.host}')

  echo "------- [INFO] Create OAuth Application -------"
  response=$(curl -k -X POST \
    ${GIT_PROVIDER_URL}/api/v4/applications \
    -H "PRIVATE-TOKEN: ${ADMIN_ACCESS_TOKEN}" \
    -d "name=${APPLICATION_NAME}" \
    -d "redirect_uri=${CHE_URL}/api/oauth/callback" \
    -d "scopes=api write_repository openid")

  echo "------- [INFO] Response of the created OAuth Application -------"

  OAUTH_ID=$(echo "$response" | jq -r '.id')
  APPLICATION_ID=$(echo "$response" | jq -r '.application_id')
  APPLICATION_SECRET=$(echo "$response" | jq -r '.secret')
}

# Only for GitLab server administrator users
deleteOAuthApplicationGitLabServer() {
  OAUTH_ID=$1
  ADMIN_ACCESS_TOKEN=$2

  echo "------- [INFO] Delete OAuth Application -------"
  curl -i -k -X DELETE \
    ${GIT_PROVIDER_URL}/api/v4/applications/${OAUTH_ID} \
    -H "PRIVATE-TOKEN: ${ADMIN_ACCESS_TOKEN}"

  echo "======= [INFO] OAuth Application is deleted ======="
}

# Only for GitLab server
revokeAuthorizedOAuthApplication() {
  APPLICATION_ID=$1
  APPLICATION_SECRET=$2

  echo "------- [INFO] Revoke authorized OAuth application -------"
  oc project ${USER_CHE_NAMESPACE}
  OAUTH_TOKEN_NAME=$(oc get secret | grep 'personal-access-token'| awk 'NR==1 { print $1 }')
  OAUTH_TOKEN=$(oc get secret $OAUTH_TOKEN_NAME -o jsonpath='{.data.token}' | base64 -d)

  curl -i -k -X POST \
    ${GIT_PROVIDER_URL}/oauth/revoke \
    -d "client_id=${APPLICATION_ID}" \
    -d "client_secret=${APPLICATION_SECRET}" \
    -d "token=${OAUTH_TOKEN}"

  echo "======= [INFO] Authorized OAuth application is revoked ======="
}

# Only for GitLab server
setupOAuthSecret() {
  APPLICATION_ID=$1
  APPLICATION_SECRET=$2

  echo "------- [INFO] Setup OAuth Secret -------"
  oc login -u=${OCP_ADMIN_USER_NAME} -p=${OCP_LOGIN_PASSWORD} --insecure-skip-tls-verify=false
  oc project ${CHE_NAMESPACE}
  SERVER_POD=$(oc get pod -l component=che | grep "che" | awk 'NR==1 { print $1 }')

  ENCODED_APP_ID=$(echo -n "${APPLICATION_ID}" | base64 -w 0)
  ENCODED_APP_SECRET=$(echo -n "${APPLICATION_SECRET}" | base64 -w 0)
  cat .ci/openshift-ci/oauth-secret.yaml > oauth-secret.yaml

  # patch the oauth-secret.yaml file
  sed -i "s#git-provider-url#${GIT_PROVIDER_URL}#g" oauth-secret.yaml
  sed -i "s#encoded-application-id#${ENCODED_APP_ID}#g" oauth-secret.yaml
  sed -i "s#encoded-application-secret#${ENCODED_APP_SECRET}#g" oauth-secret.yaml

  cat oauth-secret.yaml
  oc apply -f oauth-secret.yaml -n ${CHE_NAMESPACE}

  echo "------- [INFO] Wait updating deployment after create OAuth secret -------"
  oc wait --for=delete pod/${SERVER_POD} --timeout=120s
}

runTestWorkspaceWithGitRepoUrl() {
  WS_NAME=$1
  PROJECT_NAME=$2
  GIT_REPO_URL=$3
  OCP_USER_NAMESPACE=$4

  oc project ${OCP_USER_NAMESPACE}
  cat .ci/openshift-ci/devworkspace-test.yaml > devworkspace-test.yaml

  echo "------- [INFO] Preparing 'devworkspace-test.yaml' and run test workspace -------"
  # patch the devworkspace-test.yaml file
  sed -i "s#ws-name#${WS_NAME}#g" devworkspace-test.yaml
  sed -i "s#project-name#${PROJECT_NAME}#g" devworkspace-test.yaml
  sed -i "s#git-repo-url#${GIT_REPO_URL}#g" devworkspace-test.yaml

  cat devworkspace-test.yaml

  oc apply -f devworkspace-test.yaml -n ${OCP_USER_NAMESPACE}
  oc wait -n ${OCP_USER_NAMESPACE} --for=condition=Ready dw ${WS_NAME} --timeout=360s
  echo "======= [INFO] Test workspace is run ======="
}

testProjectIsCloned() {
  PROJECT_NAME=$1
  OCP_USER_NAMESPACE=$2

  WORKSPACE_POD_NAME=$(oc get pods -n ${OCP_USER_NAMESPACE} | grep workspace | awk '{print $1}')
  if oc exec -it -n ${OCP_USER_NAMESPACE} ${WORKSPACE_POD_NAME} -- test -f /projects/${PROJECT_NAME}/${TEST_FILE_NAME}; then
    echo "======= [INFO] Project file /projects/${PROJECT_NAME}/${TEST_FILE_NAME} exists. ======="
  else
    echo "======= [INFO] Project file /projects/${PROJECT_NAME}/${TEST_FILE_NAME} is absent. ======="
    return 1
  fi
}

testGitCredentialsData() {
  OCP_USER_NAMESPACE=$1
  GIT_PROVIDER_PAT=$2
  GIT_PROVIDER_URL=$3

  echo "------- [INFO] Check the 'git credentials' is in a workspace -------"
  hostName="${GIT_PROVIDER_URL#https://}"

  if [ "${GIT_PROVIDER_TYPE}" == "azure-devops" ]; then
    userName="username"
  else
    userName=${GIT_PROVIDER_USERNAME}
  fi

  gitCredentials="https://${userName}:${GIT_PROVIDER_PAT}@${hostName}"
  WORKSPACE_POD_NAME=$(oc get pods -n ${OCP_USER_NAMESPACE} | grep workspace | awk '{print $1}')
  if oc exec -it -n ${OCP_USER_NAMESPACE} ${WORKSPACE_POD_NAME} -- cat /.git-credentials/credentials | grep -q ${gitCredentials}; then
    echo "======= [INFO] Git credentials file '/.git-credentials/credentials' exists and has the expected content. ======="
  else
    echo "####### [ERROR] Git credentials file '/.git-credentials/credentials' does not exist or has incorrect content. ######
###### Cause possible: PR code regress or service is changed. Need to investigate it. #######"
    exit 1
  fi
}

deleteTestWorkspace() {
  WS_NAME=$1
  OCP_USER_NAMESPACE=$2
  echo "------- [INFO] Delete test workspace -------"
  oc delete dw ${WS_NAME} -n ${OCP_USER_NAMESPACE}
}

startOAuthFactoryTest() {
  CHE_URL=https://$(oc get route -n ${CHE_NAMESPACE} che -o jsonpath='{.spec.host}')
  # patch oauth-factory-test.yaml
  cat .ci/openshift-ci/pod-oauth-factory-test.yaml > oauth-factory-test.yaml
  sed -i "s#CHE_URL#${CHE_URL}#g" oauth-factory-test.yaml
  sed -i "s#CHE-NAMESPACE#${CHE_NAMESPACE}#g" oauth-factory-test.yaml
  sed -i "s#OCP_USER_NAME#${OCP_NON_ADMIN_USER_NAME}#g" oauth-factory-test.yaml
  sed -i "s#OCP_USER_PASSWORD#${OCP_LOGIN_PASSWORD}#g" oauth-factory-test.yaml
  sed -i "s#FACTORY_REPO_URL#${PRIVATE_REPO_URL}#g" oauth-factory-test.yaml
  sed -i "s#GIT_BRANCH#${GIT_REPO_BRANCH}#g" oauth-factory-test.yaml
  sed -i "s#GIT_PROVIDER_TYPE#${GIT_PROVIDER_TYPE}#g" oauth-factory-test.yaml
  sed -i "s#GIT_PROVIDER_USER_NAME#${GIT_PROVIDER_LOGIN}#g" oauth-factory-test.yaml
  sed -i "s#GIT_PROVIDER_USER_PASSWORD#${GIT_PROVIDER_PASSWORD}#g" oauth-factory-test.yaml

  echo "------- [INFO] Applying the following patched OAuth Factory Test Pod: -------"
  cat oauth-factory-test.yaml
  echo "[INFO] --------------------------------------------------"
  oc apply -f oauth-factory-test.yaml
  # wait for the pod to start
  n=0
  while [ $n -le 120 ]
  do
    PHASE=$(oc get pod -n ${CHE_NAMESPACE} ${TEST_POD_NAME} \
        --template='{{ .status.phase }}')
    if [[ ${PHASE} == "Running" ]]; then
      echo "======= [INFO] Factory test started successfully. ======="
      return
    fi

    sleep 5
    n=$(( n+1 ))
  done

  echo "####### [ERROR] Failed to start Factory test. #######
###### Cause possible: an infrastructure problem, pod could not start, try to rerun the test. #######"
  exit 1
}

startSmokeTest() {
  CHE_URL=https://$(oc get route -n ${CHE_NAMESPACE} che -o jsonpath='{.spec.host}')
  # patch che-smoke-test.yaml
  cat .ci/openshift-ci/pod-che-smoke-test.yaml > che-smoke-test.yaml
  sed -i "s#CHE_URL#${CHE_URL}#g" che-smoke-test.yaml
  sed -i "s#CHE-NAMESPACE#${CHE_NAMESPACE}#g" che-smoke-test.yaml
  sed -i "s#OCP_USER_NAME#${OCP_NON_ADMIN_USER_NAME}#g" che-smoke-test.yaml
  sed -i "s#OCP_USER_PASSWORD#${OCP_LOGIN_PASSWORD}#g" che-smoke-test.yaml

  echo "------- [INFO] Applying the following patched Smoke Test Pod: -------"
  cat che-smoke-test.yaml
  echo "[INFO] --------------------------------------------------"
  oc apply -f che-smoke-test.yaml
  # wait for the pod to start
  n=0
  while [ $n -le 120 ]
  do
    PHASE=$(oc get pod -n ${CHE_NAMESPACE} ${TEST_POD_NAME} \
        --template='{{ .status.phase }}')
    if [[ ${PHASE} == "Running" ]]; then
      echo "======= [INFO] Smoke test started successfully. ======="
      return
    fi

    sleep 5
    n=$(( n+1 ))
  done

  echo "####### [ERROR] Failed to start Smoke test. #######
####### Cause possible: an infrastructure problem, pod could not start, try to rerun the test. #######"
  exit 1
}

# Catch the finish of the job and write logs in artifacts.
catchFinish() {
  local RESULT=$?
  if [ "$RESULT" != "0" ]; then
    set +e
    collectEclipseCheLogs
    set -e
  fi

  echo "------- [INFO] Terminate the process after finish the test script. -------"
  set +e
  killProcessByPort
  set -e

  [[ "${RESULT}" != "0" ]] && echo "####### [ERROR] Job failed. #######" || echo "####### [INFO] Job completed successfully. #######"
  exit $RESULT
}

collectEclipseCheLogs() {
  mkdir -p ${ARTIFACTS_DIR}/che-logs

  # Collect all Eclipse Che logs and cluster CR
  oc login -u=${OCP_ADMIN_USER_NAME} -p=${OCP_LOGIN_PASSWORD} --insecure-skip-tls-verify=false
  oc get checluster -o yaml -n $CHE_NAMESPACE > "${ARTIFACTS_DIR}/che-cluster.yaml"
  chectl server:logs -n $CHE_NAMESPACE --directory ${ARTIFACTS_DIR}/che-logs --telemetry off
}

collectLogs() {
  echo "------- [INFO] Waiting until test pod finished. -------"
  oc logs -n ${CHE_NAMESPACE} ${TEST_POD_NAME} -c test -f
  sleep 3

  # Download artifacts
  set +e
  echo -------" [INFO] Collect all Eclipse Che logs and cluster CR. -------"
  collectEclipseCheLogs

  echo "------- [INFO] Downloading test report. -------"
  mkdir -p ${ARTIFACTS_DIR}/e2e
  oc rsync -n ${CHE_NAMESPACE} ${TEST_POD_NAME}:/tmp/e2e/report/ ${ARTIFACTS_DIR}/e2e -c download-reports
  oc exec -n ${CHE_NAMESPACE} ${TEST_POD_NAME} -c download-reports -- touch /tmp/done

  # Revoke and delete the OAuth application
  if [[ ${TEST_POD_NAME} == "oauth-factory-test" ]]; then
    revokeAuthorizedOAuthApplication ${APPLICATION_ID} ${APPLICATION_SECRET}
    deleteOAuthApplicationGitLabServer ${OAUTH_ID} ${ADMIN_ACCESS_TOKEN}
  fi

  set -e

  EXIT_CODE=$(oc logs -n ${CHE_NAMESPACE} ${TEST_POD_NAME} -c test | grep EXIT_CODE)
  if [[ ${EXIT_CODE} != "+ EXIT_CODE=0" ]]; then
    echo "####### [ERROR] GUI test failed. Job failed. #######
###### Cause possible: PR code regress or service is changed. Need to investigate it. #######"
    exit 1
  fi
  echo "======= [INFO] Job completed successfully. ======="
}

testCloneGitRepoNoProjectExists() {
    WS_NAME=$1
    PROJECT_NAME=$2
    GIT_REPO_URL=$3
    OCP_USER_NAMESPACE=$4

    runTestWorkspaceWithGitRepoUrl ${WS_NAME} ${PROJECT_NAME} ${GIT_REPO_URL} ${OCP_USER_NAMESPACE}
    echo "------- [INFO] Check the private repository is NOT cloned with NO PAT/OAuth setup. -------"
    testProjectIsCloned ${PROJECT_NAME} ${OCP_USER_NAMESPACE} && \
    { echo "####### [ERROR] Project file /projects/${PROJECT_NAME}/${TEST_FILE_NAME} should NOT be present. #######
####### Cause possible: PR code regress or service is changed. Need to investigate it. #######" && exit 1; }
    echo "======= [INFO] Project file /projects/${PROJECT_NAME}/${TEST_FILE_NAME} is NOT present. This is EXPECTED. ======="
}

# Verify that a public repository is cloned without requiring PAT, OAuth, or SSH configuration.
# Verify that a public or private repository is cloned when PAT, OAuth, or SSH configuration is provided.
testCloneGitRepoProjectShouldExists() {
  WS_NAME=$1
  PROJECT_NAME=$2
  GIT_REPO_URL=$3
  OCP_USER_NAMESPACE=$4

  runTestWorkspaceWithGitRepoUrl ${WS_NAME} ${PROJECT_NAME} ${GIT_REPO_URL} ${OCP_USER_NAMESPACE}
  echo "------- [INFO] Check the repository is cloned. -------"
  testProjectIsCloned ${PROJECT_NAME} ${OCP_USER_NAMESPACE} || \
  { echo "####### [ERROR] Project file /projects/${PROJECT_NAME}/${TEST_FILE_NAME} should be present. #######
###### Cause possible: PR code regress or service is changed. Need to investigate it. #######" && exit 1; }
}

setupTestEnvironment() {
  OCP_USER_NAME=$1

  provisionOpenShiftOAuthUser
  createCustomResourcesFile
  deployChe
  forwardPortToService
  initUserNamespace ${OCP_USER_NAME}
}

setupTestEnvironmentOAuthFlow() {
  ADMIN_ACCESS_TOKEN=$1
  APPLICATION_ID=$2
  APPLICATION_SECRET=$3

  provisionOpenShiftOAuthUser
  configureGitSelfSignedCertificate
  createCustomResourcesFile
  patchCustomResourcesFile
  deployChe
  createOAuthApplicationGitLabServer ${ADMIN_ACCESS_TOKEN} ${APPLICATION_NAME}
  setupOAuthSecret ${APPLICATION_ID} ${APPLICATION_SECRET}
}
