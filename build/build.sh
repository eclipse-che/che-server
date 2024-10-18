#!/bin/bash
#
# Copyright (c) 2017-2023 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#

set -e
set -u

IMAGE_ALIASES=${IMAGE_ALIASES:-}
ERROR=${ERROR:-}
DIR=${DIR:-}
SHA_TAG=${SHA_TAG:-}
BUILDER=${BUILDER:-}

skip_tests() {
  if [ $SKIP_TESTS = "true" ]; then
    return 0
  else
    return 1
  fi
}

prepare_build_args() {
    IFS=',' read -r -a BUILD_ARGS_ARRAY <<< "$@"
    for i in ${BUILD_ARGS_ARRAY[@]}; do
    BUILD_ARGS+="--build-arg $i "
    done
}

init() {
  BLUE='\033[1;34m'
  GREEN='\033[0;32m'
  RED='\033[0;31m'
  BROWN='\033[0;33m'
  PURPLE='\033[0;35m'
  NC='\033[0m'
  BOLD='\033[1m'
  UNDERLINE='\033[4m'

  ORGANIZATION="quay.io/eclipse"
  PREFIX="che"
  TAG="next"
  LATEST_TAG=false
  PUSH_IMAGE=false
  SKIP_TESTS=false
  NAME="che"
  ARGS=""
  OPTIONS=""
  DOCKERFILE=""
  BUILD_COMMAND="build"
  BUILD_ARGS=""
  BUILD_PLATFORMS=""

  while [ $# -gt 0 ]; do
    case $1 in
      --*)
        OPTIONS="${OPTIONS} ${1}"
        ;;
      *)
        ARGS="${ARGS} ${1}"
        ;;
    esac

    case $1 in
      --tag:*)
        TAG="${1#*:}"
        shift ;;
      --organization:*)
        ORGANIZATION="${1#*:}"
        shift ;;
      --prefix:*)
        PREFIX="${1#*:}"
        shift ;;
      --name:*)
        NAME="${1#*:}"
        shift ;;
      --skip-tests)
        SKIP_TESTS=true
        shift ;;
      --push-image)
        PUSH_IMAGE=true
        shift ;;
      --sha-tag)
        SHA_TAG=$(git rev-parse --short HEAD)
        shift ;;
      --latest-tag)
        LATEST_TAG=true
        shift ;;
      --dockerfile:*)
        DOCKERFILE="${1#*:}"
        shift ;;
      --build-arg*:*)
        BUILD_ARGS_CSV="${1#*:}"
        prepare_build_args $BUILD_ARGS_CSV
        shift ;;
      --build-platforms:*)
        BUILD_PLATFORMS="${1#*:}"
        shift ;;
      --builder:*)
        BUILDER="${1#*:}"
        shift ;;
      --*)
        printf "${RED}Unknown parameter: $1${NC}\n"; exit 2 ;;
      *)
       shift;;
    esac
  done

  IMAGE_NAME="$ORGANIZATION/$PREFIX-$NAME:$TAG"
  IMAGE_MANIFEST="$NAME-$TAG"
}

build() {

  # Compute directory
  if [ -z $DIR ]; then
      DIR=$(cd "$(dirname "$0")"; pwd)
  fi

  BUILD_COMAMAND="build"
  if [ -z $BUILDER ]; then
      echo "BUILDER is not specified, trying with podman"
      BUILDER=$(command -v podman || true)
      if [[ ! -x $BUILDER ]]; then
          echo "[WARNING] podman is not installed, trying with buildah"
          BUILDER=$(command -v buildah || true)
          if [[ ! -x $BUILDER ]]; then
              echo "[WARNING] buildah is not installed, trying with docker"
              BUILDER=$(command -v docker || true)
              if [[ ! -x $BUILDER ]]; then
                  echo "[ERROR] This script requires podman, buildah or docker to be installed. Must abort!"; exit 1
              fi
          else
              BUILD_COMMAND="bud"
          fi
      fi
  else
      if [[ ! -x $(command -v "$BUILDER" || true) ]]; then
          echo "Builder $BUILDER is missing. Aborting."; exit 1
      fi
      if [[ $BUILDER =~ "docker" || $BUILDER =~ "podman" ]]; then
          if [[ ! $($BUILDER ps) ]]; then
              echo "Builder $BUILDER is not functioning. Aborting."; exit 1
          fi
      fi
      if [[ $BUILDER =~ "buildah" ]]; then
          BUILD_COMMAND="bud"
      fi
  fi

  # If Dockerfile is empty, build all Dockerfiles
  if [ -z ${DOCKERFILE} ]; then
    DOCKERFILES_TO_BUILD="$(ls ${DIR}/Dockerfile*)"
    ORIGINAL_TAG=${TAG}
    # Build image for each Dockerfile
    for dockerfile in ${DOCKERFILES_TO_BUILD}; do
       dockerfile=$(basename $dockerfile)
       # extract TAG from Dockerfile
       if [ ${dockerfile} != "Dockerfile" ]; then
         TAG=${ORIGINAL_TAG}-$(echo ${dockerfile} | sed -e "s/^Dockerfile.//")
       fi
       IMAGE_NAME="$ORGANIZATION/$PREFIX-$NAME:$TAG"
       DOCKERFILE=${dockerfile}
       build_image
    done

    # restore variables
    TAG=${ORIGINAL_TAG}
    IMAGE_NAME="$ORGANIZATION/$PREFIX-$NAME:$TAG"
  else
    # else if specified, build only the one specified
    build_image
  fi

}

build_image() {
  printf "${BOLD}Building Docker Image ${IMAGE_NAME} from $DIR directory with tag $TAG${NC}\n"
  # Replace macros in Dockerfiles
  cat ${DIR}/${DOCKERFILE} | sed \
    -e "s;\${BUILD_ORGANIZATION};${ORGANIZATION};" \
    -e "s;\${BUILD_PREFIX};${PREFIX};" \
    -e "s;\${BUILD_TAG};${TAG};" \
    > ${DIR}/.Dockerfile
  cd "${DIR}"

  if [[ -n $BUILD_PLATFORMS ]]; then
    if [[ $BUILDER == "podman" ]]; then
      printf "${BOLD}Creating manifest ${IMAGE_MANIFEST}${NC}\n"
      "${BUILDER}" manifest create ${IMAGE_MANIFEST}
      DOCKER_STATUS=$?
      if [ ! $DOCKER_STATUS -eq 0 ]; then
        printf "${RED}Failure when creating manifest ${IMAGE_MANIFEST}${NC}\n"
        exit 1
      fi

      printf "${BOLD}Building image ${IMAGE_NAME}${NC}\n"
      "${BUILDER}" build --platform ${BUILD_PLATFORMS} -f ${DIR}/.Dockerfile --manifest ${IMAGE_MANIFEST} .
      DOCKER_STATUS=$?
      if [ ! $DOCKER_STATUS -eq 0 ]; then
        printf "${RED}Failure when building docker image ${IMAGE_NAME}${NC}\n"
        exit 1
      fi
    else
      printf "${RED}Multi-platform image building is only supported for podman builder${NC}\n"
      exit 1
    fi
  else
    "${BUILDER}" "${BUILD_COMMAND}" -f ${DIR}/.Dockerfile -t ${IMAGE_NAME} ${BUILD_ARGS} .
    DOCKER_STATUS=$?
    if [ ! $DOCKER_STATUS -eq 0 ]; then
      printf "${RED}Failure when building docker image ${IMAGE_NAME}${NC}\n"
      exit 1
    fi
  fi

  printf "Build of ${BLUE}${IMAGE_NAME} ${GREEN}[OK]${NC}\n"

  if [[ $PUSH_IMAGE == "true" ]]; then
    push_image ${IMAGE_NAME} ${IMAGE_NAME}

    if [ ! -z "${SHA_TAG}" ]; then
      SHA_IMAGE_NAME=${ORGANIZATION}/${PREFIX}-${NAME}:${SHA_TAG}
      printf "Re-tagging with SHA based tag ${BLUE}${SHA_IMAGE_NAME} ${GREEN}[OK]${NC}\n"
      push_image ${IMAGE_NAME} ${SHA_IMAGE_NAME}
    fi

    if [[ ${LATEST_TAG} == "true" ]]; then
      LATEST_IMAGE_NAME=${ORGANIZATION}/${PREFIX}-${NAME}:latest
      printf "Re-tagging with latest tag ${BLUE}${LATEST_IMAGE_NAME} ${GREEN}[OK]${NC}\n"
      push_image ${IMAGE_NAME} ${LATEST_IMAGE_NAME}
    fi
  fi

  if [ ! -z "${IMAGE_ALIASES}" ]; then
    for TMP_IMAGE_NAME in ${IMAGE_ALIASES}
    do
      "${BUILDER}" tag ${IMAGE_NAME} ${TMP_IMAGE_NAME}:${TAG}
      DOCKER_TAG_STATUS=$?
      if [ $DOCKER_TAG_STATUS -eq 0 ]; then
        printf "  /alias ${BLUE}${TMP_IMAGE_NAME}:${TAG}${NC} ${GREEN}[OK]${NC}\n"
      else
        printf "${RED}Failure when building docker image ${IMAGE_NAME}${NC}\n"
        exit 1
      fi

    done
  fi

  if [[ -n $BUILD_PLATFORMS ]] && [[ $BUILDER == "podman" ]]; then
    # Remove manifest list from local storage
    ${BUILDER} manifest rm ${IMAGE_MANIFEST}
  fi

  printf "${GREEN}Script run successfully: ${BLUE}${IMAGE_NAME}${NC}\n"
}

push_image() {
  local image=$1
  local tagged_image=$2

  printf "Pushing manifest ${BLUE}${image} ${NC}\n"
  if [[ -n $BUILD_PLATFORMS ]] && [[ $BUILDER == "podman" ]]; then
    ${BUILDER} manifest push ${IMAGE_MANIFEST} docker://${tagged_image}
    DOCKER_STATUS=$?
    if [ ! $DOCKER_STATUS -eq 0 ]; then
      printf "${RED}Failure when pushing image ${image}${NC}\n"
      exit 1
    fi
  else
    ${BUILDER} tag ${image} ${tagged_image}
    DOCKER_STATUS=$?
    if [ ! $DOCKER_STATUS -eq 0 ]; then
      printf "${RED}Failure when tagging image ${tagged_image}${NC}\n"
      exit 1
    fi

    ${BUILDER} push ${image}
    DOCKER_STATUS=$?
    if [ ! $DOCKER_STATUS -eq 0 ]; then
      printf "${RED}Failure when pushing image ${image}${NC}\n"
      exit 1
    fi
  fi
  printf "Push of ${BLUE}${tagged_image} ${GREEN}[OK]${NC}\n"
}

get_full_path() {
  echo "$(cd "$(dirname "${1}")"; pwd)/$(basename "$1")"
}

convert_windows_to_posix() {
  echo "/"$(echo "$1" | sed 's/\\/\//g' | sed 's/://')
}

get_clean_path() {
  INPUT_PATH=$1
  # \some\path => /some/path
  OUTPUT_PATH=$(echo ${INPUT_PATH} | tr '\\' '/')
  # /somepath/ => /somepath
  OUTPUT_PATH=${OUTPUT_PATH%/}
  # /some//path => /some/path
  OUTPUT_PATH=$(echo ${OUTPUT_PATH} | tr -s '/')
  # "/some/path" => /some/path
  OUTPUT_PATH=${OUTPUT_PATH//\"}
  echo ${OUTPUT_PATH}
}

get_mount_path() {
  FULL_PATH=$(get_full_path "${1}")
  POSIX_PATH=$(convert_windows_to_posix "${FULL_PATH}")
  CLEAN_PATH=$(get_clean_path "${POSIX_PATH}")
  echo $CLEAN_PATH
}

# grab assembly
DIR="$(cd "$(dirname "$0")"; pwd)/dockerfiles"
if [ ! -d "${DIR}/../../assembly/assembly-main/target" ]; then
  echo "${ERROR}Have you built assembly/assemby-main in ${DIR}/../assembly/assembly-main 'mvn clean install'?"
  exit 2
fi

# Use of folder
BUILD_ASSEMBLY_DIR=$(echo "${DIR}"/../../assembly/assembly-main/target/eclipse-che-*/eclipse-che-*/)
LOCAL_ASSEMBLY_DIR="${DIR}"/eclipse-che

if [ -d "${LOCAL_ASSEMBLY_DIR}" ]; then
  rm -r "${LOCAL_ASSEMBLY_DIR}"
fi

echo "Copying assembly ${BUILD_ASSEMBLY_DIR} --> ${LOCAL_ASSEMBLY_DIR}"
cp -r "${BUILD_ASSEMBLY_DIR}" "${LOCAL_ASSEMBLY_DIR}"

init --name:server "$@"
build

#cleanUp
rm -rf ${DIR}/eclipse-che
