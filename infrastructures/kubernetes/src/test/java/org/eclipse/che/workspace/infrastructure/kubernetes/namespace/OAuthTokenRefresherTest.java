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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.devworkspace.KubernetesDevWorkspaces;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class OAuthTokenRefresherTest {

  private static final String TEST_NAMESPACE_NAME = "namespace123";
  private static final String OTHER_NAMESPACE_NAME = "namespace456";
  private static final String TEST_USER_ID = "user123";
  private static final String SCM_URL = "https://gitlab.com";
  private static final long REFRESH_PERIOD_SEC = 600;

  private static final Map<String, String> SECRET_LABELS =
      ImmutableMap.of(
          "app.kubernetes.io/part-of", "che.eclipse.org",
          "app.kubernetes.io/component", "scm-personal-access-token");

  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private KubernetesDevWorkspaces devWorkspaces;

  private KubernetesMockServer kubernetesMockServer;
  private KubernetesClient kubernetesClient;

  private OAuthTokenRefresher refresher;

  @BeforeMethod
  public void setUp() throws Exception {
    final Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
    kubernetesMockServer =
        new KubernetesMockServer(
            new Context(),
            new MockWebServer(),
            responses,
            new KubernetesMixedDispatcher(responses),
            true);
    kubernetesMockServer.init();
    kubernetesClient = kubernetesMockServer.createClient();
    // a run that finds no running workspace never reaches for a client
    lenient().when(cheServerKubernetesClientFactory.create()).thenReturn(kubernetesClient);
    // unless stated otherwise, one workspace runs on the SCM of the tokens
    lenient()
        .when(devWorkspaces.getRunningInAllNamespaces())
        .thenReturn(List.of(runningDevWorkspace(TEST_NAMESPACE_NAME, SCM_URL + "/che/repo.git")));

    refresher = refresher(true, REFRESH_PERIOD_SEC);
  }

  private OAuthTokenRefresher refresher(boolean enabled, long periodSec) {
    return new OAuthTokenRefresher(
        cheServerKubernetesClientFactory,
        personalAccessTokenManager,
        devWorkspaces,
        enabled,
        periodSec);
  }

  @AfterMethod
  public void cleanUp() {
    kubernetesMockServer.destroy();
    EnvironmentContext.reset();
  }

  /** Nothing is registered up front, the namespaces come from the running workspaces. */
  @Test
  public void shouldRefreshExpiredOAuthTokenWithoutAnyNamespaceBeingConfigured() throws Exception {
    // given
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verify(personalAccessTokenManager).forceRefreshPersonalAccessToken(SCM_URL);
  }

  /** {@code CHE_OAUTH_TOKEN__REFRESH_ENABLED=false} keeps the job from doing anything. */
  @Test
  public void shouldDoNothingWhenTheRefreshIsDisabled() throws Exception {
    // given
    refresher = refresher(false, REFRESH_PERIOD_SEC);
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verify(devWorkspaces, never()).getRunningInAllNamespaces();
    verifyNoInteractions(personalAccessTokenManager);
  }

  @Test
  public void shouldDoNothingWhenNoWorkspaceIsRunning() throws Exception {
    // given
    when(devWorkspaces.getRunningInAllNamespaces()).thenReturn(List.of());
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verifyNoInteractions(personalAccessTokenManager);
  }

  @Test
  public void shouldDoNothingWhenTheDevWorkspacesCannotBeRead() throws Exception {
    // given
    when(devWorkspaces.getRunningInAllNamespaces())
        .thenThrow(new InfrastructureException("forbidden"));
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verifyNoInteractions(personalAccessTokenManager);
  }

  /** The secrets of a namespace whose workspaces are all stopped are not even read. */
  @Test
  public void shouldOnlyReadTheNamespacesThatRunAWorkspace() throws Exception {
    // given
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 7200));
    when(devWorkspaces.getRunningInAllNamespaces())
        .thenReturn(List.of(runningDevWorkspace(OTHER_NAMESPACE_NAME, SCM_URL + "/che/repo.git")));

    // when
    refresher.refreshTokens();

    // then
    verifyNoInteractions(personalAccessTokenManager);
  }

  @Test
  public void shouldRefreshTheTokensOfEveryNamespaceThatRunsAWorkspace() throws Exception {
    // given
    createSecret(
        TEST_NAMESPACE_NAME, "oauth2-first", oAuthTokenSecret("oauth2-first", SCM_URL, 3600, 7200));
    createSecret(
        OTHER_NAMESPACE_NAME,
        "oauth2-second",
        oAuthTokenSecret("oauth2-second", "https://github.com", 3600, 7200));
    when(devWorkspaces.getRunningInAllNamespaces())
        .thenReturn(
            List.of(
                runningDevWorkspace(TEST_NAMESPACE_NAME, SCM_URL + "/che/repo.git"),
                runningDevWorkspace(OTHER_NAMESPACE_NAME, "https://github.com/che/repo.git")));

    // when
    refresher.refreshTokens();

    // then
    verify(personalAccessTokenManager).forceRefreshPersonalAccessToken(SCM_URL);
    verify(personalAccessTokenManager).forceRefreshPersonalAccessToken("https://github.com");
  }

  /** The owner of the token is read from the secret, not from the namespace. */
  @Test
  public void shouldRefreshOnBehalfOfTheOwnerOfTheSecret() throws Exception {
    // given
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 1800));
    AtomicReference<Subject> subjectDuringRefresh = new AtomicReference<>();
    AtomicReference<String> namespaceDuringRefresh = new AtomicReference<>();
    doAnswerCapturingSubject(subjectDuringRefresh, namespaceDuringRefresh);

    // when
    refresher.refreshTokens();

    // then
    assertEquals(subjectDuringRefresh.get().getUserId(), TEST_USER_ID);
    // The name is left out: the namespace is named outright instead of resolved from it.
    assertNull(subjectDuringRefresh.get().getUserName());
    assertEquals(namespaceDuringRefresh.get(), TEST_NAMESPACE_NAME);
  }

  @Test
  public void shouldNotRefreshTokenOfASecretThatNamesNoUser() throws Exception {
    // given
    Secret secret = oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 7200);
    secret.getMetadata().getAnnotations().remove("che.eclipse.org/che-userid");
    createSecret("oauth2-expired", secret);

    // when
    refresher.refreshTokens();

    // then
    verify(personalAccessTokenManager, never()).forceRefreshPersonalAccessToken(anyString());
  }

  @Test
  public void shouldRestoreTheEnvironmentContextAfterTheRun() throws Exception {
    // given
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 1800));
    EnvironmentContext original = EnvironmentContext.getCurrent();

    // when
    refresher.refreshTokens();

    // then
    assertEquals(EnvironmentContext.getCurrent(), original);
  }

  @Test
  public void shouldRefreshEachScmProviderOnlyOnce() throws Exception {
    // given two expired secrets of the same provider, e.g. one left behind by a failed cleanup
    createSecret("oauth2-first", oAuthTokenSecret("oauth2-first", SCM_URL, 7200, 3600));
    createSecret("oauth2-second", oAuthTokenSecret("oauth2-second", SCM_URL, 3600, 1800));

    // when
    refresher.refreshTokens();

    // then
    verify(personalAccessTokenManager).forceRefreshPersonalAccessToken(SCM_URL);
  }

  /** The running workspace clones from another provider, this token is not the one it uses. */
  @Test
  public void shouldNotRefreshTokenOfAnScmNoRunningWorkspaceClonesFrom() throws Exception {
    // given
    when(devWorkspaces.getRunningInAllNamespaces())
        .thenReturn(
            List.of(runningDevWorkspace(TEST_NAMESPACE_NAME, "https://github.com/che/repo.git")));
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verifyNoInteractions(personalAccessTokenManager);
  }

  /** Only the token of the provider the running workspace clones from is refreshed. */
  @Test
  public void shouldRefreshOnlyTheTokenOfTheScmOfTheRunningWorkspace() throws Exception {
    // given
    createSecret("oauth2-gitlab", oAuthTokenSecret("oauth2-gitlab", SCM_URL, 3600, 7200));
    createSecret(
        "oauth2-github", oAuthTokenSecret("oauth2-github", "https://github.com", 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verify(personalAccessTokenManager).forceRefreshPersonalAccessToken(SCM_URL);
    verify(personalAccessTokenManager, never())
        .forceRefreshPersonalAccessToken("https://github.com");
  }

  /** A provider hosted under a path of its own only serves the repositories below that path. */
  @Test
  public void shouldMatchTheScmHostedUnderAPath() throws Exception {
    // given
    String hostedScmUrl = "https://example.com/gitlab";
    when(devWorkspaces.getRunningInAllNamespaces())
        .thenReturn(
            List.of(runningDevWorkspace(TEST_NAMESPACE_NAME, hostedScmUrl + "/che/repo.git")));
    createSecret("oauth2-hosted", oAuthTokenSecret("oauth2-hosted", hostedScmUrl, 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verify(personalAccessTokenManager).forceRefreshPersonalAccessToken(hostedScmUrl);
  }

  @Test
  public void shouldNotMatchAnotherScmOnTheHostOfTheScmHostedUnderAPath() throws Exception {
    // given
    String hostedScmUrl = "https://example.com/gitlab";
    when(devWorkspaces.getRunningInAllNamespaces())
        .thenReturn(
            List.of(
                runningDevWorkspace(
                    TEST_NAMESPACE_NAME, "https://example.com/gitea/che/repo.git")));
    createSecret("oauth2-hosted", oAuthTokenSecret("oauth2-hosted", hostedScmUrl, 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verifyNoInteractions(personalAccessTokenManager);
  }

  /** A repository cloned over SSH is authenticated with the SSH key, not with the OAuth token. */
  @Test
  public void shouldNotRefreshTokenForARepositoryClonedOverSsh() throws Exception {
    // given
    when(devWorkspaces.getRunningInAllNamespaces())
        .thenReturn(
            List.of(runningDevWorkspace(TEST_NAMESPACE_NAME, "git@gitlab.com:che/repo.git")));
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL, 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verifyNoInteractions(personalAccessTokenManager);
  }

  /** The SCM URL of the token carries a trailing slash, the remote does not. */
  @Test
  public void shouldMatchRegardlessOfATrailingSlash() throws Exception {
    // given
    createSecret("oauth2-expired", oAuthTokenSecret("oauth2-expired", SCM_URL + "/", 3600, 7200));

    // when
    refresher.refreshTokens();

    // then
    verify(personalAccessTokenManager).forceRefreshPersonalAccessToken(SCM_URL + "/");
  }

  private void doAnswerCapturingSubject(AtomicReference<Subject> holder) throws Exception {
    doAnswerCapturingSubject(holder, new AtomicReference<>());
  }

  private void doAnswerCapturingSubject(
      AtomicReference<Subject> holder, AtomicReference<String> namespaceHolder) throws Exception {
    doAnswer(
            invocation -> {
              holder.set(EnvironmentContext.getCurrent().getSubject());
              namespaceHolder.set(EnvironmentContext.getCurrent().getNamespaceName());
              return null;
            })
        .when(personalAccessTokenManager)
        .forceRefreshPersonalAccessToken(anyString());
  }

  /** A running DevWorkspace whose devfile declares a single project cloned from the given URL. */
  private static GenericKubernetesResource runningDevWorkspace(
      String namespace, String repositoryUrl) {
    GenericKubernetesResource devWorkspace = new GenericKubernetesResource();
    devWorkspace.setMetadata(
        new ObjectMetaBuilder().withName("workspace").withNamespace(namespace).build());
    devWorkspace.setAdditionalProperty("status", Map.of("phase", "Running"));
    devWorkspace.setAdditionalProperty(
        "spec",
        Map.of(
            "template",
            Map.of(
                "projects",
                List.of(
                    Map.of(
                        "name",
                        "project",
                        "git",
                        Map.of("remotes", Map.of("origin", repositoryUrl)))))));
    return devWorkspace;
  }

  private void createSecret(String name, Secret secret) {
    createSecret(TEST_NAMESPACE_NAME, name, secret);
  }

  private void createSecret(String namespace, String name, Secret secret) {
    secret.getMetadata().setName(name);
    secret.getMetadata().setNamespace(namespace);
    kubernetesClient.secrets().inNamespace(namespace).create(secret);
  }

  /**
   * Builds a token secret as {@code KubernetesPersonalAccessTokenManager} stores it.
   *
   * @param tokenName the {@code scm-personal-access-token-name} annotation, {@code oauth2-}
   *     prefixed for the tokens obtained with the OAuth flow
   * @param createdSecondsAgo how long ago the token was issued
   * @param expiresInSeconds how long the token lives from the moment it was issued
   */
  private Secret oAuthTokenSecret(
      String tokenName, String scmUrl, long createdSecondsAgo, long expiresInSeconds) {
    Map<String, String> annotations = new HashMap<>();
    annotations.put("che.eclipse.org/che-userid", TEST_USER_ID);
    annotations.put("che.eclipse.org/scm-url", scmUrl);
    annotations.put("che.eclipse.org/scm-provider-name", "gitlab");
    annotations.put("che.eclipse.org/scm-personal-access-token-name", tokenName);
    annotations.put("che.eclipse.org/scm-token-expires-in", String.valueOf(expiresInSeconds));

    Map<String, String> data = new HashMap<>();
    data.put("token", encode("token-value"));
    data.put("refresh-token", encode("refresh-token-value"));

    return new SecretBuilder()
        .withNewMetadata()
        .withNamespace(TEST_NAMESPACE_NAME)
        .withLabels(SECRET_LABELS)
        .withAnnotations(annotations)
        .withCreationTimestamp(
            Instant.now()
                .minusSeconds(createdSecondsAgo)
                .truncatedTo(ChronoUnit.SECONDS)
                .toString())
        .endMetadata()
        .withData(data)
        .build();
  }

  private static String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
