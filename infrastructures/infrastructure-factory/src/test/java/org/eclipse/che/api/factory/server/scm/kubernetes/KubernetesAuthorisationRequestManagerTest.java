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

import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.PREFERENCES_CONFIGMAP_NAME;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Collections;
import java.util.Map;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.server.impls.KubernetesNamespaceMetaImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesAuthorisationRequestManagerTest {
  @Mock private KubernetesNamespaceFactory namespaceFactory;

  @Mock
  private MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> configMapsMixedOperation;

  @Mock NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> nonNamespaceOperation;

  @Mock private Resource<ConfigMap> configMapResource;

  @Mock private ConfigMap configMap;

  @Mock private KubernetesClient kubeClient;

  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;

  private KubernetesAuthorisationRequestManager authorisationRequestManager;

  @BeforeMethod
  protected void init() throws Exception {
    KubernetesNamespaceMeta meta = new KubernetesNamespaceMetaImpl("test");
    when(namespaceFactory.list()).thenReturn(Collections.singletonList(meta));

    when(cheServerKubernetesClientFactory.create()).thenReturn(kubeClient);
    when(kubeClient.configMaps()).thenReturn(configMapsMixedOperation);
    when(configMapsMixedOperation.inNamespace(eq(meta.getName())))
        .thenReturn(nonNamespaceOperation);
    when(nonNamespaceOperation.withName(eq(PREFERENCES_CONFIGMAP_NAME)))
        .thenReturn(configMapResource);
    when(configMapResource.get()).thenReturn(configMap);

    authorisationRequestManager =
        new KubernetesAuthorisationRequestManager(
            namespaceFactory, cheServerKubernetesClientFactory);
  }

  @Test
  public void shouldStoreAuthorisationRequestToEmptyConfigMap() {
    // given
    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);

    // when
    authorisationRequestManager.store("test-provider");

    // then
    verify(configMap).setData(captor.capture());
    Map<String, String> value = captor.getValue();
    assertEquals(value.get("skip-authorisation"), "[test-provider]");
  }

  @Test
  public void shouldStoreAuthorisationRequestToNonEmptyConfigMap() {
    // given
    when(configMap.getData()).thenReturn(Map.of("skip-authorisation", "[existing-provider]"));
    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);

    // when
    authorisationRequestManager.store("test-provider");

    // then
    verify(configMap).setData(captor.capture());
    Map<String, String> value = captor.getValue();
    assertEquals(value.get("skip-authorisation"), "[test-provider, existing-provider]");
  }

  @Test
  public void shouldNotStoreAuthorisationRequestIfAlreadyStored() {
    // given
    when(configMap.getData()).thenReturn(Map.of("skip-authorisation", "[test-provider]"));

    // when
    authorisationRequestManager.store("test-provider");

    // then
    verify(configMap, never()).setData(anyMap());
  }

  @Test
  public void shouldRemoveAuthorisationRequestFromNonEmptyConfigMap() {
    // given
    when(configMap.getData())
        .thenReturn(Map.of("skip-authorisation", "[test-provider, existing-provider]"));
    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);

    // when
    authorisationRequestManager.remove("test-provider");

    // then
    verify(configMap).setData(captor.capture());
    Map<String, String> value = captor.getValue();
    assertEquals(value.get("skip-authorisation"), "[existing-provider]");
  }

  @Test
  public void shouldRemoveAuthorisationRequestFromSingleValueConfigMap() {
    // given
    when(configMap.getData()).thenReturn(Map.of("skip-authorisation", "[test-provider]"));
    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);

    // when
    authorisationRequestManager.remove("test-provider");

    // then
    verify(configMap).setData(captor.capture());
    Map<String, String> value = captor.getValue();
    assertEquals(value.get("skip-authorisation"), "[]");
  }

  @Test
  public void shouldNotRemoveAuthorisationRequestIfAlreadyRemoved() {
    // given
    when(configMap.getData()).thenReturn(Map.of("skip-authorisation", "[]"));

    // when
    authorisationRequestManager.remove("test-provider");

    // then
    verify(configMap, never()).setData(anyMap());
  }

  @Test
  public void shouldReturnFalseAuthorisationRequestStateFromEmptyConfigMap() {
    // when
    boolean stored = authorisationRequestManager.isStored("test-provider");

    // then
    assertFalse(stored);
  }

  @Test
  public void shouldReturnFalseAuthorisationRequestStateFromNonEmptyConfigMap() {
    // given
    when(configMap.getData()).thenReturn(Map.of("skip-authorisation", "[existing-provider]"));

    // when
    boolean stored = authorisationRequestManager.isStored("test-provider");

    // then
    assertFalse(stored);
  }

  @Test
  public void shouldReturnTrueAuthorisationRequestStateFromNonEmptyConfigMap() {
    // given
    when(configMap.getData())
        .thenReturn(Map.of("skip-authorisation", "[existing-provider, test-provider]"));

    // when
    boolean stored = authorisationRequestManager.isStored("test-provider");

    // then
    assertTrue(stored);
  }
}
