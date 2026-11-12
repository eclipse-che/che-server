/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes.devworkspace;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesInfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;

/**
 * Reads the {@code DevWorkspace} custom resources that the DevWorkspace Operator keeps in the
 * namespaces of the users.
 *
 * <p>The resources are returned as {@link GenericKubernetesResource}, because Che server does not
 * depend on the DevWorkspace API model. Their content is reached with {@link
 * GenericKubernetesResource#get(String...)}, which walks the deserialized YAML tree and returns
 * {@code null} for a path that is not there, for example {@code devWorkspace.get("status",
 * "mainUrl")}. A field that the CRD does not constrain to a single type has to be type checked
 * before it is cast.
 *
 * <p>All reads go through the {@code che} ServiceAccount, see {@link
 * CheServerKubernetesClientFactory}. That account is granted {@code get}, {@code list} and {@code
 * watch} on {@code devworkspaces} across the user namespaces, so the caller is responsible for
 * passing a namespace the current user is actually entitled to - {@link #getForCurrentUser()}
 * resolves it from the subject in the {@link EnvironmentContext} and is the safe choice when the
 * resources are read on behalf of a user.
 */
@Singleton
public class KubernetesDevWorkspaces {

  public static final String DEV_WORKSPACE_GROUP = "workspace.devfile.io";
  public static final String DEV_WORKSPACE_VERSION = "v1alpha2";
  public static final String DEV_WORKSPACE_KIND = "DevWorkspace";
  public static final String DEV_WORKSPACE_PLURAL = "devworkspaces";

  /** The {@code status.phase} of a DevWorkspace whose pod is up and serving. */
  public static final String PHASE_RUNNING = "Running";

  /** The {@code status.phase} of a DevWorkspace the operator is bringing up. */
  public static final String PHASE_STARTING = "Starting";

  private static final Set<String> RUNNING_PHASES = Set.of(PHASE_RUNNING, PHASE_STARTING);

  /** The fields of a devfile that hold projects, every one of them optional. */
  private static final List<String> PROJECT_FIELDS =
      List.of("projects", "starterProjects", "dependentProjects");

  /** The CRD the DevWorkspace Operator serves the DevWorkspaces under. */
  public static final ResourceDefinitionContext DEV_WORKSPACE_CONTEXT =
      new ResourceDefinitionContext.Builder()
          .withGroup(DEV_WORKSPACE_GROUP)
          .withVersion(DEV_WORKSPACE_VERSION)
          .withKind(DEV_WORKSPACE_KIND)
          .withPlural(DEV_WORKSPACE_PLURAL)
          .withNamespaced(true)
          .build();

  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;

  /**
   * The namespace factory is reached lazily, and only {@link #getForCurrentUser()} reaches it at
   * all. The factory is built from the {@link
   * org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator.NamespaceConfigurator}
   * set, so asking for it eagerly puts this class in a circular dependency as soon as anything the
   * factory depends on reads DevWorkspaces - and Guice cannot break that cycle, because neither end
   * of it is an interface it could proxy.
   */
  private final Provider<KubernetesNamespaceFactory> namespaceFactory;

  @Inject
  public KubernetesDevWorkspaces(
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory,
      Provider<KubernetesNamespaceFactory> namespaceFactory) {
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
    this.namespaceFactory = namespaceFactory;
  }

  /**
   * Returns all the DevWorkspaces of the namespace.
   *
   * @param namespace namespace to read the DevWorkspaces from
   * @return the DevWorkspaces of the namespace, empty when it has none
   * @throws InfrastructureException when the DevWorkspaces cannot be read
   */
  public List<GenericKubernetesResource> get(String namespace) throws InfrastructureException {
    return get(namespace, null);
  }

  /**
   * Returns the DevWorkspaces of the namespace that carry all of the given labels.
   *
   * @param namespace namespace to read the DevWorkspaces from
   * @param labels labels every returned DevWorkspace has to carry, not filtered when {@code null}
   *     or empty
   * @return the matching DevWorkspaces, empty when none matches
   * @throws InfrastructureException when the DevWorkspaces cannot be read
   */
  public List<GenericKubernetesResource> get(String namespace, @Nullable Map<String, String> labels)
      throws InfrastructureException {
    try {
      NonNamespaceOperation<
              GenericKubernetesResource,
              GenericKubernetesResourceList,
              Resource<GenericKubernetesResource>>
          devWorkspaces =
              cheServerKubernetesClientFactory
                  .create()
                  .genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                  .inNamespace(namespace);
      GenericKubernetesResourceList list =
          labels == null || labels.isEmpty()
              ? devWorkspaces.list()
              : devWorkspaces.withLabels(labels).list();
      return list.getItems();
    } catch (KubernetesClientException e) {
      throw new KubernetesInfrastructureException(e);
    }
  }

  /**
   * Returns the DevWorkspace of the namespace by its name.
   *
   * @param namespace namespace to read the DevWorkspace from
   * @param name name of the DevWorkspace
   * @return the DevWorkspace, empty when the namespace has none under that name
   * @throws InfrastructureException when the DevWorkspace cannot be read
   */
  public Optional<GenericKubernetesResource> getByName(String namespace, String name)
      throws InfrastructureException {
    try {
      return Optional.ofNullable(
          cheServerKubernetesClientFactory
              .create()
              .genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
              .inNamespace(namespace)
              .withName(name)
              .get());
    } catch (KubernetesClientException e) {
      throw new KubernetesInfrastructureException(e);
    }
  }

  /**
   * Returns the DevWorkspaces of the namespace that are running.
   *
   * @param namespace namespace to read the DevWorkspaces from
   * @return the running DevWorkspaces of the namespace, empty when none of them is running
   * @throws InfrastructureException when the DevWorkspaces cannot be read
   */
  public List<GenericKubernetesResource> getRunning(String namespace)
      throws InfrastructureException {
    return get(namespace).stream()
        .filter(KubernetesDevWorkspaces::isRunning)
        .collect(Collectors.toList());
  }

  /**
   * Returns the running DevWorkspaces of every namespace of the cluster.
   *
   * <p>Unlike {@link #getRunning(String)} this is not scoped to the namespace of a single user, so
   * it is meant for the background jobs of the Che server and not for serving a user request.
   *
   * @return the running DevWorkspaces of the cluster, empty when none of them is running
   * @throws InfrastructureException when the DevWorkspaces cannot be read
   */
  public List<GenericKubernetesResource> getRunningInAllNamespaces()
      throws InfrastructureException {
    try {
      return cheServerKubernetesClientFactory
          .create()
          .genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
          .inAnyNamespace()
          .list()
          .getItems()
          .stream()
          .filter(KubernetesDevWorkspaces::isRunning)
          .collect(Collectors.toList());
    } catch (KubernetesClientException e) {
      throw new KubernetesInfrastructureException(e);
    }
  }

  /**
   * Tells whether the given DevWorkspace is running, which here covers the {@value #PHASE_STARTING}
   * phase as well as {@value #PHASE_RUNNING}: a DevWorkspace that is being brought up already has
   * the secrets of the user mounted into its containers and is served by them the same way a
   * running one is.
   *
   * <p>A DevWorkspace that the operator has not reconciled yet carries no {@code status} at all and
   * is not running.
   *
   * @param devWorkspace the DevWorkspace to inspect
   * @return {@code true} when the DevWorkspace is running or starting
   */
  public static boolean isRunning(GenericKubernetesResource devWorkspace) {
    Object phase = devWorkspace.get("status", "phase");
    return phase instanceof String && RUNNING_PHASES.contains(phase);
  }

  /**
   * Returns the URLs of the repositories the given DevWorkspace clones, read from the {@code
   * git.remotes} of every project of its devfile.
   *
   * <p>Only the remotes of the devfile are reported. A repository the user clones by hand from the
   * terminal of the workspace is equally served by the git credentials Che mounts, but nothing in
   * the DevWorkspace records it.
   *
   * @param devWorkspace the DevWorkspace to inspect
   * @return the remote URLs of its projects, in the order they are declared, empty when it declares
   *     no project or none of them comes from git
   */
  public static Set<String> getRepositoryUrls(GenericKubernetesResource devWorkspace) {
    Set<String> urls = new LinkedHashSet<>();
    for (String projectsField : PROJECT_FIELDS) {
      Object projects = devWorkspace.get("spec", "template", projectsField);
      if (!(projects instanceof List)) {
        continue;
      }
      for (Object project : (List<?>) projects) {
        if (!(project instanceof Map)) {
          continue;
        }
        Object git = ((Map<?, ?>) project).get("git");
        if (!(git instanceof Map)) {
          continue;
        }
        Object remotes = ((Map<?, ?>) git).get("remotes");
        if (!(remotes instanceof Map)) {
          continue;
        }
        for (Object remote : ((Map<?, ?>) remotes).values()) {
          if (remote instanceof String && !((String) remote).isBlank()) {
            urls.add(((String) remote).trim());
          }
        }
      }
    }
    return urls;
  }

  /**
   * Returns all the DevWorkspaces of the user the current request is made on behalf of, read from
   * the namespace Che resolves for that user and from no other.
   *
   * @return the DevWorkspaces of the current user, empty when they have none
   * @throws InfrastructureException when the namespace of the user cannot be resolved, or when the
   *     DevWorkspaces cannot be read
   */
  public List<GenericKubernetesResource> getForCurrentUser() throws InfrastructureException {
    return get(
        namespaceFactory
            .get()
            .evaluateNamespaceName(
                new NamespaceResolutionContext(EnvironmentContext.getCurrent().getSubject())));
  }
}
