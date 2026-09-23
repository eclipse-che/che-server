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
package org.eclipse.che.security.oauth.kubernetes;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.server.impls.KubernetesNamespaceMetaImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesUserWorkspaceUrlProviderTest {

  @Mock private KubernetesNamespaceFactory namespaceFactory;
  @Mock private CheServerKubernetesClientFactory clientFactory;
  @Mock private KubernetesClient kubeClient;

  @Mock
  private MixedOperation<
          GenericKubernetesResource,
          GenericKubernetesResourceList,
          Resource<GenericKubernetesResource>>
      devWorkspacesOperation;

  private KubernetesUserWorkspaceUrlProvider provider;

  @BeforeMethod
  public void setUp() throws Exception {
    provider = new KubernetesUserWorkspaceUrlProvider(namespaceFactory, clientFactory);
    when(clientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.genericKubernetesResources(any(ResourceDefinitionContext.class)))
        .thenReturn(devWorkspacesOperation);
  }

  @Test
  public void shouldReturnMainUrlsOfTheDevWorkspacesOfTheUser() throws Exception {
    when(namespaceFactory.list())
        .thenReturn(singletonList(new KubernetesNamespaceMetaImpl("alice-che")));
    mockDevWorkspaces(
        "alice-che",
        devWorkspace("https://che.example.com/alice/first/3100/"),
        devWorkspace("https://che.example.com/alice/second/3100/"));

    Set<String> urls = provider.getWorkspaceUrls();

    assertEquals(
        urls,
        Set.of(
            "https://che.example.com/alice/first/3100/",
            "https://che.example.com/alice/second/3100/"));
  }

  @Test
  public void shouldCollectUrlsFromAllNamespacesOfTheUser() throws Exception {
    when(namespaceFactory.list())
        .thenReturn(
            asList(
                new KubernetesNamespaceMetaImpl("alice-che"),
                new KubernetesNamespaceMetaImpl("alice-che-2")));
    mockDevWorkspaces("alice-che", devWorkspace("https://che.example.com/alice/first/3100/"));
    mockDevWorkspaces("alice-che-2", devWorkspace("https://che.example.com/alice/second/3100/"));

    Set<String> urls = provider.getWorkspaceUrls();

    assertEquals(urls.size(), 2);
  }

  @Test
  public void shouldSkipDevWorkspacesWithoutMainUrl() throws Exception {
    when(namespaceFactory.list())
        .thenReturn(singletonList(new KubernetesNamespaceMetaImpl("alice-che")));
    mockDevWorkspaces(
        "alice-che",
        devWorkspaceWithoutStatus(),
        devWorkspace(null),
        devWorkspace(""),
        devWorkspace("   "),
        devWorkspace("https://che.example.com/alice/first/3100/"));

    Set<String> urls = provider.getWorkspaceUrls();

    assertEquals(urls, Set.of("https://che.example.com/alice/first/3100/"));
  }

  /** The CRD does not constrain us to a string here, so a non string value must not blow up. */
  @Test
  public void shouldSkipDevWorkspacesWithANonStringMainUrl() throws Exception {
    when(namespaceFactory.list())
        .thenReturn(singletonList(new KubernetesNamespaceMetaImpl("alice-che")));
    GenericKubernetesResource devWorkspace = devWorkspace(null);
    ((Map<String, Object>) devWorkspace.getAdditionalProperties().get("status"))
        .put("mainUrl", List.of("https://che.example.com/alice/first/3100/"));
    mockDevWorkspaces("alice-che", devWorkspace);

    assertTrue(provider.getWorkspaceUrls().isEmpty());
  }

  @Test
  public void shouldReturnEmptySetWhenTheUserHasNoDevWorkspaces() throws Exception {
    when(namespaceFactory.list())
        .thenReturn(singletonList(new KubernetesNamespaceMetaImpl("alice-che")));
    mockDevWorkspaces("alice-che");

    assertTrue(provider.getWorkspaceUrls().isEmpty());
  }

  @Test(expectedExceptions = ServerException.class)
  public void shouldFailWhenTheNamespacesCannotBeResolved() throws Exception {
    when(namespaceFactory.list()).thenThrow(new InfrastructureException("no namespace"));

    provider.getWorkspaceUrls();
  }

  @Test(expectedExceptions = ServerException.class)
  public void shouldFailWhenTheDevWorkspacesCannotBeRead() throws Exception {
    mockUnreadableDevWorkspaces();

    provider.getWorkspaceUrls();
  }

  /**
   * The message of a {@link ServerException} is serialized into the response body, and a Kubernetes
   * API failure names the service account and the namespaces it was denied.
   */
  @Test
  public void shouldNotLeakTheKubernetesFailureIntoTheExceptionMessage() throws Exception {
    mockUnreadableDevWorkspaces();

    try {
      provider.getWorkspaceUrls();
      fail("Expected a ServerException");
    } catch (ServerException e) {
      assertFalse(e.getMessage().contains("system:serviceaccount:eclipse-che:che"), e.getMessage());
      assertFalse(e.getMessage().contains("alice-che"), e.getMessage());
    }
  }

  private void mockUnreadableDevWorkspaces() throws Exception {
    when(namespaceFactory.list())
        .thenReturn(singletonList(new KubernetesNamespaceMetaImpl("alice-che")));
    NonNamespaceOperation<
            GenericKubernetesResource,
            GenericKubernetesResourceList,
            Resource<GenericKubernetesResource>>
        inNamespace = mock(NonNamespaceOperation.class);
    when(devWorkspacesOperation.inNamespace("alice-che")).thenReturn(inNamespace);
    when(inNamespace.list())
        .thenThrow(
            new KubernetesClientException(
                "devworkspaces.workspace.devfile.io is forbidden: User"
                    + " \"system:serviceaccount:eclipse-che:che\" cannot list resource in namespace"
                    + " \"alice-che\""));
  }

  private void mockDevWorkspaces(String namespace, GenericKubernetesResource... devWorkspaces) {
    NonNamespaceOperation<
            GenericKubernetesResource,
            GenericKubernetesResourceList,
            Resource<GenericKubernetesResource>>
        inNamespace = mock(NonNamespaceOperation.class);
    GenericKubernetesResourceList list = new GenericKubernetesResourceList();
    list.setItems(List.of(devWorkspaces));
    when(devWorkspacesOperation.inNamespace(namespace)).thenReturn(inNamespace);
    when(inNamespace.list()).thenReturn(list);
  }

  private static GenericKubernetesResource devWorkspace(String mainUrl) {
    GenericKubernetesResource devWorkspace = new GenericKubernetesResource();
    devWorkspace.setApiVersion("workspace.devfile.io/v1alpha2");
    devWorkspace.setKind("DevWorkspace");
    Map<String, Object> status = new HashMap<>();
    if (mainUrl != null) {
      status.put("mainUrl", mainUrl);
    }
    devWorkspace.setAdditionalProperty("status", status);
    return devWorkspace;
  }

  /** A DevWorkspace that has not been reconciled yet has no {@code status} at all. */
  private static GenericKubernetesResource devWorkspaceWithoutStatus() {
    GenericKubernetesResource devWorkspace = new GenericKubernetesResource();
    devWorkspace.setApiVersion("workspace.devfile.io/v1alpha2");
    devWorkspace.setKind("DevWorkspace");
    return devWorkspace;
  }
}
