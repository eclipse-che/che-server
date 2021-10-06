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
package org.eclipse.che.workspace.infrastructure.openshift.devfile;

import static io.fabric8.kubernetes.client.utils.Serialization.unmarshal;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.eclipse.che.api.workspace.server.devfile.Constants.KUBERNETES_COMPONENT_TYPE;
import static org.eclipse.che.api.workspace.server.devfile.Constants.OPENSHIFT_COMPONENT_TYPE;
import static org.eclipse.che.workspace.infrastructure.kubernetes.server.external.MultiHostExternalServiceExposureStrategy.MULTI_HOST_STRATEGY;
import static org.eclipse.che.workspace.infrastructure.kubernetes.server.external.SingleHostExternalServiceExposureStrategy.SINGLE_HOST_STRATEGY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.KubernetesList;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.che.api.core.ValidationException;
import org.eclipse.che.api.core.model.workspace.config.ServerConfig;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;
import org.eclipse.che.api.workspace.server.model.impl.MachineConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.devfile.ComponentImpl;
import org.eclipse.che.api.workspace.server.model.impl.devfile.EndpointImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.devfile.KubernetesComponentToWorkspaceApplier;
import org.eclipse.che.workspace.infrastructure.kubernetes.devfile.KubernetesEnvironmentProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesRecipeParser;
import org.eclipse.che.workspace.infrastructure.kubernetes.util.EnvVars;
import org.eclipse.che.workspace.infrastructure.openshift.environment.OpenShiftEnvironment;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.testng.reporters.Files;

@Listeners(MockitoTestNGListener.class)
public class OpenshiftComponentToWorkspaceApplierTest {
  public static final String REFERENCE_FILENAME = "reference.yaml";
  public static final String COMPONENT_NAME = "foo";

  private WorkspaceConfigImpl workspaceConfig;

  private KubernetesComponentToWorkspaceApplier applier;
  @Mock private KubernetesEnvironmentProvisioner k8sEnvProvisioner;
  @Mock private KubernetesRecipeParser k8sRecipeParser;
  @Mock private EnvVars envVars;

  @BeforeMethod
  public void setUp() {
    Set<String> k8sBasedComponents = new HashSet<>();
    k8sBasedComponents.add(KUBERNETES_COMPONENT_TYPE);
    applier =
        new OpenshiftComponentToWorkspaceApplier(
            k8sRecipeParser,
            k8sEnvProvisioner,
            envVars,
            "/projects",
            "1Gi",
            "ReadWriteOnce",
            "",
            "Always",
            MULTI_HOST_STRATEGY,
            k8sBasedComponents);

    workspaceConfig = new WorkspaceConfigImpl();
  }

  @Test
  public void shouldProvisionEnvironmentWithCorrectRecipeTypeAndContentFromOSList()
      throws Exception {
    // given
    doReturn(emptyList()).when(k8sRecipeParser).parse(anyString());
    ComponentImpl component = new ComponentImpl();
    component.setType(KUBERNETES_COMPONENT_TYPE);
    component.setReference(REFERENCE_FILENAME);
    component.setAlias(COMPONENT_NAME);

    // when
    applier.apply(workspaceConfig, component, s -> "content");

    // then
    verify(k8sEnvProvisioner)
        .provision(workspaceConfig, OpenShiftEnvironment.TYPE, emptyList(), emptyMap());
  }

  @Test
  public void serverCantHaveRequireSubdomainWhenSinglehostDevfileExpose()
      throws DevfileException, IOException, ValidationException, InfrastructureException {
    Set<String> openshiftBasedComponents = new HashSet<>();
    openshiftBasedComponents.add(OPENSHIFT_COMPONENT_TYPE);
    applier =
        new OpenshiftComponentToWorkspaceApplier(
            k8sRecipeParser,
            k8sEnvProvisioner,
            envVars,
            "/projects",
            "1Gi",
            "ReadWriteOnce",
            "",
            "Always",
            SINGLE_HOST_STRATEGY,
            openshiftBasedComponents);

    String yamlRecipeContent = getResource("devfile/petclinic.yaml");
    doReturn(toK8SList(yamlRecipeContent).getItems()).when(k8sRecipeParser).parse(anyString());

    // given
    ComponentImpl component = new ComponentImpl();
    component.setType(OPENSHIFT_COMPONENT_TYPE);
    component.setReference(REFERENCE_FILENAME);
    component.setAlias(COMPONENT_NAME);
    component.setEndpoints(
        Arrays.asList(
            new EndpointImpl("e1", 1111, emptyMap()), new EndpointImpl("e2", 2222, emptyMap())));

    // when
    applier.apply(workspaceConfig, component, s -> yamlRecipeContent);

    // then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, MachineConfigImpl>> objectsCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(k8sEnvProvisioner).provision(any(), any(), any(), objectsCaptor.capture());
    Map<String, MachineConfigImpl> machineConfigs = objectsCaptor.getValue();
    assertEquals(machineConfigs.size(), 4);
    machineConfigs
        .values()
        .forEach(
            machineConfig -> {
              assertEquals(machineConfig.getServers().size(), 2);
              assertFalse(
                  ServerConfig.isRequireSubdomain(
                      machineConfig.getServers().get("e1").getAttributes()));
              assertFalse(
                  ServerConfig.isRequireSubdomain(
                      machineConfig.getServers().get("e2").getAttributes()));
            });
  }

  @Test
  public void serverMustHaveRequireSubdomainWhenNonSinglehostDevfileExpose()
      throws DevfileException, IOException, ValidationException, InfrastructureException {
    Set<String> openshiftBasedComponents = new HashSet<>();
    openshiftBasedComponents.add(OPENSHIFT_COMPONENT_TYPE);
    applier =
        new OpenshiftComponentToWorkspaceApplier(
            k8sRecipeParser,
            k8sEnvProvisioner,
            envVars,
            "/projects",
            "1Gi",
            "ReadWriteOnce",
            "",
            "Always",
            MULTI_HOST_STRATEGY,
            openshiftBasedComponents);

    String yamlRecipeContent = getResource("devfile/petclinic.yaml");
    doReturn(toK8SList(yamlRecipeContent).getItems()).when(k8sRecipeParser).parse(anyString());

    // given
    ComponentImpl component = new ComponentImpl();
    component.setType(OPENSHIFT_COMPONENT_TYPE);
    component.setReference(REFERENCE_FILENAME);
    component.setAlias(COMPONENT_NAME);
    component.setEndpoints(
        Arrays.asList(
            new EndpointImpl("e1", 1111, emptyMap()), new EndpointImpl("e2", 2222, emptyMap())));

    // when
    applier.apply(workspaceConfig, component, s -> yamlRecipeContent);

    // then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, MachineConfigImpl>> objectsCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(k8sEnvProvisioner).provision(any(), any(), any(), objectsCaptor.capture());
    Map<String, MachineConfigImpl> machineConfigs = objectsCaptor.getValue();
    assertEquals(machineConfigs.size(), 4);
    machineConfigs
        .values()
        .forEach(
            machineConfig -> {
              assertEquals(machineConfig.getServers().size(), 2);
              assertTrue(
                  ServerConfig.isRequireSubdomain(
                      machineConfig.getServers().get("e1").getAttributes()));
              assertTrue(
                  ServerConfig.isRequireSubdomain(
                      machineConfig.getServers().get("e2").getAttributes()));
            });
  }

  private KubernetesList toK8SList(String content) {
    return unmarshal(content, KubernetesList.class);
  }

  private String getResource(String resourceName) throws IOException {
    return Files.readFile(getClass().getClassLoader().getResourceAsStream(resourceName));
  }
}
