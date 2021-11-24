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
package org.eclipse.che.workspace.infrastructure.openshift.project;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta.DEFAULT_ATTRIBUTE;
import static org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta.PHASE_ATTRIBUTE;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.CREDENTIALS_SECRET_NAME;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.PREFERENCES_CONFIGMAP_NAME;
import static org.eclipse.che.workspace.infrastructure.openshift.Constants.PROJECT_DESCRIPTION_ANNOTATION;
import static org.eclipse.che.workspace.infrastructure.openshift.Constants.PROJECT_DESCRIPTION_ATTRIBUTE;
import static org.eclipse.che.workspace.infrastructure.openshift.Constants.PROJECT_DISPLAY_NAME_ANNOTATION;
import static org.eclipse.che.workspace.infrastructure.openshift.Constants.PROJECT_DISPLAY_NAME_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.api.model.ProjectList;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.dsl.ProjectOperation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.che.api.core.ValidationException;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.model.impl.RuntimeIdentityImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesConfigsMaps;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesSecrets;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator.CredentialsSecretConfigurator;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator.NamespaceConfigurator;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator.PreferencesConfigMapConfigurator;
import org.eclipse.che.workspace.infrastructure.kubernetes.util.KubernetesSharedPool;
import org.eclipse.che.workspace.infrastructure.openshift.CheServerOpenshiftClientFactory;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.eclipse.che.workspace.infrastructure.openshift.project.configurator.OpenShiftWorkspaceServiceAccountConfigurator;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests {@link OpenShiftProjectFactory}.
 *
 * @author Sergii Leshchenko
 */
@Listeners(MockitoTestNGListener.class)
public class OpenShiftProjectFactoryTest {

  private static final String USER_ID = "2342-2559-234";
  private static final String USER_NAME = "johndoe";
  private static final String NO_OAUTH_IDENTITY_PROVIDER = null;
  private static final String OAUTH_IDENTITY_PROVIDER = "openshift-v4";
  private static final String NAMESPACE_LABEL_NAME = "component";
  private static final String NAMESPACE_LABELS = NAMESPACE_LABEL_NAME + "=workspace";
  private static final String NAMESPACE_ANNOTATION_NAME = "owner";
  private static final String NAMESPACE_ANNOTATIONS = NAMESPACE_ANNOTATION_NAME + "=<username>";

  //  @Mock private OpenShiftClientConfigFactory configFactory;
  @Mock private OpenShiftClientFactory clientFactory;
  @Mock private CheServerKubernetesClientFactory cheClientFactory;
  @Mock private CheServerOpenshiftClientFactory cheServerOpenshiftClientFactory;
  @Mock private WorkspaceManager workspaceManager;
  @Mock private UserManager userManager;
  @Mock private PreferenceManager preferenceManager;
  @Mock private KubernetesSharedPool pool;

  @Mock private ProjectOperation projectOperation;

  @Mock private Resource<Project> projectResource;

  @Mock private OpenShiftClient osClient;

  private OpenShiftProjectFactory projectFactory;

  @Mock private FilterWatchListDeletable<Project, ProjectList> projectListResource;

  @Mock private ProjectList projectList;

  @BeforeMethod
  public void setUp() throws Exception {
    lenient().when(clientFactory.createOC()).thenReturn(osClient);
    lenient().when(clientFactory.create()).thenReturn(osClient);
    lenient().when(osClient.projects()).thenReturn(projectOperation);

    lenient()
        .when(workspaceManager.getWorkspace(any()))
        .thenReturn(WorkspaceImpl.builder().setId("1").setAttributes(emptyMap()).build());

    lenient().when(projectOperation.withName(any())).thenReturn(projectResource);
    lenient().when(projectResource.get()).thenReturn(mock(Project.class));

    lenient().when(projectOperation.withLabels(any())).thenReturn(projectListResource);
    lenient().when(projectListResource.list()).thenReturn(projectList);
    lenient().when(projectList.getItems()).thenReturn(emptyList());

    lenient()
        .when(userManager.getById(USER_ID))
        .thenReturn(new UserImpl(USER_ID, "test@mail.com", USER_NAME));
    EnvironmentContext.getCurrent()
        .setSubject(new SubjectImpl(USER_NAME, USER_ID, "t-354t53xff34234", false));
  }

  @AfterMethod
  public void cleanup() {
    EnvironmentContext.reset();
  }

  @Test
  public void shouldNotThrowExceptionIfDefaultNamespaceIsSpecifiedOnCheckingIfNamespaceIsAllowed()
      throws Exception {

    projectFactory =
        new OpenShiftProjectFactory(
            "<username>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);

    projectFactory.checkIfNamespaceIsAllowed(USER_NAME + "-che");
  }

  @Test(
      expectedExceptions = ValidationException.class,
      expectedExceptionsMessageRegExp =
          "User defined namespaces are not allowed. Only the default namespace 'johndoe-che' is available.")
  public void
      shouldThrowExceptionIfNonDefaultNamespaceIsSpecifiedAndUserDefinedAreNotAllowedOnCheckingIfNamespaceIsAllowed()
          throws Exception {
    System.out.println("0--------");
    System.out.println(EnvironmentContext.getCurrent().getSubject());
    System.out.println("2--------");
    projectFactory =
        new OpenShiftProjectFactory(
            "<username>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);
    try {
      projectFactory.checkIfNamespaceIsAllowed("any-namespace");
    } catch (ValidationException e) {
      e.printStackTrace();
      throw e;
    }
  }

  @Test(
      expectedExceptions = ConfigurationException.class,
      expectedExceptionsMessageRegExp = "che.infra.kubernetes.namespace.default must be configured")
  public void
      shouldThrowExceptionIfNoDefaultNamespaceIsConfiguredAndUserDefinedNamespacesAreNotAllowed()
          throws Exception {
    projectFactory =
        new OpenShiftProjectFactory(
            null,
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);
  }

  @Test
  public void shouldReturnPreparedNamespacesWhenFound() throws InfrastructureException {
    // given
    List<Project> projects =
        Arrays.asList(
            createProject(
                "ns1", "project1", "desc1", "Active", Map.of(NAMESPACE_ANNOTATION_NAME, "jondoe")),
            createProject(
                "ns3",
                "project3",
                "desc3",
                "Active",
                Map.of(NAMESPACE_ANNOTATION_NAME, "some_other_user")),
            createProject(
                "ns2", "project2", "desc2", "Active", Map.of(NAMESPACE_ANNOTATION_NAME, "jondoe")));
    doReturn(projects).when(projectList).getItems();

    projectFactory =
        new OpenShiftProjectFactory(
            "<userid>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);
    EnvironmentContext.getCurrent().setSubject(new SubjectImpl("jondoe", "123", null, false));

    // when
    List<KubernetesNamespaceMeta> availableNamespaces = projectFactory.list();

    // then
    assertEquals(availableNamespaces.size(), 2);
    assertEquals(availableNamespaces.get(0).getName(), "ns1");
    assertEquals(availableNamespaces.get(1).getName(), "ns2");
  }

  @Test
  public void shouldNotThrowAnExceptionWhenNotAllowedToListNamespaces() throws Exception {
    // given
    Project p = createProject("ns1", "project1", "desc1", "Active");
    doThrow(new KubernetesClientException("Not allowed.", 403, new Status()))
        .when(projectList)
        .getItems();
    prepareNamespaceToBeFoundByName("u123-che", p);

    projectFactory =
        new OpenShiftProjectFactory(
            "<userid>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);
    EnvironmentContext.getCurrent().setSubject(new SubjectImpl("jondoe", "u123", null, false));

    // when
    List<KubernetesNamespaceMeta> availableNamespaces = projectFactory.list();

    // then
    assertEquals(availableNamespaces.get(0).getName(), "ns1");
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void throwAnExceptionWhenErrorListingNamespaces() throws Exception {
    // given
    doThrow(new KubernetesClientException("Not allowed.", 500, new Status()))
        .when(projectList)
        .getItems();

    projectFactory =
        new OpenShiftProjectFactory(
            "<userid>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);

    // when
    projectFactory.list();

    // then throw
  }

  @Test
  public void shouldReturnDefaultProjectWhenItExistsAndUserDefinedIsNotAllowed() throws Exception {
    prepareNamespaceToBeFoundByName(
        USER_NAME + "-che",
        new ProjectBuilder()
            .withNewMetadata()
            .withName(USER_NAME + "-che")
            .withAnnotations(
                ImmutableMap.of(
                    PROJECT_DISPLAY_NAME_ANNOTATION,
                    "Default Che Project",
                    PROJECT_DESCRIPTION_ANNOTATION,
                    "some description"))
            .endMetadata()
            .withNewStatus()
            .withPhase("Active")
            .endStatus()
            .build());

    projectFactory =
        new OpenShiftProjectFactory(
            "<username>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);

    List<KubernetesNamespaceMeta> availableNamespaces = projectFactory.list();
    assertEquals(availableNamespaces.size(), 1);
    KubernetesNamespaceMeta defaultNamespace = availableNamespaces.get(0);
    assertEquals(defaultNamespace.getName(), USER_NAME + "-che");
    assertEquals(defaultNamespace.getAttributes().get(DEFAULT_ATTRIBUTE), "true");
    assertEquals(
        defaultNamespace.getAttributes().get(PROJECT_DISPLAY_NAME_ATTRIBUTE),
        "Default Che Project");
    assertEquals(
        defaultNamespace.getAttributes().get(PROJECT_DESCRIPTION_ATTRIBUTE), "some description");
    assertEquals(defaultNamespace.getAttributes().get(PHASE_ATTRIBUTE), "Active");
  }

  @Test
  public void shouldReturnDefaultProjectWhenItDoesNotExistAndUserDefinedIsNotAllowed()
      throws Exception {
    throwOnTryToGetProjectByName(
        USER_NAME + "-che", new KubernetesClientException("forbidden", 403, null));

    projectFactory =
        new OpenShiftProjectFactory(
            "<username>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);

    List<KubernetesNamespaceMeta> availableNamespaces = projectFactory.list();
    assertEquals(availableNamespaces.size(), 1);
    KubernetesNamespaceMeta defaultNamespace = availableNamespaces.get(0);
    assertEquals(defaultNamespace.getName(), USER_NAME + "-che");
    assertEquals(defaultNamespace.getAttributes().get(DEFAULT_ATTRIBUTE), "true");
    assertNull(
        defaultNamespace
            .getAttributes()
            .get(PHASE_ATTRIBUTE)); // no phase - means such project does not exist
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp =
          "Error while trying to fetch the project 'johndoe-che'. Cause: connection refused")
  public void shouldThrowExceptionWhenFailedToGetInfoAboutDefaultNamespace() throws Exception {
    throwOnTryToGetProjectByName(
        USER_NAME + "-che", new KubernetesClientException("connection refused"));

    projectFactory =
        new OpenShiftProjectFactory(
            "<username>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);

    projectFactory.list();
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp =
          "Error occurred when tried to list all available projects. Cause: connection refused")
  public void shouldThrowExceptionWhenFailedToGetNamespaces() throws Exception {
    throwOnTryToGetProjectsList(new KubernetesClientException("connection refused"));
    projectFactory =
        new OpenShiftProjectFactory(
            "<username>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);

    projectFactory.list();
  }

  @Test
  public void shouldRequireNamespacePriorExistenceIfDifferentFromDefaultAndUserDefinedIsNotAllowed()
      throws Exception {
    // There is only one scenario where this can happen. The workspace was created and started in
    // some default namespace. Then server was reconfigured to use a different default namespace
    // AND the namespace of the workspace was MANUALLY deleted in the cluster. In this case, we
    // should NOT try to re-create the namespace because it would be created in a namespace that
    // is not configured. We DO allow it to start if the namespace still exists though.

    // given
    projectFactory =
        spy(
            new OpenShiftProjectFactory(
                "<userid>-che",
                true,
                true,
                true,
                NAMESPACE_LABELS,
                NAMESPACE_ANNOTATIONS,
                true,
                emptySet(),
                clientFactory,
                cheClientFactory,
                cheServerOpenshiftClientFactory,
                userManager,
                preferenceManager,
                pool,
                NO_OAUTH_IDENTITY_PROVIDER));
    OpenShiftProject toReturnProject = mock(OpenShiftProject.class);
    prepareProject(toReturnProject);
    doReturn(toReturnProject).when(projectFactory).doCreateProjectAccess(any(), any());

    // when
    RuntimeIdentity identity =
        new RuntimeIdentityImpl("workspace123", null, USER_ID, "old-default");
    OpenShiftProject project = projectFactory.getOrCreate(identity);

    // then
    assertEquals(toReturnProject, project);
    verify(toReturnProject).prepare(eq(false), eq(false), any(), any());
  }

  @Test
  public void shouldCreateCredentialsSecretIfNotExists() throws Exception {
    // given
    projectFactory =
        spy(
            new OpenShiftProjectFactory(
                "<userid>-che",
                true,
                true,
                true,
                NAMESPACE_LABELS,
                NAMESPACE_ANNOTATIONS,
                true,
                Set.of(new CredentialsSecretConfigurator(clientFactory)),
                clientFactory,
                cheClientFactory,
                cheServerOpenshiftClientFactory,
                userManager,
                preferenceManager,
                pool,
                NO_OAUTH_IDENTITY_PROVIDER));
    OpenShiftProject toReturnProject = mock(OpenShiftProject.class);
    doReturn(toReturnProject).when(projectFactory).doCreateProjectAccess(any(), any());
    when(toReturnProject.getName()).thenReturn("namespace123");
    NonNamespaceOperation namespaceOperation = mock(NonNamespaceOperation.class);
    MixedOperation mixedOperation = mock(MixedOperation.class);
    when(osClient.secrets()).thenReturn(mixedOperation);
    when(mixedOperation.inNamespace(anyString())).thenReturn(namespaceOperation);
    Resource<Secret> nullSecret = mock(Resource.class);
    when(namespaceOperation.withName(CREDENTIALS_SECRET_NAME)).thenReturn(nullSecret);
    when(nullSecret.get()).thenReturn(null);

    // when
    RuntimeIdentity identity =
        new RuntimeIdentityImpl("workspace123", null, USER_ID, "namespace123");
    projectFactory.getOrCreate(identity);

    // then
    ArgumentCaptor<Secret> secretsCaptor = ArgumentCaptor.forClass(Secret.class);
    verify(namespaceOperation).create(secretsCaptor.capture());
    Secret secret = secretsCaptor.getValue();
    Assert.assertEquals(secret.getMetadata().getName(), CREDENTIALS_SECRET_NAME);
    Assert.assertEquals(secret.getType(), "opaque");
  }

  @Test
  public void shouldCreatePreferencesConfigmapIfNotExists() throws Exception {
    // given
    projectFactory =
        spy(
            new OpenShiftProjectFactory(
                "<userid>-che",
                true,
                true,
                true,
                NAMESPACE_LABELS,
                NAMESPACE_ANNOTATIONS,
                true,
                Set.of(new PreferencesConfigMapConfigurator(clientFactory)),
                clientFactory,
                cheClientFactory,
                cheServerOpenshiftClientFactory,
                userManager,
                preferenceManager,
                pool,
                NO_OAUTH_IDENTITY_PROVIDER));
    OpenShiftProject toReturnProject = mock(OpenShiftProject.class);
    doReturn(toReturnProject).when(projectFactory).doCreateProjectAccess(any(), any());
    when(toReturnProject.getName()).thenReturn("namespace123");
    NonNamespaceOperation namespaceOperation = mock(NonNamespaceOperation.class);
    MixedOperation mixedOperation = mock(MixedOperation.class);
    when(osClient.configMaps()).thenReturn(mixedOperation);
    when(mixedOperation.inNamespace(anyString())).thenReturn(namespaceOperation);
    Resource<ConfigMap> nullCm = mock(Resource.class);
    when(namespaceOperation.withName(PREFERENCES_CONFIGMAP_NAME)).thenReturn(nullCm);

    // when
    RuntimeIdentity identity =
        new RuntimeIdentityImpl("workspace123", null, USER_ID, "workspace123");
    projectFactory.getOrCreate(identity);

    // then
    ArgumentCaptor<ConfigMap> configMapCaptor = ArgumentCaptor.forClass(ConfigMap.class);
    verify(namespaceOperation).create(configMapCaptor.capture());
    ConfigMap configmap = configMapCaptor.getValue();
    Assert.assertEquals(configmap.getMetadata().getName(), PREFERENCES_CONFIGMAP_NAME);
  }

  @Test
  public void shouldNotCreateCredentialsSecretIfExist() throws Exception {
    // given
    projectFactory =
        spy(
            new OpenShiftProjectFactory(
                "<userid>-che",
                true,
                true,
                true,
                NAMESPACE_LABELS,
                NAMESPACE_ANNOTATIONS,
                true,
                Set.of(new CredentialsSecretConfigurator(clientFactory)),
                clientFactory,
                cheClientFactory,
                cheServerOpenshiftClientFactory,
                userManager,
                preferenceManager,
                pool,
                NO_OAUTH_IDENTITY_PROVIDER));
    OpenShiftProject toReturnProject = mock(OpenShiftProject.class);
    prepareProject(toReturnProject);
    doReturn(toReturnProject).when(projectFactory).doCreateProjectAccess(any(), any());
    when(toReturnProject.getName()).thenReturn("namespace123");
    NonNamespaceOperation namespaceOperation = mock(NonNamespaceOperation.class);
    MixedOperation mixedOperation = mock(MixedOperation.class);
    when(osClient.secrets()).thenReturn(mixedOperation);
    when(mixedOperation.inNamespace(anyString())).thenReturn(namespaceOperation);
    Resource<Secret> secretResource = mock(Resource.class);
    when(namespaceOperation.withName(CREDENTIALS_SECRET_NAME)).thenReturn(secretResource);
    when(secretResource.get()).thenReturn(mock(Secret.class));

    // when
    RuntimeIdentity identity =
        new RuntimeIdentityImpl("workspace123", null, USER_ID, "workspace123");
    projectFactory.getOrCreate(identity);

    // then
    verify(namespaceOperation, never()).create(any());
  }

  @Test
  public void shouldNotCreatePreferencesConfigmapIfExist() throws Exception {
    // given
    projectFactory =
        spy(
            new OpenShiftProjectFactory(
                "<userid>-che",
                true,
                true,
                true,
                NAMESPACE_LABELS,
                NAMESPACE_ANNOTATIONS,
                true,
                Set.of(new PreferencesConfigMapConfigurator(clientFactory)),
                clientFactory,
                cheClientFactory,
                cheServerOpenshiftClientFactory,
                userManager,
                preferenceManager,
                pool,
                NO_OAUTH_IDENTITY_PROVIDER));
    OpenShiftProject toReturnProject = mock(OpenShiftProject.class);
    prepareProject(toReturnProject);
    doReturn(toReturnProject).when(projectFactory).doCreateProjectAccess(any(), any());
    when(toReturnProject.getName()).thenReturn("namespace123");
    NonNamespaceOperation namespaceOperation = mock(NonNamespaceOperation.class);
    MixedOperation mixedOperation = mock(MixedOperation.class);
    when(osClient.configMaps()).thenReturn(mixedOperation);
    when(mixedOperation.inNamespace(anyString())).thenReturn(namespaceOperation);
    Resource<ConfigMap> cmResource = mock(Resource.class);
    when(namespaceOperation.withName(PREFERENCES_CONFIGMAP_NAME)).thenReturn(cmResource);
    when(cmResource.get()).thenReturn(mock(ConfigMap.class));

    // when
    RuntimeIdentity identity =
        new RuntimeIdentityImpl("workspace123", null, USER_ID, "workspace123");
    projectFactory.getOrCreate(identity);

    // then
    verify(namespaceOperation, never()).create(any());
  }

  @Test
  public void shouldCallStopWorkspaceRoleProvisionWhenIdentityProviderIsDefined() throws Exception {
    var saConf =
        spy(new OpenShiftWorkspaceServiceAccountConfigurator("serviceAccount", "", clientFactory));
    projectFactory =
        spy(
            new OpenShiftProjectFactory(
                "<userid>-che",
                true,
                true,
                true,
                NAMESPACE_LABELS,
                NAMESPACE_ANNOTATIONS,
                true,
                Set.of(saConf),
                clientFactory,
                cheClientFactory,
                cheServerOpenshiftClientFactory,
                userManager,
                preferenceManager,
                pool,
                OAUTH_IDENTITY_PROVIDER));
    OpenShiftProject toReturnProject = mock(OpenShiftProject.class);
    when(toReturnProject.getName()).thenReturn("workspace123");
    prepareProject(toReturnProject);
    doReturn(toReturnProject).when(projectFactory).doCreateProjectAccess(any(), any());

    OpenShiftWorkspaceServiceAccount serviceAccount = mock(OpenShiftWorkspaceServiceAccount.class);
    doReturn(serviceAccount).when(saConf).createServiceAccount("workspace123", "workspace123");

    // when
    RuntimeIdentity identity =
        new RuntimeIdentityImpl("workspace123", null, USER_ID, "workspace123");
    projectFactory.getOrCreate(identity);

    // then
    verify(serviceAccount).prepare();
  }

  @Test
  public void testEvalNamespaceNameWhenPreparedNamespacesFound() throws InfrastructureException {
    List<Project> projects =
        Arrays.asList(
            createProject(
                "ns1", "project1", "desc1", "Active", Map.of(NAMESPACE_ANNOTATION_NAME, "jondoe")),
            createProject(
                "ns3",
                "project3",
                "desc3",
                "Active",
                Map.of(NAMESPACE_ANNOTATION_NAME, "some_other_user")),
            createProject(
                "ns2", "project2", "desc2", "Active", Map.of(NAMESPACE_ANNOTATION_NAME, "jondoe")));
    doReturn(projects).when(projectList).getItems();

    projectFactory =
        new OpenShiftProjectFactory(
            "<userid>-che",
            true,
            true,
            true,
            NAMESPACE_LABELS,
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);

    String namespace =
        projectFactory.evaluateNamespaceName(
            new NamespaceResolutionContext("workspace123", "user123", "jondoe"));

    assertEquals(namespace, "ns1");
  }

  @Test
  public void testUsernamePlaceholderInLabelsIsNotEvaluated() throws InfrastructureException {
    List<Project> projects =
        singletonList(
            createProject(
                "ns1", "project1", "desc1", "Active", Map.of(NAMESPACE_ANNOTATION_NAME, "jondoe")));
    doReturn(projects).when(projectList).getItems();

    projectFactory =
        new OpenShiftProjectFactory(
            "<userid>-che",
            true,
            true,
            true,
            "try_placeholder_here=<username>",
            NAMESPACE_ANNOTATIONS,
            true,
            emptySet(),
            clientFactory,
            cheClientFactory,
            cheServerOpenshiftClientFactory,
            userManager,
            preferenceManager,
            pool,
            NO_OAUTH_IDENTITY_PROVIDER);
    EnvironmentContext.getCurrent().setSubject(new SubjectImpl("jondoe", "123", null, false));
    projectFactory.list();

    verify(projectOperation).withLabels(Map.of("try_placeholder_here", "<username>"));
  }

  @Test
  public void testUsernamePlaceholderInAnnotationsIsEvaluated() throws InfrastructureException {
    // given
    projectFactory =
        spy(
            new OpenShiftProjectFactory(
                "<userid>-che",
                true,
                true,
                true,
                NAMESPACE_LABELS,
                "try_placeholder_here=<username>",
                true,
                emptySet(),
                clientFactory,
                cheClientFactory,
                cheServerOpenshiftClientFactory,
                userManager,
                preferenceManager,
                pool,
                NO_OAUTH_IDENTITY_PROVIDER));
    EnvironmentContext.getCurrent().setSubject(new SubjectImpl("jondoe", "123", null, false));
    OpenShiftProject toReturnProject = mock(OpenShiftProject.class);
    prepareProject(toReturnProject);
    doReturn(toReturnProject).when(projectFactory).doCreateProjectAccess(any(), any());

    // when
    RuntimeIdentity identity = new RuntimeIdentityImpl("workspace123", null, USER_ID, "old-che");
    OpenShiftProject project = projectFactory.getOrCreate(identity);

    // then
    assertEquals(toReturnProject, project);
    verify(toReturnProject)
        .prepare(eq(false), eq(false), any(), eq(Map.of("try_placeholder_here", "jondoe")));
  }

  @Test
  public void testAllConfiguratorsAreCalledWhenCreatingProject() throws InfrastructureException {
    // given
    String projectName = "testprojectname";
    NamespaceConfigurator configurator1 = Mockito.mock(NamespaceConfigurator.class);
    NamespaceConfigurator configurator2 = Mockito.mock(NamespaceConfigurator.class);
    Set<NamespaceConfigurator> namespaceConfigurators = Set.of(configurator1, configurator2);

    projectFactory =
        spy(
            new OpenShiftProjectFactory(
                "<username>-che",
                true,
                true,
                true,
                NAMESPACE_LABELS,
                "try_placeholder_here=<username>",
                true,
                namespaceConfigurators,
                clientFactory,
                cheClientFactory,
                cheServerOpenshiftClientFactory,
                userManager,
                preferenceManager,
                pool,
                NO_OAUTH_IDENTITY_PROVIDER));
    EnvironmentContext.getCurrent().setSubject(new SubjectImpl("jondoe", "123", null, false));

    OpenShiftProject toReturnProject = mock(OpenShiftProject.class);
    when(toReturnProject.getName()).thenReturn(projectName);

    RuntimeIdentity identity = new RuntimeIdentityImpl("workspace123", null, USER_ID, "old-che");
    doReturn(toReturnProject).when(projectFactory).get(identity);

    // when
    OpenShiftProject project = projectFactory.getOrCreate(identity);

    // then
    NamespaceResolutionContext resolutionCtx =
        new NamespaceResolutionContext("workspace123", "123", "jondoe");
    verify(configurator1).configure(resolutionCtx, projectName);
    verify(configurator2).configure(resolutionCtx, projectName);
    assertEquals(project, toReturnProject);
  }

  private void prepareNamespaceToBeFoundByName(String name, Project project) throws Exception {
    @SuppressWarnings("unchecked")
    Resource<Project> getProjectByNameOperation = mock(Resource.class);
    when(projectOperation.withName(name)).thenReturn(getProjectByNameOperation);

    when(getProjectByNameOperation.get()).thenReturn(project);
  }

  private void throwOnTryToGetProjectByName(String name, KubernetesClientException e)
      throws Exception {
    @SuppressWarnings("unchecked")
    Resource<Project> getProjectByNameOperation = mock(Resource.class);
    when(projectOperation.withName(name)).thenReturn(getProjectByNameOperation);

    when(getProjectByNameOperation.get()).thenThrow(e);
  }

  private void prepareListedProjects(List<Project> projects) throws Exception {
    @SuppressWarnings("unchecked")
    ProjectList projectList = mock(ProjectList.class);
    when(projectOperation.list()).thenReturn(projectList);

    when(projectList.getItems()).thenReturn(projects);
  }

  private void prepareProject(OpenShiftProject project) throws InfrastructureException {
    KubernetesSecrets secrets = mock(KubernetesSecrets.class);
    lenient().when(project.secrets()).thenReturn(secrets);
    KubernetesConfigsMaps configsMaps = mock(KubernetesConfigsMaps.class);
    Secret secretMock = mock(Secret.class);
    ObjectMeta objectMeta = mock(ObjectMeta.class);
    lenient().when(objectMeta.getName()).thenReturn(CREDENTIALS_SECRET_NAME);
    lenient().when(secretMock.getMetadata()).thenReturn(objectMeta);
    lenient().when(secrets.get()).thenReturn(Collections.singletonList(secretMock));
  }

  private void throwOnTryToGetProjectsList(Throwable e) throws Exception {
    when(projectListResource.list()).thenThrow(e);
  }

  private Project createProject(String name, String displayName, String description, String phase) {
    return createProject(name, displayName, description, phase, emptyMap());
  }

  private Project createProject(
      String name,
      String displayName,
      String description,
      String phase,
      Map<String, String> extraAnnotations) {
    Map<String, String> annotations = new HashMap<>();
    if (displayName != null) {
      annotations.put(PROJECT_DISPLAY_NAME_ANNOTATION, displayName);
    }
    if (description != null) {
      annotations.put(PROJECT_DESCRIPTION_ANNOTATION, description);
    }
    if (extraAnnotations != null) {
      annotations.putAll(extraAnnotations);
    }

    return new ProjectBuilder()
        .withNewMetadata()
        .withName(name)
        .withAnnotations(annotations)
        .endMetadata()
        .withNewStatus()
        .withNewPhase(phase)
        .endStatus()
        .build();
  }
}
