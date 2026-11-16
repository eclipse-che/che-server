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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.eclipse.che.api.factory.server.scm.kubernetes.KubernetesGitCredentialManager.ANNOTATION_CHE_USERID;
import static org.eclipse.che.api.factory.server.scm.kubernetes.KubernetesGitCredentialManager.ANNOTATION_SCM_URL;
import static org.eclipse.che.api.factory.server.scm.kubernetes.KubernetesGitCredentialManager.DEFAULT_SECRET_ANNOTATIONS;
import static org.eclipse.che.api.factory.server.scm.kubernetes.KubernetesGitCredentialManager.NAME_PATTERN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.server.impls.KubernetesNamespaceMetaImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesGitCredentialManagerTest {

  @Mock private KubernetesNamespaceFactory namespaceFactory;
  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  @Mock private KubernetesClient kubeClient;

  @Mock private MixedOperation<Secret, SecretList, Resource<Secret>> secretsMixedOperation;

  @Mock NonNamespaceOperation<Secret, SecretList, Resource<Secret>> nonNamespaceOperation;

  @Mock private FilterWatchListDeletable<Secret, SecretList, Resource<Secret>> filterWatchDeletable;

  @Mock private SecretList secretList;

  KubernetesGitCredentialManager kubernetesGitCredentialManager;

  @BeforeMethod
  protected void init() {
    kubernetesGitCredentialManager =
        new KubernetesGitCredentialManager(namespaceFactory, cheServerKubernetesClientFactory);
    assertNotNull(this.kubernetesGitCredentialManager);
  }

  @AfterMethod
  public void cleanUp() {
    EnvironmentContext.reset();
  }

  @Test
  public void testCreateAndSaveNewPATGitCredential() throws Exception {
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(Collections.singletonList(meta));

    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    when(nonNamespaceOperation.withLabels(anyMap())).thenReturn(filterWatchDeletable);
    when(filterWatchDeletable.list()).thenReturn(secretList);
    when(secretList.getItems()).thenReturn(emptyList());
    ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);

    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://bitbucket.com",
            "provider",
            "cheUser",
            null,
            "username",
            "token-name",
            "tid-23434",
            "token123",
            null,
            0);

    // when
    kubernetesGitCredentialManager.createOrReplace(token);
    // then
    verify(nonNamespaceOperation).createOrReplace(captor.capture());
    Secret createdSecret = captor.getValue();
    assertNotNull(createdSecret);
    assertEquals(
        new String(Base64.getDecoder().decode(createdSecret.getData().get("credentials"))),
        "https://username:token123@bitbucket.com");
    assertTrue(createdSecret.getMetadata().getName().startsWith(NAME_PATTERN));
    assertFalse(createdSecret.getMetadata().getName().contains(token.getScmUserName()));
  }

  @Test
  public void shouldUseHardcodedUsernameIfScmOrganizationIsDefined() throws Exception {
    // given
    KubernetesNamespaceMeta namespaceMeta = new KubernetesNamespaceMetaImpl("test");
    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://bitbucket-server.com:5648",
            "provider",
            "cheUser",
            "cheOrganization",
            "username",
            "token-name",
            "tid-23434",
            "token123",
            null,
            0);

    Map<String, String> annotations = new HashMap<>(DEFAULT_SECRET_ANNOTATIONS);

    annotations.put(ANNOTATION_SCM_URL, token.getScmProviderUrl() + "/");
    annotations.put(ANNOTATION_CHE_USERID, token.getCheUserId());
    ObjectMeta objectMeta =
        new ObjectMetaBuilder()
            .withName(NameGenerator.generate(NAME_PATTERN, 5))
            .withAnnotations(annotations)
            .build();
    Secret existing =
        new SecretBuilder()
            .withMetadata(objectMeta)
            .withData(Map.of("credentials", "foo 123"))
            .build();

    when(namespaceFactory.list()).thenReturn(Collections.singletonList(namespaceMeta));

    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(namespaceMeta.getName())))
        .thenReturn(nonNamespaceOperation);
    when(nonNamespaceOperation.withLabels(anyMap())).thenReturn(filterWatchDeletable);
    when(filterWatchDeletable.list()).thenReturn(secretList);
    when(secretList.getItems()).thenReturn(singletonList(existing));

    // when
    kubernetesGitCredentialManager.createOrReplace(token);
    // then
    ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
    verify(nonNamespaceOperation).createOrReplace(captor.capture());
    Secret createdSecret = captor.getValue();
    assertNotNull(createdSecret);
    assertEquals(
        new String(Base64.getDecoder().decode(createdSecret.getData().get("credentials"))),
        "https://username:token123@bitbucket-server.com:5648");
  }

  @Test
  public void testCreateAndSaveNewOAuthGitCredential() throws Exception {
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(Collections.singletonList(meta));

    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    when(nonNamespaceOperation.withLabels(anyMap())).thenReturn(filterWatchDeletable);
    when(filterWatchDeletable.list()).thenReturn(secretList);
    when(secretList.getItems()).thenReturn(emptyList());
    ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);

    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://bitbucket.com",
            "provider",
            "cheUser",
            null,
            "username",
            "oauth2-token-name",
            "tid-23434",
            "token123",
            null,
            0);

    // when
    kubernetesGitCredentialManager.createOrReplace(token);
    // then
    verify(nonNamespaceOperation).createOrReplace(captor.capture());
    Secret createdSecret = captor.getValue();
    assertNotNull(createdSecret);
    assertEquals(
        new String(Base64.getDecoder().decode(createdSecret.getData().get("credentials"))),
        "https://oauth2:token123@bitbucket.com");
    assertTrue(createdSecret.getMetadata().getName().startsWith(NAME_PATTERN));
    assertFalse(createdSecret.getMetadata().getName().contains(token.getScmUserName()));
  }

  @Test
  public void testCreateAndSaveNewBitbucketOAuthGitCredential() throws Exception {
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(Collections.singletonList(meta));

    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(meta.getName()))).thenReturn(nonNamespaceOperation);
    when(nonNamespaceOperation.withLabels(anyMap())).thenReturn(filterWatchDeletable);
    when(filterWatchDeletable.list()).thenReturn(secretList);
    when(secretList.getItems()).thenReturn(emptyList());
    ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);

    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://bitbucket.com",
            "bitbucket",
            "cheUser",
            null,
            "username",
            "oauth2-token-name",
            "tid-23434",
            "token123",
            null,
            0);

    // when
    kubernetesGitCredentialManager.createOrReplace(token);
    // then
    verify(nonNamespaceOperation).createOrReplace(captor.capture());
    Secret createdSecret = captor.getValue();
    assertNotNull(createdSecret);
    assertEquals(
        new String(Base64.getDecoder().decode(createdSecret.getData().get("credentials"))),
        "https://username:token123@bitbucket.com");
    assertTrue(createdSecret.getMetadata().getName().startsWith(NAME_PATTERN));
    assertFalse(createdSecret.getMetadata().getName().contains(token.getScmUserName()));
  }

  @Test
  public void testUpdateTokenInExistingCredential() throws Exception {
    KubernetesNamespaceMeta namespaceMeta = new KubernetesNamespaceMetaImpl("test");
    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://bitbucket.com:5648",
            "provider",
            "cheUser",
            null,
            "username",
            "token-name",
            "tid-23434",
            "token123",
            null,
            0);

    Map<String, String> annotations = new HashMap<>(DEFAULT_SECRET_ANNOTATIONS);

    annotations.put(ANNOTATION_SCM_URL, token.getScmProviderUrl() + "/");
    annotations.put(ANNOTATION_CHE_USERID, token.getCheUserId());
    ObjectMeta objectMeta =
        new ObjectMetaBuilder()
            .withName(NameGenerator.generate(NAME_PATTERN, 5))
            .withAnnotations(annotations)
            .build();
    Secret existing =
        new SecretBuilder()
            .withMetadata(objectMeta)
            .withData(Map.of("credentials", "foo 123"))
            .build();

    when(namespaceFactory.list()).thenReturn(Collections.singletonList(namespaceMeta));

    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(namespaceMeta.getName())))
        .thenReturn(nonNamespaceOperation);
    when(nonNamespaceOperation.withLabels(anyMap())).thenReturn(filterWatchDeletable);
    when(filterWatchDeletable.list()).thenReturn(secretList);
    when(secretList.getItems()).thenReturn(singletonList(existing));

    // when
    kubernetesGitCredentialManager.createOrReplace(token);
    // then
    ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
    verify(nonNamespaceOperation).createOrReplace(captor.capture());
    Secret createdSecret = captor.getValue();
    assertNotNull(createdSecret);
    assertEquals(
        new String(Base64.getDecoder().decode(createdSecret.getData().get("credentials"))),
        "https://username:token123@bitbucket.com:5648");
    assertEquals(createdSecret.getMetadata().getName(), objectMeta.getName());
  }

  /**
   * A caller with no user of its own, the job that refreshes the OAuth tokens of the running
   * workspaces for one, names the namespace it works on in the {@link EnvironmentContext}. The
   * credentials have to be written there, and the namespace of the user must not be resolved from
   * the subject, which that caller does not have a full one of.
   */
  @Test
  public void shouldWriteTheCredentialsToTheNamespaceNamedInTheEnvironmentContext()
      throws Exception {
    // given
    EnvironmentContext context = new EnvironmentContext();
    context.setNamespaceName("named-namespace");
    EnvironmentContext.setCurrent(context);
    expectSecretsOf("named-namespace", emptyList());

    // when
    kubernetesGitCredentialManager.createOrReplace(token("https://bitbucket.com"));

    // then
    verify(secretsMixedOperation, atLeastOnce()).inNamespace("named-namespace");
    verify(nonNamespaceOperation).createOrReplace(any(Secret.class));
    verify(namespaceFactory, never()).list();
  }

  /** Nothing names a namespace, so the first namespace of the user is taken. */
  @Test
  public void shouldWriteTheCredentialsToTheFirstNamespaceOfTheUser() throws Exception {
    // given
    when(namespaceFactory.list())
        .thenReturn(
            List.of(
                new KubernetesNamespaceMetaImpl("first-namespace"),
                new KubernetesNamespaceMetaImpl("second-namespace")));
    expectSecretsOf("first-namespace", emptyList());

    // when
    kubernetesGitCredentialManager.createOrReplace(token("https://bitbucket.com"));

    // then
    verify(secretsMixedOperation, atLeastOnce()).inNamespace("first-namespace");
    verify(secretsMixedOperation, never()).inNamespace("second-namespace");
    verify(nonNamespaceOperation).createOrReplace(any(Secret.class));
  }

  /** The user has no namespace at all, so there is nowhere to write the credentials to. */
  @Test(
      expectedExceptions = UnsatisfiedScmPreconditionException.class,
      expectedExceptionsMessageRegExp = "No user namespace found. Cannot read SCM credentials.")
  public void shouldFailWhenTheUserHasNoNamespace() throws Exception {
    // given
    when(namespaceFactory.list()).thenReturn(emptyList());

    // when
    kubernetesGitCredentialManager.createOrReplace(token("https://bitbucket.com"));
  }

  /** Makes the mocked client answer with the given secrets of the given namespace. */
  private void expectSecretsOf(String namespace, List<Secret> secrets) throws Exception {
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.secrets()).thenReturn(secretsMixedOperation);
    when(secretsMixedOperation.inNamespace(eq(namespace))).thenReturn(nonNamespaceOperation);
    when(nonNamespaceOperation.withLabels(anyMap())).thenReturn(filterWatchDeletable);
    when(filterWatchDeletable.list()).thenReturn(secretList);
    when(secretList.getItems()).thenReturn(secrets);
  }

  private static PersonalAccessToken token(String scmProviderUrl) {
    return new PersonalAccessToken(
        scmProviderUrl,
        "provider",
        "cheUser",
        null,
        "username",
        "token-name",
        "tid-23434",
        "token123",
        null,
        0);
  }
}
