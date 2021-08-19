/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes.server.external;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressRuleValue;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressRuleValueBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackend;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackendBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressServiceBackend;
import io.fabric8.kubernetes.api.model.networking.v1.IngressServiceBackendBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressSpec;
import io.fabric8.kubernetes.api.model.networking.v1.IngressSpecBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPortBuilder;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.che.api.core.model.workspace.config.ServerConfig;
import org.eclipse.che.workspace.infrastructure.kubernetes.Annotations;

/**
 * helper class for builder ingresses. Creates an ingress with a single rule, based on hostname,
 * http path. Ingress maps path to a specific service and service port.
 *
 * @author Guy Daich
 */
public class ExternalServerIngressBuilder {

  private String host;
  private String path;
  private String name;
  private String serviceName;
  private String servicePortName;
  private Integer servicePort;
  private Map<String, ? extends ServerConfig> serversConfigs;
  private String machineName;
  private Map<String, String> annotations;
  private Map<String, String> labels;

  @VisibleForTesting static final String INGRESS_PATH_TYPE = "Prefix";

  public ExternalServerIngressBuilder withHost(String host) {
    this.host = host;
    return this;
  }

  public ExternalServerIngressBuilder withPath(String path) {
    this.path = path;
    return this;
  }

  public ExternalServerIngressBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public ExternalServerIngressBuilder withServiceName(String serviceName) {
    this.serviceName = serviceName;
    return this;
  }

  public ExternalServerIngressBuilder withAnnotations(Map<String, String> annotations) {
    this.annotations = annotations;
    return this;
  }

  public ExternalServerIngressBuilder withServicePort(Integer targetPort) {
    this.servicePort = targetPort;
    return this;
  }

  public ExternalServerIngressBuilder withServicePortName(String targetPortName) {
    this.servicePortName = targetPortName;
    return this;
  }

  public ExternalServerIngressBuilder withServers(
      Map<String, ? extends ServerConfig> serversConfigs) {
    this.serversConfigs = serversConfigs;
    return this;
  }

  public ExternalServerIngressBuilder withMachineName(String machineName) {
    this.machineName = machineName;
    return this;
  }

  public ExternalServerIngressBuilder withLabels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public Ingress build() {

    ServiceBackendPortBuilder serviceBackendPortBuilder = new ServiceBackendPortBuilder();

    // cannot set both port and name
    if (!isNullOrEmpty(servicePortName)) {
      serviceBackendPortBuilder.withName(servicePortName);
    } else if (servicePort != null) {
      serviceBackendPortBuilder.withNumber(servicePort);
    }

    IngressServiceBackend ingressServiceBackend =
        new IngressServiceBackendBuilder()
            .withPort(serviceBackendPortBuilder.build())
            .withName(serviceName)
            .build();

    IngressBackend ingressBackend =
        new IngressBackendBuilder().withService(ingressServiceBackend).build();

    HTTPIngressPathBuilder httpIngressPathBuilder =
        new HTTPIngressPathBuilder().withBackend(ingressBackend).withPathType(INGRESS_PATH_TYPE);

    if (!isNullOrEmpty(path)) {
      httpIngressPathBuilder.withPath(path);
    }

    HTTPIngressPath httpIngressPath = httpIngressPathBuilder.build();

    HTTPIngressRuleValue httpIngressRuleValue =
        new HTTPIngressRuleValueBuilder().withPaths(httpIngressPath).build();
    IngressRuleBuilder ingressRuleBuilder = new IngressRuleBuilder().withHttp(httpIngressRuleValue);

    if (!isNullOrEmpty(host)) {
      ingressRuleBuilder.withHost(host);
    }

    IngressRule ingressRule = ingressRuleBuilder.build();

    IngressSpec ingressSpec = new IngressSpecBuilder().withRules(ingressRule).build();

    Map<String, String> ingressAnnotations = new HashMap<>(annotations);
    ingressAnnotations.putAll(
        Annotations.newSerializer().servers(serversConfigs).machineName(machineName).annotations());

    return new IngressBuilder()
        .withSpec(ingressSpec)
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(name)
                .withAnnotations(ingressAnnotations)
                .withLabels(labels)
                .build())
        .build();
  }
}
