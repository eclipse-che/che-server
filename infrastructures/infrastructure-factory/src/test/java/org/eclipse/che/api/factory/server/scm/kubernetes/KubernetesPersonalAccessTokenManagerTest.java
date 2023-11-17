/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
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
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.ScmPersonalAccessTokenFetcher;
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
            .get(new SubjectImpl("user", "user", "t1", false), "http://host1")
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
            "https://bitbucket.com", "cheUser", "username", "token-name", "tid-24", "token123");

    // when
    personalAccessTokenManager.save(token);

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
            .get(new SubjectImpl("user", "user1", "t1", false), "http://host1")
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
            new SubjectImpl("user", "user1", "t1", false), "http://host1");

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
            new SubjectImpl("user", "user1", "t1", false), "http://host1");

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
            .get(new SubjectImpl("user", "user1", "t1", false), "http://host1.com")
            .get();
    PersonalAccessToken token2 =
        personalAccessTokenManager
            .get(new SubjectImpl("user", "user1", "t1", false), "http://host2.com/")
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
            new SubjectImpl("user", "user1", "t1", false), "http://host1");
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
            new SubjectImpl("user", "user1", "t1", false), "http://host1");
    // then
    assertFalse(token.isPresent());
    verify(nonNamespaceOperation, times(1)).delete(eq(secret1));
  }

  @Test(dependsOnMethods = "shouldDeleteInvalidTokensOnGet")
  public void shouldReturnFirstValidToken() throws Exception {
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
            new SubjectImpl("user", "user1", "t1", false), "http://host1");
    // then
    assertTrue(token.isPresent());
    assertEquals(token.get().getScmTokenId(), "id2");
  }
}
