/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
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

import static java.util.Collections.singletonList;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.CONFIGMAPS_ROLE_NAME;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.CREDENTIALS_SECRET_NAME;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.METRICS_ROLE_NAME;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.PREFERENCES_CONFIGMAP_NAME;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.SECRETS_ROLE_NAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingList;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesWorkspaceServiceAccountTest {

  public static final String NAMESPACE = "testNamespace";
  public static final String WORKSPACE_ID = "workspace123";
  public static final String SA_NAME = "workspace-sa";
  public static final Set<String> ROLE_NAMES = Collections.singleton("role-foo");

  @Mock private KubernetesClientFactory clientFactory;
  private KubernetesClient k8sClient;
  private KubernetesServer serverMock;
  private KubernetesWorkspaceServiceAccount serviceAccount;

  @BeforeMethod
  public void setUp() throws Exception {
    this.serviceAccount =
        new KubernetesWorkspaceServiceAccount(
            WORKSPACE_ID, NAMESPACE, SA_NAME, ROLE_NAMES, clientFactory);

    serverMock = new KubernetesServer(true, true);
    serverMock.before();

    k8sClient = serverMock.getClient();
    when(clientFactory.create(anyString())).thenReturn(k8sClient);
  }

  @Test
  public void shouldProvisionSARolesEvenIfItAlreadyExists() throws Exception {
    ServiceAccountBuilder serviceAccountBuilder =
        new ServiceAccountBuilder().withNewMetadata().withName(SA_NAME).endMetadata();
    RoleBuilder roleBuilder = new RoleBuilder().withNewMetadata().withName("foo").endMetadata();
    RoleBindingBuilder roleBindingBuilder =
        new RoleBindingBuilder().withNewMetadata().withName("foo-builder").endMetadata();

    // pre-create SA and some roles
    k8sClient
        .serviceAccounts()
        .inNamespace(NAMESPACE)
        .createOrReplace(serviceAccountBuilder.build());
    k8sClient.rbac().roles().inNamespace(NAMESPACE).create(roleBuilder.build());
    k8sClient.rbac().roleBindings().inNamespace(NAMESPACE).create(roleBindingBuilder.build());

    // when
    serviceAccount.prepare();

    // then
    // make sure more roles added
    RoleList rl = k8sClient.rbac().roles().inNamespace(NAMESPACE).list();
    assertTrue(rl.getItems().size() > 1);

    RoleBindingList rbl = k8sClient.rbac().roleBindings().inNamespace(NAMESPACE).list();
    assertTrue(rbl.getItems().size() > 1);
  }

  @Test
  public void shouldCreateMetricsRoleIfAPIEnabledOnServer() throws Exception {
    KubernetesClient localK8sClient = spy(serverMock.getClient());
    when(localK8sClient.supportsApiPath(eq("/apis/metrics.k8s.io"))).thenReturn(true);
    when(clientFactory.create(anyString())).thenReturn(localK8sClient);

    // when
    serviceAccount.prepare();

    // then
    // make sure metrics role & rb added
    RoleList rl = k8sClient.rbac().roles().inNamespace(NAMESPACE).list();
    assertTrue(
        rl.getItems().stream().anyMatch(r -> r.getMetadata().getName().equals(METRICS_ROLE_NAME)));

    RoleBindingList rbl = k8sClient.rbac().roleBindings().inNamespace(NAMESPACE).list();
    assertTrue(
        rbl.getItems().stream()
            .anyMatch(rb -> rb.getMetadata().getName().equals(SA_NAME + "-metrics")));
  }

  @Test
  public void shouldNotCreateMetricsRoleIfAPINotEnabledOnServer() throws Exception {
    KubernetesClient localK8sClient = spy(serverMock.getClient());
    when(localK8sClient.supportsApiPath(eq("/apis/metrics.k8s.io"))).thenReturn(false);
    when(clientFactory.create(anyString())).thenReturn(localK8sClient);

    // when
    serviceAccount.prepare();

    // then
    // make sure metrics role & rb not added
    RoleList rl = k8sClient.rbac().roles().inNamespace(NAMESPACE).list();
    assertTrue(
        rl.getItems().stream().noneMatch(r -> r.getMetadata().getName().equals(METRICS_ROLE_NAME)));

    RoleBindingList rbl = k8sClient.rbac().roleBindings().inNamespace(NAMESPACE).list();
    assertTrue(
        rbl.getItems().stream()
            .noneMatch(rb -> rb.getMetadata().getName().equals(SA_NAME + "-metrics")));
  }

  @Test
  public void shouldCreateCredentialsSecretRole() throws Exception {
    KubernetesClient localK8sClient = spy(serverMock.getClient());
    when(clientFactory.create(anyString())).thenReturn(localK8sClient);

    // when
    serviceAccount.prepare();

    // then
    RoleList rl = k8sClient.rbac().roles().inNamespace(NAMESPACE).list();
    Optional<Role> roleOptional =
        rl.getItems().stream()
            .filter(r -> r.getMetadata().getName().equals(SECRETS_ROLE_NAME))
            .findFirst();
    assertTrue(roleOptional.isPresent());
    PolicyRule rule = roleOptional.get().getRules().get(0);
    assertEquals(rule.getResources(), singletonList("secrets"));
    assertEquals(rule.getResourceNames(), singletonList(CREDENTIALS_SECRET_NAME));
    assertEquals(rule.getApiGroups(), singletonList(""));
    assertEquals(rule.getVerbs(), Arrays.asList("get", "patch"));

    RoleBindingList rbl = k8sClient.rbac().roleBindings().inNamespace(NAMESPACE).list();
    assertTrue(
        rbl.getItems().stream()
            .anyMatch(rb -> rb.getMetadata().getName().equals(SA_NAME + "-secrets")));
  }

  @Test
  public void shouldCreatePreferencesConfigmapRole() throws Exception {
    KubernetesClient localK8sClient = spy(serverMock.getClient());
    when(clientFactory.create(anyString())).thenReturn(localK8sClient);

    // when
    serviceAccount.prepare();

    // then
    RoleList rl = k8sClient.rbac().roles().inNamespace(NAMESPACE).list();
    Optional<Role> roleOptional =
        rl.getItems().stream()
            .filter(r -> r.getMetadata().getName().equals(CONFIGMAPS_ROLE_NAME))
            .findFirst();
    assertTrue(roleOptional.isPresent());
    PolicyRule rule = roleOptional.get().getRules().get(0);
    assertEquals(rule.getResources(), singletonList("configmaps"));
    assertEquals(rule.getResourceNames(), singletonList(PREFERENCES_CONFIGMAP_NAME));
    assertEquals(rule.getApiGroups(), singletonList(""));
    assertEquals(rule.getVerbs(), Arrays.asList("get", "patch"));

    RoleBindingList rbl = k8sClient.rbac().roleBindings().inNamespace(NAMESPACE).list();
    assertTrue(
        rbl.getItems().stream()
            .anyMatch(rb -> rb.getMetadata().getName().equals(SA_NAME + "-configmaps")));
  }
}
