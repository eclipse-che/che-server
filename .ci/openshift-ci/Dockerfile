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

# Dockerfile to bootstrap build and test in openshift-ci
FROM registry.access.redhat.com/ubi9/nodejs-18:1
# hadolint ignore=DL3002
USER 0

# Install yq, kubectl, chectl cli used by olm/olm.sh script.
# hadolint ignore=DL3041
RUN set -x && dnf install -v -y -q --allowerasing --nobest nodejs-devel nodejs-libs psmisc python3-pip jq golang httpd-tools \
    # already installed or installed as deps:
    openssl openssl-devel ca-certificates make cmake cpp gcc gcc-c++ zlib zlib-devel brotli brotli-devel python3 nodejs-packaging && \
    dnf update -y && dnf clean all && \
    npm install -g yarn@1.22 npm@9 && \
    echo -n "node version: "; node -v; \
    echo -n "npm  version: "; npm -v; \
    echo -n "yarn version: "; yarn -v; \
    go version; \
    pip3 install --upgrade pip setuptools yq && \

    # Install kubectl, chectl cli used by olm/olm.sh script.
    curl -LO https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl && \
    chmod +x ./kubectl && \
    mv ./kubectl /usr/local/bin && \
    bash <(curl -sL https://www.eclipse.org/che/chectl/) --channel=next && \
    curl https://mirror.openshift.com/pub/openshift-v4/clients/ocp/latest-4.12/openshift-client-linux.tar.gz | tar xvzf - -C /usr/local/bin/ oc && \
    chmod ug+x /usr/local/bin/oc

SHELL ["/bin/bash", "-c"]

