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

import static com.google.common.base.Strings.emptyToNull;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesInfrastructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the {@code DevWorkspace} custom resources that the DevWorkspace Operator keeps in the
 * namespaces of the users.
 *
 * <p>The resources are returned as {@link GenericKubernetesResource}, because Che server does not
 * depend on the DevWorkspace API model. Their content is reached with {@link
 * GenericKubernetesResource#get(Object...)}, which walks the deserialized YAML tree and returns
 * {@code null} for a path that is not there, for example {@code devWorkspace.get("status",
 * "phase")}. A field that the CRD does not constrain to a single type has to be type checked before
 * it is cast.
 *
 * <p>All reads go through the {@code che} ServiceAccount, see {@link
 * CheServerKubernetesClientFactory}, which is granted {@code list} on {@code devworkspaces} across
 * the cluster. Nothing here is scoped to a single user, so this is for the background jobs of the
 * Che server and not for serving a user request.
 */
@Singleton
public class KubernetesDevWorkspaces {

  private static final Logger LOG = LoggerFactory.getLogger(KubernetesDevWorkspaces.class);

  public static final String DEV_WORKSPACE_GROUP = "workspace.devfile.io";
  public static final String DEV_WORKSPACE_VERSION = "v1alpha2";
  public static final String DEV_WORKSPACE_KIND = "DevWorkspace";
  public static final String DEV_WORKSPACE_PLURAL = "devworkspaces";

  /** The {@code status.phase} of a DevWorkspace whose pod is up and serving. */
  public static final String PHASE_RUNNING = "Running";

  /** The {@code status.phase} of a DevWorkspace the operator is bringing up. */
  public static final String PHASE_STARTING = "Starting";

  private static final Set<String> RUNNING_PHASES = Set.of(PHASE_RUNNING, PHASE_STARTING);

  /**
   * The label the DevWorkspace Operator stamps the DevWorkspace with, holding the {@code uid} of
   * the Kubernetes identity that created it. The operator sets it from the admission request and
   * puts it back when it is edited away, so unlike anything else a user can write into their own
   * namespace, it says who the DevWorkspace really belongs to.
   *
   * <p>The identity has to have a {@code uid} for the label to be of any use. On OpenShift it is
   * the {@code uid} of the User object, which is what Che takes the id of the user from, see {@code
   * OpenshiftTokenInitializationFilter}, so the two can be compared. An OIDC provider that Kube
   * authenticates against commonly issues no {@code uid} at all, and the operator then stamps the
   * DevWorkspace with an empty one, which says nothing about the owner.
   */
  public static final String CREATOR_LABEL = "controller.devfile.io/creator";

  /**
   * The label the OpenShift console stamps the DevWorkspace of a web terminal with. Those belong to
   * the Web Terminal Operator rather than to Che, and are left out of every read here, see
   * https://redhat-developer.github.io/web-terminal-operator/.
   */
  public static final String WEB_TERMINAL_LABEL = "console.openshift.io/terminal";

  /**
   * How many DevWorkspaces one page of a cluster wide read carries. A DevWorkspace holds the whole
   * devfile it was started from, so a page is kept well below the number of workspaces a cluster
   * may hold.
   */
  static final int PAGE_SIZE = 100;

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

  @Inject
  public KubernetesDevWorkspaces(
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory) {
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
  }

  /**
   * Returns the running DevWorkspaces of every namespace of the cluster.
   *
   * <p>A DevWorkspace carries the whole devfile it was started from, so the cluster of a large
   * deployment holds more of them than one response should carry. They are read a page of {@value
   * #PAGE_SIZE} at a time, and only the running ones are kept, which is the handful the caller is
   * after. The web terminals of the OpenShift console are left to the operator that owns them, see
   * {@value #WEB_TERMINAL_LABEL}.
   *
   * <p>A page that cannot be read ends the walk: what was read until then is returned, and the
   * caller works with the part of the cluster it was given. The next run reads the whole of it
   * again, so nothing is lost for good.
   *
   * @return the running DevWorkspaces of the cluster, empty when none of them is running
   * @throws InfrastructureException when the first page cannot be read, which says the
   *     DevWorkspaces cannot be read at all rather than that one page went missing
   */
  public List<GenericKubernetesResource> getRunningInAllNamespaces()
      throws InfrastructureException {
    List<GenericKubernetesResource> running = new ArrayList<>();
    String continueToken = null;
    do {
      GenericKubernetesResourceList page;
      try {
        page =
            cheServerKubernetesClientFactory
                .create()
                .genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                .inAnyNamespace()
                .withoutLabel(WEB_TERMINAL_LABEL, "true")
                .list(PAGE_SIZE, continueToken);
      } catch (KubernetesClientException e) {
        if (continueToken == null) {
          throw new KubernetesInfrastructureException(e);
        }
        // A continue token expires with the window the API server keeps a read open for, so a
        // walk that is slower than that window ends here rather than starting over.
        LOG.warn(
            "Failed to read a page of the DevWorkspaces of the cluster, carrying on with the {}"
                + " running one(s) read so far",
            running.size(),
            e);
        return running;
      }
      page.getItems().stream().filter(KubernetesDevWorkspaces::isRunning).forEach(running::add);
      continueToken =
          page.getMetadata() == null ? null : emptyToNull(page.getMetadata().getContinue());
    } while (continueToken != null);
    return running;
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
   * Returns the id of the user who created the given DevWorkspace, read from the {@value
   * #CREATOR_LABEL} label the DevWorkspace Operator stamps it with.
   *
   * @param devWorkspace the DevWorkspace to inspect
   * @return the id of its creator, empty when the operator recorded none, which is what an identity
   *     without a {@code uid} leaves behind
   */
  public static Optional<String> getCreator(GenericKubernetesResource devWorkspace) {
    if (devWorkspace.getMetadata() == null || devWorkspace.getMetadata().getLabels() == null) {
      return Optional.empty();
    }
    String creator = devWorkspace.getMetadata().getLabels().get(CREATOR_LABEL);
    return creator == null || creator.isBlank() ? Optional.empty() : Optional.of(creator);
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
}
