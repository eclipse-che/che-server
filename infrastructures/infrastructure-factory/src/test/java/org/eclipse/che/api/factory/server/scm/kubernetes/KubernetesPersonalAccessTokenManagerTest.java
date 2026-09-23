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
package org.eclipse.che.api.factory.server.scm.kubernetes;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.eclipse.che.api.factory.server.scm.kubernetes.KubernetesPersonalAccessTokenManager.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.ScmPersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.server.impls.KubernetesNamespaceMetaImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespace;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesSecrets;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesPersonalAccessTokenManagerTest {

  @Mock private KubernetesNamespaceFactory namespaceFactory;
  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  @Mock private ScmPersonalAccessTokenFetcher scmPersonalAccessTokenFetcher;

  @Mock private KubernetesClient kubeClient;
  @Mock private GitCredentialManager gitCredentialManager;

  @Mock private MixedOperation<Secret, SecretList, Resource<Secret>> secretsMixedOperation;

  @Mock NonNamespaceOperation<Secret, SecretList, Resource<Secret>> nonNamespaceOperation;

  KubernetesPersonalAccessTokenManager personalAccessTokenManager;

  @BeforeMethod
  protected void init() {
    personalAccessTokenManager =
        new KubernetesPersonalAccessTokenManager(
            namespaceFactory,
            cheServerKubernetesClientFactory,
            scmPersonalAccessTokenFetcher,
            gitCredentialManager);
    assertNotNull(this.personalAccessTokenManager);
  }

  @Test
  public void shouldTrimBlankCharsInToken() throws Exception {
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));

    Map<String, String> data =
        Map.of("token", Base64.getEncoder().encodeToString(" token_value \n".getBytes(UTF_8)));

    ObjectMeta meta1 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user",
                    ANNOTATION_SCM_URL,
                    "http://host1"))
            .build();

    Secret secret = new SecretBuilder().withMetadata(meta1).withData(data).build();

    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(secret));

    // when
    PersonalAccessToken token =
        personalAccessTokenManager
            .get(
                new SubjectImpl("user", Collections.emptyList(), "user", "t1", false),
                null,
                "http://host1",
                null)
            .get();

    // then
    assertEquals(token.getToken(), "token_value");
  }

  @Test
  public void testSavingOfPersonalAccessToken() throws Exception {

    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));

    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);

    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://bitbucket.com",
            "provider",
            "cheUser",
            null,
            "username",
            "token-name",
            "tid-24",
            "token123",
            "refresh123",
            3600);

    // when
    personalAccessTokenManager.store(token);

    // then
    verify(nonNamespaceOperation).createOrReplace(captor.capture());
    Secret createdSecret = captor.getValue();
    assertNotNull(createdSecret);
    assertTrue(
        createdSecret
            .getMetadata()
            .getName()
            .startsWith(KubernetesPersonalAccessTokenManager.NAME_PATTERN));
    assertEquals(
        createdSecret.getData().get("token"),
        Base64.getEncoder().encodeToString(token.getToken().getBytes()));
  }

  @Test
  public void testGetTokenFromNamespace() throws Exception {

    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));

    Map<String, String> data1 =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));
    Map<String, String> data2 =
        Map.of("token", Base64.getEncoder().encodeToString("token2".getBytes(UTF_8)));
    Map<String, String> data3 =
        Map.of("token", Base64.getEncoder().encodeToString("token3".getBytes(UTF_8)));

    ObjectMeta meta1 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1"))
            .build();
    ObjectMeta meta2 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-02T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host2"))
            .build();
    ObjectMeta meta3 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-03T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user2",
                    ANNOTATION_SCM_URL,
                    "http://host3"))
            .build();

    Secret secret1 = new SecretBuilder().withMetadata(meta1).withData(data1).build();
    Secret secret2 = new SecretBuilder().withMetadata(meta2).withData(data2).build();
    Secret secret3 = new SecretBuilder().withMetadata(meta3).withData(data3).build();

    when(secrets.get(any(LabelSelector.class)))
        .thenReturn(Arrays.asList(secret1, secret2, secret3));

    // when
    PersonalAccessToken token =
        personalAccessTokenManager
            .get(
                new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
                null,
                "http://host1",
                null)
            .get();

    // then
    assertEquals(token.getCheUserId(), "user1");
    assertEquals(token.getScmProviderUrl(), "http://host1");
    assertEquals(token.getToken(), "token1");
  }

  @Test
  public void shouldGetTokenFromASecretWithSCMUsername() throws Exception {

    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));

    Map<String, String> data =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));

    ObjectMeta metaData =
        new ObjectMetaBuilder()
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    "che.eclipse.org/scm-username",
                    "scm-username"))
            .build();

    Secret secret = new SecretBuilder().withMetadata(metaData).withData(data).build();

    when(secrets.get(any(LabelSelector.class))).thenReturn(singletonList(secret));

    // when
    Optional<PersonalAccessToken> tokenOptional =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);
    // then
    assertTrue(tokenOptional.isPresent());
    assertEquals(tokenOptional.get().getCheUserId(), "user1");
    assertEquals(tokenOptional.get().getScmProviderUrl(), "http://host1");
    assertEquals(tokenOptional.get().getToken(), "token1");
  }

  @Test
  public void shouldGetTokenFromASecretWithoutSCMUsername() throws Exception {

    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));

    Map<String, String> data =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));

    ObjectMeta metaData =
        new ObjectMetaBuilder()
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1"))
            .build();

    Secret secret = new SecretBuilder().withMetadata(metaData).withData(data).build();

    when(secrets.get(any(LabelSelector.class))).thenReturn(singletonList(secret));

    // when
    Optional<PersonalAccessToken> tokenOptional =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(tokenOptional.isPresent());
    assertEquals(tokenOptional.get().getCheUserId(), "user1");
    assertEquals(tokenOptional.get().getScmProviderUrl(), "http://host1");
    assertEquals(tokenOptional.get().getToken(), "token1");
  }

  @Test
  public void testGetTokenFromNamespaceWithTrailingSlashMismatch() throws Exception {

    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));

    Map<String, String> data1 =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));
    Map<String, String> data2 =
        Map.of("token", Base64.getEncoder().encodeToString("token2".getBytes(UTF_8)));

    ObjectMeta meta1 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1.com/"))
            .build();
    ObjectMeta meta2 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-08-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host2.com"))
            .build();

    Secret secret1 = new SecretBuilder().withMetadata(meta1).withData(data1).build();
    Secret secret2 = new SecretBuilder().withMetadata(meta2).withData(data2).build();

    when(secrets.get(any(LabelSelector.class))).thenReturn(Arrays.asList(secret1, secret2));

    // when
    PersonalAccessToken token1 =
        personalAccessTokenManager
            .get(
                new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
                null,
                "http://host1.com",
                null)
            .get();
    PersonalAccessToken token2 =
        personalAccessTokenManager
            .get(
                new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
                null,
                "http://host2.com/",
                null)
            .get();

    // then
    assertNotNull(token1);
    assertNotNull(token2);
  }

  @Test
  public void shouldDeleteMisconfiguredTokensOnGet() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    Map<String, String> data1 =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));
    ObjectMeta meta1 =
        new ObjectMetaBuilder()
            .withNamespace("test")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1"))
            .build();
    Secret secret1 = new SecretBuilder().withMetadata(meta1).withData(data1).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(Arrays.asList(secret1));
    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);
    // then
    assertFalse(token.isPresent());
    verify(nonNamespaceOperation, times(1)).delete(eq(secret1));
  }

  @Test
  public void shouldDeleteInvalidTokensOnGet() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.empty());
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    Map<String, String> data1 =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));
    ObjectMeta meta1 =
        new ObjectMetaBuilder()
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1"))
            .build();
    Secret secret1 = new SecretBuilder().withMetadata(meta1).withData(data1).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(Arrays.asList(secret1));
    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);
    // then
    assertFalse(token.isPresent());
    verify(nonNamespaceOperation, times(1)).delete(eq(secret1));
  }

  @Test(dependsOnMethods = "shouldDeleteInvalidTokensOnGet")
  public void shouldReturnFirstValidTokenAndDeleteTheInvalidOne() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenAnswer(
            (Answer<Optional<String>>)
                invocation -> {
                  PersonalAccessTokenParams params = invocation.getArgument(0);
                  return "id2".equals(params.getScmTokenId())
                      ? Optional.of("user")
                      : Optional.empty();
                });
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);

    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    Map<String, String> data1 =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));
    Map<String, String> data2 =
        Map.of("token", Base64.getEncoder().encodeToString("token2".getBytes(UTF_8)));
    ObjectMeta meta1 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "id1"))
            .build();
    ObjectMeta meta2 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-02T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "id2"))
            .build();
    Secret secret1 = new SecretBuilder().withMetadata(meta1).withData(data1).build();
    Secret secret2 = new SecretBuilder().withMetadata(meta2).withData(data2).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(Arrays.asList(secret1, secret2));
    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);
    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getScmTokenId(), "id2");
    verify(nonNamespaceOperation, times(1)).delete(eq(secret1));
  }

  @Test
  public void shouldReturnFirstValidTokenAndDeleteTheOlderOne() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    Map<String, String> data1 =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));
    Map<String, String> data2 =
        Map.of("token", Base64.getEncoder().encodeToString("token2".getBytes(UTF_8)));
    ObjectMeta meta1 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "oauth2-abcde",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "id1"))
            .build();
    ObjectMeta meta2 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-02T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "oauth2-fghij",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "id2"))
            .build();
    Secret secret1 = new SecretBuilder().withMetadata(meta1).withData(data1).build();
    Secret secret2 = new SecretBuilder().withMetadata(meta2).withData(data2).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(Arrays.asList(secret1, secret2));
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    PersonalAccessToken token =
        new PersonalAccessToken(
            "http://host1",
            "provider",
            "cheUser",
            null,
            "username",
            "token-name",
            "tid-24",
            "token123",
            "refresh123",
            3600);
    when(scmPersonalAccessTokenFetcher.refreshPersonalAccessToken(
            any(org.eclipse.che.commons.subject.Subject.class), eq("http://host1")))
        .thenReturn(token);

    // when
    personalAccessTokenManager.forceRefreshPersonalAccessToken("http://host1");

    // then
    verify(nonNamespaceOperation, times(1)).delete(eq(secret1));
  }

  @Test
  public void shouldPreferPersonalAccessTokenOverOAuthToken() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));
    Map<String, String> patData =
        Map.of("token", Base64.getEncoder().encodeToString("pat-token".getBytes(UTF_8)));
    Map<String, String> oauthData =
        Map.of("token", Base64.getEncoder().encodeToString("oauth-token".getBytes(UTF_8)));
    // the personal access token secret is the older one
    ObjectMeta patMeta =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "gitlab",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "pat-id"))
            .build();
    ObjectMeta oauthMeta =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-02T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "oauth2-abcde",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "oauth-id"))
            .build();
    Secret patSecret = new SecretBuilder().withMetadata(patMeta).withData(patData).build();
    Secret oauthSecret = new SecretBuilder().withMetadata(oauthMeta).withData(oauthData).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(Arrays.asList(patSecret, oauthSecret));

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getScmTokenId(), "pat-id");
    assertEquals(token.get().getToken(), "pat-token");
  }

  @Test
  public void shouldKeepPersonalAccessTokenSecretOnForceRefresh() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    Map<String, String> patData =
        Map.of("token", Base64.getEncoder().encodeToString("pat-token".getBytes(UTF_8)));
    Map<String, String> oauthData =
        Map.of("token", Base64.getEncoder().encodeToString("oauth-token".getBytes(UTF_8)));
    ObjectMeta patMeta =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "gitlab",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "pat-id"))
            .build();
    ObjectMeta oauthMeta =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-02T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "oauth2-abcde",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "oauth-id"))
            .build();
    Secret patSecret = new SecretBuilder().withMetadata(patMeta).withData(patData).build();
    Secret oauthSecret = new SecretBuilder().withMetadata(oauthMeta).withData(oauthData).build();
    // the newly stored token is the first one, the rest are the candidates for the cleanup
    when(secrets.get(any(LabelSelector.class))).thenReturn(Arrays.asList(patSecret, oauthSecret));
    PersonalAccessToken token =
        new PersonalAccessToken(
            "http://host1",
            "gitlab",
            "user1",
            null,
            "user",
            "oauth2-fghij",
            "new-oauth-id",
            "new-oauth-token",
            "refresh-token",
            3600);
    when(scmPersonalAccessTokenFetcher.refreshPersonalAccessToken(
            any(Subject.class), eq("http://host1")))
        .thenReturn(token);

    // when
    personalAccessTokenManager.forceRefreshPersonalAccessToken("http://host1");

    // then
    verify(nonNamespaceOperation, never()).delete(eq(patSecret));
  }

  @Test
  public void shouldRefreshExpiredOAuthToken() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    // the token was issued for an hour back in 2021, so it is long expired by now
    ObjectMeta oauthMeta =
        new ObjectMetaBuilder()
            .withName("personal-access-token-old")
            .withCreationTimestamp("2021-07-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "oauth2-abcde",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PROVIDER_NAME,
                    "gitlab",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "oauth-id",
                    ANNOTATION_SCM_TOKEN_EXPIRES_IN,
                    "3600"))
            .build();
    Secret oauthSecret =
        new SecretBuilder()
            .withMetadata(oauthMeta)
            .withData(
                Map.of(
                    "token", Base64.getEncoder().encodeToString("expired-token".getBytes(UTF_8))))
            .build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(oauthSecret));
    PersonalAccessToken refreshedToken =
        new PersonalAccessToken(
            "http://host1",
            "gitlab",
            "user1",
            null,
            "user",
            "oauth2-fghij",
            "new-oauth-id",
            "new-oauth-token",
            "new-refresh-token",
            3600);
    when(scmPersonalAccessTokenFetcher.refreshPersonalAccessToken(
            any(Subject.class), eq("http://host1")))
        .thenReturn(refreshedToken);

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getToken(), "new-oauth-token");
    // the refreshed token is stored and the outdated secret is removed
    verify(nonNamespaceOperation, times(1)).createOrReplace(any(Secret.class));
    verify(nonNamespaceOperation, times(1)).delete(eq(oauthSecret));
    // there is no point in validating the token that is known to be expired
    verify(scmPersonalAccessTokenFetcher, never())
        .getScmUsername(any(PersonalAccessTokenParams.class));
  }

  @Test
  public void shouldNotRefreshOAuthTokenThatIsStillValid() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));
    // the token has just been issued for an hour
    ObjectMeta oauthMeta =
        new ObjectMetaBuilder()
            .withName("personal-access-token-fresh")
            .withCreationTimestamp(Instant.now().toString())
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "oauth2-abcde",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PROVIDER_NAME,
                    "gitlab",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "oauth-id",
                    ANNOTATION_SCM_TOKEN_EXPIRES_IN,
                    "3600"))
            .build();
    Secret oauthSecret =
        new SecretBuilder()
            .withMetadata(oauthMeta)
            .withData(
                Map.of("token", Base64.getEncoder().encodeToString("oauth-token".getBytes(UTF_8))))
            .build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(oauthSecret));

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getToken(), "oauth-token");
    verify(scmPersonalAccessTokenFetcher, never())
        .refreshPersonalAccessToken(any(Subject.class), eq("http://host1"));
  }

  @Test
  public void shouldRefreshOAuthTokenThatIsAboutToExpire() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    // the token is valid for a couple of seconds more, which is within the expiration leeway
    Secret oauthSecret =
        tokenSecret(
            "personal-access-token-almost-expired",
            "oauth2-abcde",
            Instant.now().minusSeconds(3595).toString(),
            "3600",
            "almost-expired-token");
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(oauthSecret));
    PersonalAccessToken refreshedToken = refreshedToken();
    when(scmPersonalAccessTokenFetcher.refreshPersonalAccessToken(
            any(Subject.class), eq("http://host1")))
        .thenReturn(refreshedToken);

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getToken(), "new-oauth-token");
    // the git credentials have to be updated, otherwise the workspace keeps the expired token
    verify(gitCredentialManager, times(1)).createOrReplace(eq(refreshedToken));
  }

  @Test
  public void shouldFallBackToStoredOAuthTokenIfRefreshFails() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    Secret oauthSecret =
        tokenSecret(
            "personal-access-token-expired",
            "oauth2-abcde",
            "2021-07-01T12:00:00Z",
            "3600",
            "expired-token");
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(oauthSecret));
    when(scmPersonalAccessTokenFetcher.refreshPersonalAccessToken(
            any(Subject.class), eq("http://host1")))
        .thenThrow(new ScmCommunicationException("the SCM provider is not reachable"));
    // the SCM provider still accepts the stored token, e.g. Che and the provider disagree on the
    // expiration time
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getToken(), "expired-token");
    verify(gitCredentialManager, never()).createOrReplace(any(PersonalAccessToken.class));
  }

  @Test
  public void shouldRemoveExpiredOAuthTokenSecretIfRefreshFailsAndTokenIsInvalid()
      throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    Secret oauthSecret =
        tokenSecret(
            "personal-access-token-expired",
            "oauth2-abcde",
            "2021-07-01T12:00:00Z",
            "3600",
            "expired-token");
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(oauthSecret));
    when(scmPersonalAccessTokenFetcher.refreshPersonalAccessToken(
            any(Subject.class), eq("http://host1")))
        .thenThrow(
            new ScmUnauthorizedException(
                "the refresh token is revoked", "gitlab", "2.0", "http://host1/oauth"));
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.empty());

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertFalse(token.isPresent());
    verify(nonNamespaceOperation, times(1)).delete(eq(oauthSecret));
  }

  @Test
  public void shouldReturnRefreshedOAuthTokenWhenOutdatedSecretRemovalFails() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    Secret oauthSecret =
        tokenSecret(
            "personal-access-token-expired",
            "oauth2-abcde",
            "2021-07-01T12:00:00Z",
            "3600",
            "expired-token");
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(oauthSecret));
    when(scmPersonalAccessTokenFetcher.refreshPersonalAccessToken(
            any(Subject.class), eq("http://host1")))
        .thenReturn(refreshedToken());
    // the outdated secret is left behind, which must not fail the whole operation
    doThrow(new KubernetesClientException("failed to delete the secret"))
        .when(nonNamespaceOperation)
        .delete(any(Secret.class));

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getToken(), "new-oauth-token");
  }

  @Test
  public void shouldNotRefreshExpiredPersonalAccessToken() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));
    // a manually configured token cannot be refreshed, even if it somehow got the expiration
    // annotation
    Secret patSecret =
        tokenSecret(
            "personal-access-token-pat", "gitlab", "2021-07-01T12:00:00Z", "3600", "pat-token");
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(patSecret));

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getToken(), "pat-token");
    verify(scmPersonalAccessTokenFetcher, never())
        .refreshPersonalAccessToken(any(Subject.class), eq("http://host1"));
  }

  @Test
  public void shouldNotRefreshOAuthTokenWithoutExpirationAnnotation() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));
    // secrets created before the OAuth refresh support have no known lifetime
    Secret oauthSecret =
        tokenSecret(
            "personal-access-token-legacy",
            "oauth2-abcde",
            "2021-07-01T12:00:00Z",
            null,
            "oauth-token");
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(oauthSecret));

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getToken(), "oauth-token");
    verify(scmPersonalAccessTokenFetcher, never())
        .refreshPersonalAccessToken(any(Subject.class), eq("http://host1"));
  }

  @Test
  public void shouldNotRefreshOAuthTokenWithUnparsableCreationTimestamp() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));
    // the expiration time cannot be calculated, so the token is validated the regular way
    Secret oauthSecret =
        tokenSecret(
            "personal-access-token-broken",
            "oauth2-abcde",
            "not-a-timestamp",
            "3600",
            "oauth-token");
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(oauthSecret));

    // when
    Optional<PersonalAccessToken> token =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            null,
            "http://host1",
            null);

    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getToken(), "oauth-token");
    verify(scmPersonalAccessTokenFetcher, never())
        .refreshPersonalAccessToken(any(Subject.class), eq("http://host1"));
  }

  @Test
  public void shouldRefreshExpiredOAuthTokenOnStoreGitCredentials() throws Exception {
    // given
    Subject subject = mock(Subject.class);
    when(subject.getUserId()).thenReturn("user1");
    EnvironmentContext context = spy(EnvironmentContext.getCurrent());
    EnvironmentContext.setCurrent(context);
    doReturn(subject).when(context).getSubject();
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    Secret oauthSecret =
        tokenSecret(
            "personal-access-token-expired",
            "oauth2-abcde",
            "2021-07-01T12:00:00Z",
            "3600",
            "expired-token");
    when(secrets.get(any(LabelSelector.class))).thenReturn(List.of(oauthSecret));
    PersonalAccessToken refreshedToken = refreshedToken();
    when(scmPersonalAccessTokenFetcher.refreshPersonalAccessToken(
            any(Subject.class), eq("http://host1")))
        .thenReturn(refreshedToken);

    // when
    personalAccessTokenManager.storeGitCredentials("http://host1");

    // then
    verify(gitCredentialManager, atLeastOnce()).createOrReplace(eq(refreshedToken));
    verify(nonNamespaceOperation, times(1)).delete(eq(oauthSecret));
  }

  /** The token the SCM provider returns when the expired one gets refreshed. */
  private static PersonalAccessToken refreshedToken() {
    return new PersonalAccessToken(
        "http://host1",
        "gitlab",
        "user1",
        null,
        "user",
        "oauth2-fghij",
        "new-oauth-id",
        "new-oauth-token",
        "new-refresh-token",
        3600);
  }

  /**
   * Builds a token secret of the 'user1' user for the 'http://host1' SCM server, so that the
   * expiration related tests do not have to repeat the whole secret structure.
   *
   * @param secretName name of the secret
   * @param tokenName token name annotation value, prefixed with 'oauth2-' for the OAuth tokens
   * @param creationTimestamp creation timestamp of the secret, the token lifetime is counted from
   * @param expiresIn token lifetime annotation value in seconds, or {@code null} if it is not set
   * @param token the token value kept in the secret
   */
  private static Secret tokenSecret(
      String secretName,
      String tokenName,
      String creationTimestamp,
      String expiresIn,
      String token) {
    Map<String, String> annotations = new HashMap<>();
    annotations.put(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME, tokenName);
    annotations.put(ANNOTATION_CHE_USERID, "user1");
    annotations.put(ANNOTATION_SCM_URL, "http://host1");
    annotations.put(ANNOTATION_SCM_PROVIDER_NAME, "gitlab");
    annotations.put(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID, "token-id");
    if (expiresIn != null) {
      annotations.put(ANNOTATION_SCM_TOKEN_EXPIRES_IN, expiresIn);
    }
    return new SecretBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(secretName)
                .withCreationTimestamp(creationTimestamp)
                .withAnnotations(annotations)
                .build())
        .withData(
            Map.of(TOKEN_DATA_FIELD, Base64.getEncoder().encodeToString(token.getBytes(UTF_8))))
        .build();
  }

  @Test
  public void shouldRemoveToken() throws Exception {
    // given
    Subject subject = mock(Subject.class);
    when(subject.getUserId()).thenReturn("user");
    EnvironmentContext context = spy(EnvironmentContext.getCurrent());
    EnvironmentContext.setCurrent(context);
    doReturn(subject).when(context).getSubject();
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    Map<String, String> data1 =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));
    Map<String, String> data2 =
        Map.of("token", Base64.getEncoder().encodeToString("token2".getBytes(UTF_8)));
    ObjectMeta meta1 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-01T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user",
                    ANNOTATION_SCM_URL,
                    "http://host1",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "id1"))
            .build();
    ObjectMeta meta2 =
        new ObjectMetaBuilder()
            .withCreationTimestamp("2021-07-02T12:00:00Z")
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user",
                    ANNOTATION_SCM_URL,
                    "http://host2",
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                    "id2"))
            .build();
    Secret secret1 = new SecretBuilder().withMetadata(meta1).withData(data1).build();
    Secret secret2 = new SecretBuilder().withMetadata(meta2).withData(data2).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(Arrays.asList(secret1, secret2));
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);

    // when
    personalAccessTokenManager.remove("http://host1");

    // then
    verify(nonNamespaceOperation, times(1)).delete(eq(secret1));
  }

  @Test
  public void shouldStoreRefreshTokenAndExpiryInSecret() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);

    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://github.com",
            "github",
            "cheUser",
            null,
            "username",
            "token-name",
            "tid-24",
            "access-token",
            "refresh-token-value",
            3600);

    // when
    personalAccessTokenManager.store(token);

    // then
    verify(nonNamespaceOperation).createOrReplace(captor.capture());
    Secret createdSecret = captor.getValue();
    assertEquals(
        new String(Base64.getDecoder().decode(createdSecret.getData().get("token")), UTF_8),
        "access-token");
    assertEquals(
        new String(Base64.getDecoder().decode(createdSecret.getData().get("refresh-token")), UTF_8),
        "refresh-token-value");
    assertEquals(
        createdSecret.getMetadata().getAnnotations().get(ANNOTATION_SCM_TOKEN_EXPIRES_IN), "3600");
  }

  @Test
  public void shouldStoreSecretWithoutRefreshTokenFieldWhenRefreshTokenIsNull() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);

    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://github.com",
            "github",
            "cheUser",
            null,
            "username",
            "token-name",
            "tid-24",
            "access-token",
            null,
            0);

    // when
    personalAccessTokenManager.store(token);

    // then
    verify(nonNamespaceOperation).createOrReplace(captor.capture());
    Secret createdSecret = captor.getValue();
    assertEquals(
        new String(Base64.getDecoder().decode(createdSecret.getData().get("token")), UTF_8),
        "access-token");
    assertFalse(createdSecret.getData().containsKey("refresh-token"));
    assertFalse(
        createdSecret.getMetadata().getAnnotations().containsKey(ANNOTATION_SCM_TOKEN_EXPIRES_IN));
  }

  @Test
  public void shouldStoreSecretWithoutRefreshTokenFieldWhenRefreshTokenIsEmpty() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);

    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://github.com",
            "github",
            "cheUser",
            null,
            "username",
            "token-name",
            "tid-24",
            "access-token",
            "",
            0);

    // when
    personalAccessTokenManager.store(token);

    // then
    verify(nonNamespaceOperation).createOrReplace(captor.capture());
    Secret createdSecret = captor.getValue();
    assertEquals(createdSecret.getData().keySet(), Set.of("token"));
  }

  @Test
  public void shouldDecodeRefreshTokenAndExpiryFromSecret() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));

    Map<String, String> data =
        Map.of(
            "token", Base64.getEncoder().encodeToString("access-token".getBytes(UTF_8)),
            "refresh-token",
                Base64.getEncoder().encodeToString("refresh-token-value".getBytes(UTF_8)));

    ObjectMeta metaData =
        new ObjectMetaBuilder()
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "oauth2-token",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://github.com",
                    ANNOTATION_SCM_TOKEN_EXPIRES_IN,
                    "7200"))
            .build();

    Secret secret = new SecretBuilder().withMetadata(metaData).withData(data).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(singletonList(secret));

    // when
    PersonalAccessToken result =
        personalAccessTokenManager
            .get(
                new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
                null,
                "http://github.com",
                null)
            .get();

    // then
    assertEquals(result.getToken(), "access-token");
    assertEquals(result.getRefreshToken(), "refresh-token-value");
    assertEquals(result.getExpiresIn(), 7200L);
  }

  @Test
  public void shouldHandleMissingRefreshTokenAndExpiryInSecret() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));

    Map<String, String> data =
        Map.of("token", Base64.getEncoder().encodeToString("token-value".getBytes(UTF_8)));

    ObjectMeta metaData =
        new ObjectMetaBuilder()
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "pat-name",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://host1"))
            .build();

    Secret secret = new SecretBuilder().withMetadata(metaData).withData(data).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(singletonList(secret));

    // when
    PersonalAccessToken result =
        personalAccessTokenManager
            .get(
                new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
                null,
                "http://host1",
                null)
            .get();

    // then
    assertEquals(result.getToken(), "token-value");
    assertEquals(result.getRefreshToken(), null);
    assertEquals(result.getExpiresIn(), 0L);
  }

  @Test
  public void shouldMatchSecretByOAuthProviderName() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);
    when(scmPersonalAccessTokenFetcher.getScmUsername(any(PersonalAccessTokenParams.class)))
        .thenReturn(Optional.of("user"));

    Map<String, String> data =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));

    ObjectMeta metaData =
        new ObjectMetaBuilder()
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "oauth2-token-name",
                    ANNOTATION_SCM_PROVIDER_NAME,
                    "github",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://github.com"))
            .build();

    Secret secret = new SecretBuilder().withMetadata(metaData).withData(data).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(singletonList(secret));

    // when
    Optional<PersonalAccessToken> result =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            "github",
            null,
            null);

    // then
    assertTrue(result.isPresent());
    assertEquals(result.get().getToken(), "token1");
  }

  @Test
  public void shouldNotMatchSecretWithWrongProviderName() throws Exception {
    // given
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(singletonList(meta));
    KubernetesNamespace kubernetesnamespace = Mockito.mock(KubernetesNamespace.class);
    KubernetesSecrets secrets = Mockito.mock(KubernetesSecrets.class);
    when(namespaceFactory.access(eq(null), eq(meta.getName()))).thenReturn(kubernetesnamespace);
    when(kubernetesnamespace.secrets()).thenReturn(secrets);

    Map<String, String> data =
        Map.of("token", Base64.getEncoder().encodeToString("token1".getBytes(UTF_8)));

    ObjectMeta metaData =
        new ObjectMetaBuilder()
            .withAnnotations(
                Map.of(
                    ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                    "oauth2-token-name",
                    ANNOTATION_SCM_PROVIDER_NAME,
                    "gitlab",
                    ANNOTATION_CHE_USERID,
                    "user1",
                    ANNOTATION_SCM_URL,
                    "http://gitlab.com"))
            .build();

    Secret secret = new SecretBuilder().withMetadata(metaData).withData(data).build();
    when(secrets.get(any(LabelSelector.class))).thenReturn(singletonList(secret));

    // when
    Optional<PersonalAccessToken> result =
        personalAccessTokenManager.get(
            new SubjectImpl("user", Collections.emptyList(), "user1", "t1", false),
            "github",
            null,
            null);

    // then
    assertFalse(result.isPresent());
  }
}
