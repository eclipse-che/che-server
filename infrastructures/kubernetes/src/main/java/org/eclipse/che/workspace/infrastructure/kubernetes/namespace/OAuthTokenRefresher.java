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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.schedule.ScheduleRate;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.devworkspace.KubernetesDevWorkspaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the OAuth tokens of the running workspaces alive.
 *
 * <p>OAuth tokens are short-living, GitLab for instance issues them for two hours. A token is
 * refreshed when it is read and the provider no longer accepts it, see {@link
 * PersonalAccessTokenManager#get(org.eclipse.che.commons.subject.Subject, String, String, String)},
 * but nothing reads the token of a user who is not using Che at that moment. Their running
 * workspace is then left with an expired token in its git credentials until the next read.
 *
 * <p>This job closes that gap. Every {@value #REFRESH_PERIOD_SEC_PROPERTY} seconds it reads the
 * running DevWorkspaces of the whole cluster, and for each namespace that has one it refreshes the
 * OAuth tokens that have expired, or that expire before the job is due to run again. Setting
 * {@value #REFRESH_ENABLED_PROPERTY} to {@code false} turns the job off.
 *
 * <p>Everything the job needs is read from the cluster, so it covers every user from the first run
 * on, with no state to lose across a Che server restart. It deliberately does not hook into {@link
 * org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator.NamespaceConfigurator}:
 * those run only when Che itself provisions a namespace, which a deployment that has its namespaces
 * prepared for it never does.
 *
 * <p>Only the tokens a running workspace is actually using are refreshed. A token hurts no one
 * while it sits in a namespace whose workspaces are all stopped: the refresh-on-read flow hands the
 * user a fresh one the next time they start a workspace. So the job collects the repositories the
 * running workspaces clone, and refreshes the token of an SCM provider only when one of those
 * repositories is served by it. Without that match the job would spend a call to every OAuth
 * provider of every user, on every run, for the whole life of the Che server.
 *
 * <p>All reads go through the {@code che} ServiceAccount, see {@link
 * CheServerKubernetesClientFactory}, which needs {@code list} on {@code devworkspaces} across the
 * cluster and on the {@code secrets} of the user namespaces.
 */
@Singleton
public class OAuthTokenRefresher {

  /**
   * Whether the job runs at all, {@code CHE_OAUTH_TOKEN__REFRESH_ENABLED} in the environment of the
   * Che server.
   */
  static final String REFRESH_ENABLED_PROPERTY = "che.oauth.token_refresh.enabled";

  /**
   * How often, in seconds, the tokens of the running workspaces are checked, {@code
   * CHE_OAUTH_TOKEN__REFRESH_PERIOD__SEC} in the environment of the Che server. A non positive
   * value keeps the job from being scheduled, just like disabling it.
   */
  static final String REFRESH_PERIOD_SEC_PROPERTY = "che.oauth.token_refresh.period_sec";

  private static final Map<String, String> SEARCH_LABELS =
      ImmutableMap.of(
          "app.kubernetes.io/part-of", "che.eclipse.org",
          "app.kubernetes.io/component", "scm-personal-access-token");

  private static final String ANNOTATION_CHE_USERID = "che.eclipse.org/che-userid";
  private static final String ANNOTATION_SCM_URL = "che.eclipse.org/scm-url";

  private static final Logger LOG = LoggerFactory.getLogger(OAuthTokenRefresher.class);

  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private final KubernetesDevWorkspaces devWorkspaces;
  private final boolean refreshEnabled;

  @Inject
  public OAuthTokenRefresher(
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory,
      PersonalAccessTokenManager personalAccessTokenManager,
      KubernetesDevWorkspaces devWorkspaces,
      @Named(REFRESH_ENABLED_PROPERTY) boolean refreshEnabled,
      @Named(REFRESH_PERIOD_SEC_PROPERTY) long refreshPeriodSec) {
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.devWorkspaces = devWorkspaces;
    this.refreshEnabled = refreshEnabled;
    if (refreshEnabled && refreshPeriodSec > 0) {
      LOG.info(
          "The OAuth tokens of the running workspaces are refreshed every {} second(s)",
          refreshPeriodSec);
    } else {
      LOG.info("The refresh of the OAuth tokens of the running workspaces is disabled");
    }
  }

  /**
   * Refreshes the expired OAuth tokens of every namespace that holds a running workspace.
   *
   * <p>A namespace that cannot be read, and a token that cannot be refreshed, are logged and
   * skipped: the remaining namespaces and tokens still have to be taken care of, and the next run
   * tries again.
   */
  @ScheduleRate(
      initialDelayParameterName = REFRESH_PERIOD_SEC_PROPERTY,
      periodParameterName = REFRESH_PERIOD_SEC_PROPERTY,
      unit = SECONDS)
  public void refreshTokens() {
    if (!refreshEnabled) {
      return;
    }
    Map<String, Set<String>> repositoryUrlsByNamespace;
    try {
      repositoryUrlsByNamespace = getRepositoryUrlsOfRunningWorkspaces();
    } catch (InfrastructureException | KubernetesClientException e) {
      LOG.warn("Failed to read the running DevWorkspaces of the cluster", e);
      return;
    }
    LOG.debug(
        "Checking the OAuth tokens of {} namespace(s) with a running workspace",
        repositoryUrlsByNamespace.size());
    for (Map.Entry<String, Set<String>> namespace : repositoryUrlsByNamespace.entrySet()) {
      refreshTokens(namespace.getKey(), namespace.getValue());
    }
  }

  /**
   * Returns the URLs of the repositories the running workspaces of the cluster clone, keyed by the
   * namespace they run in. A namespace whose workspaces clone nothing is left out.
   */
  private Map<String, Set<String>> getRepositoryUrlsOfRunningWorkspaces()
      throws InfrastructureException {
    Map<String, Set<String>> repositoryUrlsByNamespace = new LinkedHashMap<>();
    for (GenericKubernetesResource devWorkspace : devWorkspaces.getRunningInAllNamespaces()) {
      Set<String> repositoryUrls = KubernetesDevWorkspaces.getRepositoryUrls(devWorkspace);
      if (repositoryUrls.isEmpty()) {
        continue;
      }
      String namespace =
          devWorkspace.getMetadata() == null ? null : devWorkspace.getMetadata().getNamespace();
      if (isNullOrEmpty(namespace)) {
        continue;
      }
      repositoryUrlsByNamespace
          .computeIfAbsent(namespace, name -> new LinkedHashSet<>())
          .addAll(repositoryUrls);
    }
    return repositoryUrlsByNamespace;
  }

  private void refreshTokens(String namespace, Set<String> repositoryUrls) {
    List<Secret> secrets;
    try {
      secrets =
          cheServerKubernetesClientFactory
              .create()
              .secrets()
              .inNamespace(namespace)
              .withLabels(SEARCH_LABELS)
              .list()
              .getItems();
    } catch (InfrastructureException | KubernetesClientException e) {
      LOG.warn("Failed to read the personal access token secrets of namespace {}", namespace, e);
      return;
    }

    // A user may have several secrets for the same SCM provider, e.g. the one that a previous
    // refresh failed to clean up. Refreshing the provider once brings all of them up to date.
    Set<String> refreshed = new HashSet<>();
    for (Secret secret : secrets) {
      Map<String, String> annotations = secret.getMetadata().getAnnotations();
      String scmServerUrl = annotations.get(ANNOTATION_SCM_URL);
      if (!serves(scmServerUrl, repositoryUrls)) {
        LOG.debug(
            "No workspace running in namespace {} clones from {}, skipping its OAuth token",
            namespace,
            scmServerUrl);
        continue;
      }
      // A namespace may be shared, so the owner of the token is read from the secret rather than
      // from the namespace.
      String userId = annotations.get(ANNOTATION_CHE_USERID);
      if (isNullOrEmpty(userId)) {
        LOG.warn(
            "Secret {} of namespace {} does not say which user its token belongs to. Skipping it.",
            secret.getMetadata().getName(),
            namespace);
        continue;
      }
      if (refreshed.add(userId + '\n' + scmServerUrl)) {
        refreshToken(namespace, userId, scmServerUrl);
      }
    }
  }

  /**
   * Refreshes the token of the given user for the given SCM provider, on behalf of that user.
   *
   * @param namespace the namespace the token secret was read from
   * @param userId the owner of the token, as annotated on its secret
   * @param scmServerUrl the SCM provider the token belongs to
   */
  private void refreshToken(String namespace, String userId, String scmServerUrl) {
    // The whole refresh flow, from the OAuth provider that holds the refresh token down to the
    // namespace the new token is written to, reads the EnvironmentContext, and the job runs on a
    // scheduler thread that has none. The id is what the OAuth token of the user is keyed by and
    // what their secrets are annotated with. The namespace is named outright, rather than left to
    // be resolved from the name of the user: the job already knows which namespace it is on, and
    // the only record of the name is the Che database, which a deployment that authenticates
    // through OIDC never writes a user to.
    EnvironmentContext originalContext = EnvironmentContext.getCurrent();
    try {
      EnvironmentContext context = new EnvironmentContext();
      context.setSubject(new SubjectImpl(null, Collections.emptyList(), userId, null, false));
      context.setNamespaceName(namespace);
      EnvironmentContext.setCurrent(context);

      LOG.debug(
          "Refreshing the OAuth token of user {} of namespace {} for {}",
          userId,
          namespace,
          scmServerUrl);
      personalAccessTokenManager.forceRefreshPersonalAccessToken(scmServerUrl);
    } catch (ScmUnauthorizedException
        | ScmCommunicationException
        | ScmConfigurationPersistenceException
        | UnknownScmProviderException
        | UnsatisfiedScmPreconditionException e) {
      // The user has to go through the OAuth flow again, which they are asked to do the next
      // time the token is read.
      LOG.warn(
          "Failed to refresh the OAuth token of user {} of namespace {} for {}",
          userId,
          namespace,
          scmServerUrl,
          e);
    } finally {
      EnvironmentContext.setCurrent(originalContext);
    }
  }

  /**
   * Tells whether the SCM provider the token belongs to serves any of the given repositories.
   *
   * <p>The provider is matched on its host, its port and, when it is hosted under a path of its
   * own, on that path being a prefix of the path of the repository. The token of {@code
   * https://gitlab.com} serves {@code https://gitlab.com/che/repo.git}, and the token of a GitLab
   * hosted under {@code https://example.com/gitlab} serves {@code
   * https://example.com/gitlab/che/repo.git} but not {@code https://example.com/other/repo.git}.
   *
   * <p>Only the repositories cloned over HTTP are considered, because those are the only ones git
   * reaches for the credentials of the token. A repository cloned over SSH is authenticated with
   * the SSH key of the user and keeps working with an expired OAuth token.
   */
  private static boolean serves(String scmServerUrl, Set<String> repositoryUrls) {
    URI scmUri = toUri(scmServerUrl);
    if (scmUri == null || scmUri.getHost() == null) {
      return false;
    }
    String scmPath = trimEnd(nullToEmpty(scmUri.getPath()), '/');
    for (String repositoryUrl : repositoryUrls) {
      URI repositoryUri = toUri(repositoryUrl);
      if (repositoryUri == null
          || repositoryUri.getHost() == null
          || !isHttp(repositoryUri)
          || !scmUri.getHost().equalsIgnoreCase(repositoryUri.getHost())
          || portOf(scmUri) != portOf(repositoryUri)) {
        continue;
      }
      String repositoryPath = nullToEmpty(repositoryUri.getPath());
      if (scmPath.isEmpty()
          || repositoryPath.equals(scmPath)
          || repositoryPath.startsWith(scmPath + "/")) {
        return true;
      }
    }
    return false;
  }

  private static URI toUri(String url) {
    if (isNullOrEmpty(url)) {
      return null;
    }
    try {
      return new URI(url.trim());
    } catch (URISyntaxException e) {
      // A remote git cannot reach with the credentials of the token, e.g. the scp-like
      // git@github.com:che/repo.git form of an SSH remote.
      LOG.debug("Cannot parse {} as a URL", url, e);
      return null;
    }
  }

  private static boolean isHttp(URI uri) {
    String scheme = uri.getScheme();
    return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
  }

  /** The port of the URI, resolved to the default port of its scheme when it declares none. */
  private static int portOf(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }
    return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
  }
}
