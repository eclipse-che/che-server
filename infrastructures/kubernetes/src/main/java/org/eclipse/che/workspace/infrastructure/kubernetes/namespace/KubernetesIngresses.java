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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace;

import static io.fabric8.kubernetes.api.model.DeletionPropagation.BACKGROUND;
import static java.util.stream.Collectors.toList;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.CHE_WORKSPACE_ID_LABEL;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesObjectUtil.putLabel;

import io.fabric8.kubernetes.api.model.LoadBalancerStatusBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackendBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressServiceBackendBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesInfrastructureException;

/**
 * Defines an internal API for managing {@link Ingress} instances in {@link
 * KubernetesIngresses#namespace predefined namespace}.
 *
 * @author Sergii Leshchenko
 * @author Guy Daich
 */
public class KubernetesIngresses {

  private final String namespace;
  private final String workspaceId;
  private final KubernetesClientFactory clientFactory;

  KubernetesIngresses(String namespace, String workspaceId, KubernetesClientFactory clientFactory) {
    this.namespace = namespace;
    this.workspaceId = workspaceId;
    this.clientFactory = clientFactory;
  }

  public Ingress create(Ingress ingress) throws InfrastructureException {
    putLabel(ingress, CHE_WORKSPACE_ID_LABEL, workspaceId);
    try {
      return asExtensionsIngress(
          clientFactory
              .create(workspaceId)
              .network()
              .v1()
              .ingresses()
              .inNamespace(namespace)
              .withName(ingress.getMetadata().getName())
              .create(asNetworkingIngress(ingress)));
    } catch (KubernetesClientException e) {
      throw new KubernetesInfrastructureException(e);
    }
  }

  public List<Ingress> get() throws InfrastructureException {
    try {
      List<io.fabric8.kubernetes.api.model.networking.v1.Ingress> v1Ingresses =
          clientFactory
              .create(workspaceId)
              .network()
              .v1()
              .ingresses()
              .inNamespace(namespace)
              .withLabel(CHE_WORKSPACE_ID_LABEL, workspaceId)
              .list()
              .getItems();
      return v1Ingresses.stream().map(this::asExtensionsIngress).collect(toList());
    } catch (KubernetesClientException e) {
      throw new KubernetesInfrastructureException(e);
    }
  }

  public Ingress wait(String name, long timeout, TimeUnit timeoutUnit, Predicate<Ingress> predicate)
      throws InfrastructureException {
    CompletableFuture<Ingress> future = new CompletableFuture<>();
    Watch watch = null;
    try {
      Resource<io.fabric8.kubernetes.api.model.networking.v1.Ingress> ingressResource =
          clientFactory
              .create(workspaceId)
              .network()
              .v1()
              .ingresses()
              .inNamespace(namespace)
              .withName(name);

      watch =
          ingressResource.watch(
              new Watcher<>() {
                @Override
                public void eventReceived(
                    Action action, io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress) {
                  Ingress extIngress = asExtensionsIngress(ingress);
                  if (predicate.test(extIngress)) {
                    future.complete(extIngress);
                  }
                }

                @Override
                public void onClose(WatcherException cause) {
                  future.completeExceptionally(
                      new InfrastructureException(
                          "Waiting for ingress '" + name + "' was interrupted"));
                }
              });

      Ingress actualIngress = asExtensionsIngress(ingressResource.get());
      if (actualIngress == null) {
        throw new InfrastructureException("Specified ingress " + name + " doesn't exist");
      }
      if (predicate.test(actualIngress)) {
        return actualIngress;
      }
      try {
        return future.get(timeout, timeoutUnit);
      } catch (ExecutionException e) {
        throw new InfrastructureException(e.getCause().getMessage(), e);
      } catch (TimeoutException e) {
        throw new InfrastructureException("Waiting for ingress '" + name + "' reached timeout");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new InfrastructureException("Waiting for ingress '" + name + "' was interrupted");
      }
    } catch (KubernetesClientException e) {
      throw new KubernetesInfrastructureException(e);
    } finally {
      if (watch != null) {
        watch.close();
      }
    }
  }

  public void delete() throws InfrastructureException {
    try {

      clientFactory
          .create(workspaceId)
          .extensions()
          .ingresses()
          .inNamespace(namespace)
          .withLabel(CHE_WORKSPACE_ID_LABEL, workspaceId)
          .withPropagationPolicy(BACKGROUND)
          .delete();

      clientFactory
          .create(workspaceId)
          .network()
          .ingresses()
          .inNamespace(namespace)
          .withLabel(CHE_WORKSPACE_ID_LABEL, workspaceId)
          .withPropagationPolicy(BACKGROUND)
          .delete();
    } catch (KubernetesClientException e) {
      throw new KubernetesInfrastructureException(e);
    }
  }

  private io.fabric8.kubernetes.api.model.networking.v1.Ingress asNetworkingIngress(
      Ingress ingress) {
    return new IngressBuilder()
        .withKind(ingress.getKind())
        .withMetadata(ingress.getMetadata())
        .withNewStatus()
        .withLoadBalancer(
            new LoadBalancerStatusBuilder()
                .withIngress(ingress.getStatus().getLoadBalancer().getIngress())
                .build())
        .endStatus()
        .withNewSpec()
        .withIngressClassName(ingress.getSpec().getIngressClassName())
        .withDefaultBackend(
            new IngressBackendBuilder()
                .withResource(ingress.getSpec().getBackend().getResource())
                .build())
        .withTls(
            ingress
                .getSpec()
                .getTls()
                .stream()
                .map(
                    t ->
                        new IngressTLSBuilder()
                            .withSecretName(t.getSecretName())
                            .withHosts(t.getHosts())
                            .build())
                .collect(toList()))
        .withRules(
            ingress
                .getSpec()
                .getRules()
                .stream()
                .map(
                    r ->
                        new io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder()
                            .withHost(r.getHost())
                            .withHttp(
                                new io.fabric8.kubernetes.api.model.networking.v1
                                        .HTTPIngressRuleValueBuilder()
                                    .withPaths(
                                        r.getHttp()
                                            .getPaths()
                                            .stream()
                                            .map(
                                                httpIngressPath ->
                                                    new HTTPIngressPathBuilder()
                                                        .withBackend(
                                                            new IngressBackendBuilder()
                                                                .withService(
                                                                    new IngressServiceBackendBuilder()
                                                                        .withName(
                                                                            httpIngressPath
                                                                                .getBackend()
                                                                                .getServiceName())
                                                                        .withNewPort(
                                                                            httpIngressPath
                                                                                .getBackend()
                                                                                .getServicePort()
                                                                                .getStrVal(),
                                                                            httpIngressPath
                                                                                .getBackend()
                                                                                .getServicePort()
                                                                                .getIntVal())
                                                                        .build())
                                                                .build())
                                                        .build())
                                            .collect(toList()))
                                    .build())
                            .build())
                .collect(toList()))
        .endSpec()
        .build();
  }

  private Ingress asExtensionsIngress(
      io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress) {
    return new io.fabric8.kubernetes.api.model.extensions.IngressBuilder()
        .withKind(ingress.getKind())
        .withMetadata(ingress.getMetadata())
        .withNewStatus()
        .withLoadBalancer(
            new LoadBalancerStatusBuilder()
                .withIngress(ingress.getStatus().getLoadBalancer().getIngress())
                .build())
        .endStatus()
        .withNewSpec()
        .withIngressClassName(ingress.getSpec().getIngressClassName())
        .withBackend(
            new io.fabric8.kubernetes.api.model.extensions.IngressBackendBuilder()
                .withResource(ingress.getSpec().getDefaultBackend().getResource())
                .build())
        .withTls(
            ingress
                .getSpec()
                .getTls()
                .stream()
                .map(
                    t ->
                        new io.fabric8.kubernetes.api.model.extensions.IngressTLSBuilder()
                            .withSecretName(t.getSecretName())
                            .withHosts(t.getHosts())
                            .build())
                .collect(toList()))
        .withRules(
            ingress
                .getSpec()
                .getRules()
                .stream()
                .map(
                    r ->
                        new io.fabric8.kubernetes.api.model.extensions.IngressRuleBuilder()
                            .withHost(r.getHost())
                            .withHttp(
                                new io.fabric8.kubernetes.api.model.extensions
                                        .HTTPIngressRuleValueBuilder()
                                    .withPaths(
                                        r.getHttp()
                                            .getPaths()
                                            .stream()
                                            .map(
                                                httpIngressPath ->
                                                    new io.fabric8.kubernetes.api.model.extensions
                                                            .HTTPIngressPathBuilder()
                                                        .withBackend(
                                                            new io.fabric8.kubernetes.api.model
                                                                    .extensions
                                                                    .IngressBackendBuilder()
                                                                .withServiceName(
                                                                    httpIngressPath
                                                                        .getBackend()
                                                                        .getService()
                                                                        .getName())
                                                                .withNewServicePort()
                                                                .withIntVal(
                                                                    httpIngressPath
                                                                        .getBackend()
                                                                        .getService()
                                                                        .getPort()
                                                                        .getNumber())
                                                                .withStrVal(
                                                                    httpIngressPath
                                                                        .getBackend()
                                                                        .getService()
                                                                        .getPort()
                                                                        .getName())
                                                                .endServicePort()
                                                                .build())
                                                        .build())
                                            .collect(toList()))
                                    .build())
                            .build())
                .collect(toList()))
        .endSpec()
        .build();
  }
}
