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
package org.eclipse.che.workspace.infrastructure.openshift.project.configurator;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import io.fabric8.kubernetes.api.model.rbac.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.RbacAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.CheInstallationLocation;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Test for {@link
 * org.eclipse.che.workspace.infrastructure.openshift.project.configurator.OpenShiftStopWorkspaceRoleConfigurator}
 *
 * <p>#author Tom George
 */
@Listeners(MockitoTestNGListener.class)
public class OpenShiftStopWorkspaceRoleConfiguratorTest {

  @Mock private CheInstallationLocation cheInstallationLocation;
  private OpenShiftStopWorkspaceRoleConfigurator stopWorkspaceRoleProvisioner;

  @Mock private CheServerKubernetesClientFactory cheClientFactory;
  @Mock private KubernetesClient client;

  @Mock private MixedOperation<Role, RoleList, Resource<Role>> mixedRoleOperation;

  @Mock
  private MixedOperation<RoleBinding, RoleBindingList, Resource<RoleBinding>>
      mixedRoleBindingOperation;

  @Mock private NonNamespaceOperation<Role, RoleList, Resource<Role>> nonNamespaceRoleOperation;

  @Mock
  private NonNamespaceOperation<RoleBinding, RoleBindingList, Resource<RoleBinding>>
      nonNamespaceRoleBindingOperation;

  @Mock private Resource<Role> roleResource;
  @Mock private Resource<RoleBinding> roleBindingResource;
  @Mock private Role mockRole;
  @Mock private RoleBinding mockRoleBinding;
  @Mock private RbacAPIGroupDSL rbacAPIGroupDSL;

  private final Role expectedRole =
      new RoleBuilder()
          .withNewMetadata()
          .withName("workspace-stop")
          .endMetadata()
          .withRules(
              new PolicyRuleBuilder()
                  .withApiGroups("")
                  .withResources("pods")
                  .withVerbs("get", "list", "watch", "delete")
                  .build(),
              new PolicyRuleBuilder()
                  .withApiGroups("")
                  .withResources("configmaps", "services", "secrets")
                  .withVerbs("delete", "list", "get")
                  .build(),
              new PolicyRuleBuilder()
                  .withApiGroups("route.openshift.io")
                  .withResources("routes")
                  .withVerbs("delete", "list")
                  .build(),
              new PolicyRuleBuilder()
                  .withApiGroups("apps")
                  .withResources("deployments", "replicasets")
                  .withVerbs("delete", "list", "get", "patch")
                  .build())
          .build();

  private final RoleBinding expectedRoleBinding =
      new RoleBindingBuilder()
          .withNewMetadata()
          .withName("workspace-stop")
          .endMetadata()
          .withNewRoleRef()
          .withKind("Role")
          .withName("workspace-stop")
          .endRoleRef()
          .withSubjects(
              new SubjectBuilder()
                  .withKind("ServiceAccount")
                  .withName("che")
                  .withNamespace("che")
                  .build())
          .build();

  @BeforeMethod
  public void setUp() throws Exception {
    lenient().when(cheInstallationLocation.getInstallationLocationNamespace()).thenReturn("che");
    stopWorkspaceRoleProvisioner =
        new OpenShiftStopWorkspaceRoleConfigurator(
            cheClientFactory, cheInstallationLocation, true, "yes");
    lenient().when(cheClientFactory.create()).thenReturn(client);
    lenient().when(client.rbac()).thenReturn(rbacAPIGroupDSL);
    lenient().when(rbacAPIGroupDSL.roles()).thenReturn(mixedRoleOperation);
    lenient().when(rbacAPIGroupDSL.roleBindings()).thenReturn(mixedRoleBindingOperation);
    lenient()
        .when(mixedRoleOperation.inNamespace(anyString()))
        .thenReturn(nonNamespaceRoleOperation);
    lenient()
        .when(mixedRoleBindingOperation.inNamespace(anyString()))
        .thenReturn(nonNamespaceRoleBindingOperation);
    lenient().when(nonNamespaceRoleOperation.withName(anyString())).thenReturn(roleResource);
    lenient()
        .when(nonNamespaceRoleBindingOperation.withName(anyString()))
        .thenReturn(roleBindingResource);
    lenient().when(roleResource.get()).thenReturn(null);
    lenient().when(nonNamespaceRoleOperation.createOrReplace(any())).thenReturn(mockRole);
    lenient()
        .when(nonNamespaceRoleBindingOperation.createOrReplace(any()))
        .thenReturn(mockRoleBinding);
  }

  @Test
  public void shouldCreateRole() {
    assertEquals(
        stopWorkspaceRoleProvisioner.createStopWorkspacesRole("workspace-stop"), expectedRole);
  }

  @Test
  public void shouldCreateRoleBinding() throws InfrastructureException {
    assertEquals(
        stopWorkspaceRoleProvisioner.createStopWorkspacesRoleBinding("workspace-stop"),
        expectedRoleBinding);
  }

  @Test
  public void shouldCreateRoleAndRoleBindingWhenRoleDoesNotYetExist()
      throws InfrastructureException {
    stopWorkspaceRoleProvisioner.configure(null, "developer-che");
    verify(client.rbac().roles().inNamespace("developer-che")).createOrReplace(expectedRole);
    verify(client.rbac().roleBindings().inNamespace("developer-che"))
        .createOrReplace(expectedRoleBinding);
  }

  @Test
  public void shouldNotCreateRoleBindingWhenStopWorkspaceRolePropertyIsDisabled()
      throws InfrastructureException {
    OpenShiftStopWorkspaceRoleConfigurator disabledStopWorkspaceRoleProvisioner =
        new OpenShiftStopWorkspaceRoleConfigurator(
            cheClientFactory, cheInstallationLocation, false, "yes");
    disabledStopWorkspaceRoleProvisioner.configure(null, "developer-che");
    verify(client, never()).rbac();
  }

  @Test
  public void shouldNotCreateRoleBindingWhenInstallationLocationIsNull()
      throws InfrastructureException {
    lenient().when(cheInstallationLocation.getInstallationLocationNamespace()).thenReturn(null);
    OpenShiftStopWorkspaceRoleConfigurator
        stopWorkspaceRoleProvisionerWithoutValidInstallationLocation =
            new OpenShiftStopWorkspaceRoleConfigurator(
                cheClientFactory, cheInstallationLocation, true, "yes");
    stopWorkspaceRoleProvisionerWithoutValidInstallationLocation.configure(null, "developer-che");
    verify(client, never()).rbac();
  }

  @Test
  public void shouldNotCallStopWorkspaceRoleProvisionWhenIdentityProviderIsDefined()
      throws Exception {
    when(cheInstallationLocation.getInstallationLocationNamespace()).thenReturn("something");
    OpenShiftStopWorkspaceRoleConfigurator configurator =
        new OpenShiftStopWorkspaceRoleConfigurator(
            cheClientFactory, cheInstallationLocation, true, null);

    configurator.configure(null, "something");

    verify(cheClientFactory, times(0)).create();
  }
}
