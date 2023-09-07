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

FROM registry.ci.openshift.org/openshift/release:golang-1.18

SHELL ["/bin/bash", "-c"]

USER 0

# Install yq, kubectl, chectl cli used by olm/olm.sh script.
RUN dnf install -y -q --allowerasing --nobest nodejs-devel nodejs-libs \
    # already installed or installed as deps:
    openssl openssl-devel ca-certificates make cmake cpp gcc gcc-c++ zlib zlib-devel brotli brotli-devel python3 nodejs-packaging && \
    dnf update -y && dnf clean all && \
    npm install -g yarn@1.22 npm@9 && \
    echo -n "node version: "; node -v; \
    echo -n "npm  version: "; npm -v; \
    echo -n "yarn version: "; yarn -v && \
    yum install --assumeyes -d1 python3-pip  httpd-tools && \
    pip3 install --upgrade setuptools && \
    pip3 install yq && \
    curl -LO https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl && \
    chmod +x ./kubectl && \
    mv ./kubectl /usr/local/bin && \
    bash <(curl -sL https://www.eclipse.org/che/chectl/) --channel=next && \
    curl https://mirror.openshift.com/pub/openshift-v4/clients/ocp/4.12.30/openshift-client-linux.tar.gz | tar xvzf - -C /usr/local/bin/ oc && \
    chmod ug+x /usr/local/bin/oc
