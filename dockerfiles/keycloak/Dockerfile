# Copyright (c) 2018 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#

FROM quay.io/keycloak/keycloak:15.0.2

ADD che /opt/jboss/keycloak/themes/che
ADD che-username-readonly /opt/jboss/keycloak/themes/che-username-readonly
ADD . /scripts/
ADD cli /scripts/cli
USER root
RUN microdnf install findutils && microdnf clean all && \
    ln -s /opt/jboss/tools/docker-entrypoint.sh && chmod +x /opt/jboss/tools/docker-entrypoint.sh

USER root
RUN chown -R 1000:0 /scripts && \
    chmod -R g+rwX /scripts

USER 1000:1000
