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
import static org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher.OAUTH_2_PREFIX;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.net.URI;
import java.net.URISyntaxException;
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
import org.eclipse.che.commons.schedule.ScheduleRate;
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
 * OAuth tokens its workspaces use. Setting {@value #REFRESH_ENABLED_PROPERTY} to {@code false}
 * turns the job off.
 *
 * <p>A token is refreshed whether or not it has expired: the job does not compare its expiration
 * time against a clock. The token is used by the workspace pod, which does not share a clock with
 * the pod this job runs in, so an expiration computed here may well be wrong, and a token taken for
 * valid is one the workspace is already failing to push with.
 *
 * <p>Everything the job needs is read from the cluster, so it covers every user from the first run
 * on, with no state to lose across a Che server restart. It deliberately does not hook into {@link
 * org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator.NamespaceConfigurator}:
 * those run only when Che itself provisions a namespace, which a deployment that has its namespaces
 * prepared for it never does.
 *
 * <p>A token is only refreshed once the user its secret names is the one single user known to run a
 * workspace in the namespace that secret sits in, see {@link #owns(String, RunningWorkspaces)}. The
 * job acts as that user and writes the refreshed token back into that namespace, and both come from
 * a secret that anyone who can write to the namespace can write.
 *
 * <p>Only the tokens obtained with the OAuth flow are refreshed. A manually configured personal
 * access token is left alone: it does not expire on its own, and it is the one preferred over an
 * OAuth token of the same provider, so refreshing it would overwrite the git credentials of the
 * user with a token they did not ask to be used.
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
   * How often, in seconds, the tokens of the running workspaces are refreshed, {@code
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
  private static final String ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME =
      "che.eclipse.org/scm-personal-access-token-name";

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
   * Refreshes the OAuth tokens of every namespace that holds a running workspace.
   *
   * <p>Every token a running workspace uses is refreshed, without first asking whether it has
   * expired. The workspace pod that holds the token does not share a clock with the pod this job
   * runs in, so an expiration worked out here is not one to leave a workspace to run on.
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
    Map<String, RunningWorkspaces> runningByNamespace;
    try {
      runningByNamespace = getRunningWorkspacesByNamespace();
    } catch (InfrastructureException | KubernetesClientException e) {
      LOG.warn("Failed to read the running DevWorkspaces of the cluster", e);
      return;
    }
    LOG.debug(
        "Checking the OAuth tokens of {} namespace(s) with a running workspace",
        runningByNamespace.size());
    for (Map.Entry<String, RunningWorkspaces> namespace : runningByNamespace.entrySet()) {
      refreshTokens(namespace.getKey(), namespace.getValue());
    }
  }

  /**
   * Returns what the running workspaces of the cluster say about the namespace they run in, keyed
   * by that namespace. A namespace whose workspaces clone nothing is left out, there is no token of
   * it to keep alive.
   */
  private Map<String, RunningWorkspaces> getRunningWorkspacesByNamespace()
      throws InfrastructureException {
    Map<String, RunningWorkspaces> runningByNamespace = new LinkedHashMap<>();
    for (GenericKubernetesResource devWorkspace : devWorkspaces.getRunningInAllNamespaces()) {
      String namespace =
          devWorkspace.getMetadata() == null ? null : devWorkspace.getMetadata().getNamespace();
      if (isNullOrEmpty(namespace)) {
        continue;
      }
      RunningWorkspaces running =
          runningByNamespace.computeIfAbsent(namespace, name -> new RunningWorkspaces());
      // The creator of a workspace that clones nothing still says who runs in the namespace.
      KubernetesDevWorkspaces.getCreator(devWorkspace).ifPresent(running.creatorIds::add);
      running.repositoryUrls.addAll(KubernetesDevWorkspaces.getRepositoryUrls(devWorkspace));
    }
    runningByNamespace.values().removeIf(running -> running.repositoryUrls.isEmpty());
    return runningByNamespace;
  }

  private void refreshTokens(String namespace, RunningWorkspaces running) {
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
      if (annotations == null) {
        continue;
      }
      String scmServerUrl = annotations.get(ANNOTATION_SCM_URL);
      if (!isOAuthTokenSecret(annotations)) {
        LOG.debug(
            "Secret {} of namespace {} keeps a manually configured personal access token, skipping"
                + " it",
            secret.getMetadata().getName(),
            namespace);
        continue;
      }
      if (!serves(scmServerUrl, running.repositoryUrls)) {
        LOG.debug(
            "No workspace running in namespace {} clones from {}, skipping its OAuth token",
            namespace,
            scmServerUrl);
        continue;
      }
      // A namespace may be shared, so the owner of the token is read from the secret rather than
      // from the namespace. The secret is written by whoever can write to the namespace, so what
      // it claims is only taken once the namespace is known to be that user's, see #owns.
      String userId = annotations.get(ANNOTATION_CHE_USERID);
      if (isNullOrEmpty(userId)) {
        LOG.warn(
            "Secret {} of namespace {} does not say which user its token belongs to. Skipping it.",
            secret.getMetadata().getName(),
            namespace);
        continue;
      }
      if (!owns(userId, running)) {
        LOG.warn(
            "Secret {} of namespace {} claims to hold the token of user {}, who is not the only"
                + " user known to run a workspace in that namespace. Skipping it.",
            secret.getMetadata().getName(),
            namespace,
            userId);
        continue;
      }
      if (refreshed.add(userId + '\n' + trimEnd(scmServerUrl, '/'))) {
        refreshToken(namespace, userId, scmServerUrl);
      }
    }
  }

  /**
   * Tells whether the given user is the only user of the given namespace.
   *
   * <p>The job acts as the user a secret names, and writes the refreshed token into the namespace
   * that secret was found in. Both are taken from a secret that anyone who can write to the
   * namespace can write, and a user can write to their own. Without this check, a user who
   * annotates a secret of a namespace with the id of another user has the job refresh that user's
   * token, from the refresh token Che holds for them, and store the result where the first user can
   * read it. Ids are not secret: a shared namespace carries the ids of its users on their secrets.
   *
   * <p>So a token is only refreshed into a namespace that one single user runs workspaces in, as
   * recorded by the {@value KubernetesDevWorkspaces#CREATOR_LABEL} label the DevWorkspace Operator
   * stamps them with and puts back when it is edited away. That user is the only one the namespace
   * can belong to, and a secret that names anybody else is a secret somebody else wrote.
   *
   * <p>A namespace that several users run a workspace in therefore has none of its tokens
   * refreshed, and says so in the log of every run. Telling the owner of such a namespace from a
   * user who claims the id of one would take a record of who the namespace was handed to, which a
   * deployment that has its namespaces prepared for it never writes. Refreshing on a claim alone
   * would hand every user of the namespace the tokens of all the others.
   *
   * <p>Equally refused is a namespace whose running workspaces carry no creator at all, which is
   * what an identity without a {@code uid} leaves behind, commonly an OIDC one. Nothing then says
   * who the namespace belongs to.
   *
   * @param userId the user the secret claims the token belongs to
   * @param running what the workspaces running in that namespace say about it
   */
  private boolean owns(String userId, RunningWorkspaces running) {
    return running.creatorIds.size() == 1 && running.creatorIds.contains(userId);
  }

  /** What the DevWorkspaces running in a namespace say about it. */
  private static final class RunningWorkspaces {

    /** The URLs of the repositories they clone. */
    private final Set<String> repositoryUrls = new LinkedHashSet<>();

    /**
     * The ids of the users who created them, as recorded by the DevWorkspace Operator. Empty when
     * it recorded none, see {@link KubernetesDevWorkspaces#CREATOR_LABEL}.
     */
    private final Set<String> creatorIds = new LinkedHashSet<>();
  }

  /**
   * Tells whether the secret keeps a token obtained with the OAuth flow. Such tokens are stored
   * under a generated {@code oauth2-<random>} name, while the manually configured personal access
   * tokens are named after the SCM provider.
   *
   * <p>Only the OAuth tokens are refreshed. A manually configured personal access token has no
   * refresh token behind it and does not expire on its own, so refreshing it means going to the
   * OAuth provider of the user, which either fails, when they never went through the OAuth flow, or
   * overwrites their git credentials with an OAuth token, although the manually configured token is
   * the one preferred over it, see {@code KubernetesPersonalAccessTokenManager}.
   *
   * @param annotations the annotations of the token secret
   */
  private static boolean isOAuthTokenSecret(Map<String, String> annotations) {
    String tokenName = annotations.get(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME);
    return tokenName != null && tokenName.startsWith(OAUTH_2_PREFIX);
  }

  /**
   * Refreshes the token of the given user for the given SCM provider, on behalf of that user.
   *
   * @param namespace the namespace the token secret was read from
   * @param userId the owner of the token, as annotated on its secret
   * @param scmServerUrl the SCM provider the token belongs to
   */
  private void refreshToken(String namespace, String userId, String scmServerUrl) {
    try {
      LOG.debug(
          "Refreshing the OAuth token of user {} of namespace {} for {}",
          userId,
          namespace,
          scmServerUrl);
      // The user and the namespace are named outright. The job runs on a scheduler thread that
      // carries no EnvironmentContext to resolve them from, and the namespace could not be
      // resolved from the user anyway: the only record of the name of a user is the Che database,
      // which a deployment that authenticates through OIDC never writes a user to. That the
      // namespace is this user's is settled by #owns before the call.
      personalAccessTokenManager.forceRefreshPersonalAccessToken(scmServerUrl, userId, namespace);
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
    } catch (RuntimeException e) {
      // One user must not stop the refresh of the remaining namespaces.
      LOG.warn(
          "Unexpected failure refreshing the OAuth token of user {} of namespace {} for {}",
          userId,
          namespace,
          scmServerUrl,
          e);
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
