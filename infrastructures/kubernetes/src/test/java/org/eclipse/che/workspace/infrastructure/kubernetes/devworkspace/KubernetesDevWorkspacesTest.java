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
package org.eclipse.che.workspace.infrastructure.kubernetes.devworkspace;

import static java.util.Collections.emptyList;
import static org.eclipse.che.workspace.infrastructure.kubernetes.devworkspace.KubernetesDevWorkspaces.DEV_WORKSPACE_CONTEXT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesDevWorkspacesTest {

  private static final String NAMESPACE = "alice-che";
  private static final String OTHER_NAMESPACE = "bob-che";

  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  @Mock private KubernetesNamespaceFactory namespaceFactory;

  private KubernetesMockServer kubernetesMockServer;
  private KubernetesClient kubernetesClient;
  private KubernetesDevWorkspaces devWorkspaces;

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
    // the tests of the static readers never reach for a client
    lenient().when(cheServerKubernetesClientFactory.create()).thenReturn(kubernetesClient);

    devWorkspaces =
        new KubernetesDevWorkspaces(cheServerKubernetesClientFactory, () -> namespaceFactory);

    EnvironmentContext context = new EnvironmentContext();
    context.setSubject(new SubjectImpl("alice", emptyList(), "alice-id", "token", false));
    EnvironmentContext.setCurrent(context);
  }

  @AfterMethod
  public void tearDown() {
    EnvironmentContext.reset();
    kubernetesMockServer.destroy();
  }

  @Test
  public void shouldReturnTheDevWorkspacesOfTheNamespace() throws Exception {
    createDevWorkspace(NAMESPACE, "first", Map.of());
    createDevWorkspace(NAMESPACE, "second", Map.of());
    createDevWorkspace(OTHER_NAMESPACE, "third", Map.of());

    assertEquals(names(devWorkspaces.get(NAMESPACE)), List.of("first", "second"));
  }

  @Test
  public void shouldReturnEmptyListWhenTheNamespaceHasNoDevWorkspaces() throws Exception {
    assertTrue(devWorkspaces.get(NAMESPACE).isEmpty());
  }

  @Test
  public void shouldReturnTheDevWorkspacesCarryingAllOfTheLabels() throws Exception {
    createDevWorkspace(NAMESPACE, "started", Map.of("controller.devfile.io/started", "true"));
    createDevWorkspace(NAMESPACE, "stopped", Map.of("controller.devfile.io/started", "false"));

    assertEquals(
        names(devWorkspaces.get(NAMESPACE, Map.of("controller.devfile.io/started", "true"))),
        List.of("started"));
  }

  @Test
  public void shouldNotFilterOnEmptyLabels() throws Exception {
    createDevWorkspace(NAMESPACE, "first", Map.of("controller.devfile.io/started", "true"));
    createDevWorkspace(NAMESPACE, "second", Map.of());

    assertEquals(names(devWorkspaces.get(NAMESPACE, Map.of())), List.of("first", "second"));
    assertEquals(names(devWorkspaces.get(NAMESPACE, null)), List.of("first", "second"));
  }

  @Test
  public void shouldReturnTheDevWorkspaceByName() throws Exception {
    createDevWorkspace(NAMESPACE, "first", Map.of());

    Optional<GenericKubernetesResource> devWorkspace = devWorkspaces.getByName(NAMESPACE, "first");

    assertTrue(devWorkspace.isPresent());
    assertEquals(devWorkspace.get().getMetadata().getName(), "first");
    assertEquals(devWorkspace.get().get("status", "mainUrl"), mainUrlOf("first"));
  }

  @Test
  public void shouldReturnEmptyOptionalWhenTheDevWorkspaceDoesNotExist() throws Exception {
    assertFalse(devWorkspaces.getByName(NAMESPACE, "missing").isPresent());
  }

  /** The DevWorkspaces of a user may only be read from the namespace Che resolves for them. */
  @Test
  public void shouldReadTheNamespaceResolvedForTheCurrentUser() throws Exception {
    when(namespaceFactory.evaluateNamespaceName(any(NamespaceResolutionContext.class)))
        .thenReturn(NAMESPACE);
    createDevWorkspace(NAMESPACE, "first", Map.of());
    createDevWorkspace(OTHER_NAMESPACE, "second", Map.of());

    assertEquals(names(devWorkspaces.getForCurrentUser()), List.of("first"));
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void shouldFailWhenTheNamespaceOfTheCurrentUserCannotBeResolved() throws Exception {
    when(namespaceFactory.evaluateNamespaceName(any(NamespaceResolutionContext.class)))
        .thenThrow(new InfrastructureException("no namespace"));

    devWorkspaces.getForCurrentUser();
  }

  @Test
  public void shouldReturnTheRunningDevWorkspaces() throws Exception {
    createDevWorkspace(NAMESPACE, "running", "Running");
    createDevWorkspace(NAMESPACE, "stopped", "Stopped");
    createDevWorkspace(NAMESPACE, "stopping", "Stopping");
    createDevWorkspace(NAMESPACE, "failed", "Failed");

    assertEquals(names(devWorkspaces.getRunning(NAMESPACE)), List.of("running"));
  }

  /** A workspace that is being brought up already serves the secrets of the user. */
  @Test
  public void shouldTreatAStartingDevWorkspaceAsRunning() throws Exception {
    createDevWorkspace(NAMESPACE, "starting", "Starting");

    assertEquals(names(devWorkspaces.getRunning(NAMESPACE)), List.of("starting"));
  }

  /** A DevWorkspace the operator has not reconciled yet has no status at all. */
  @Test
  public void shouldNotTreatADevWorkspaceWithoutAStatusAsRunning() throws Exception {
    createDevWorkspace(NAMESPACE, "fresh", Map.of(), null);

    assertTrue(devWorkspaces.getRunning(NAMESPACE).isEmpty());
  }

  /** The running workspaces of another user say nothing about this namespace. */
  @Test
  public void shouldNotReturnTheRunningDevWorkspacesOfAnotherNamespace() throws Exception {
    createDevWorkspace(OTHER_NAMESPACE, "running", "Running");

    assertTrue(devWorkspaces.getRunning(NAMESPACE).isEmpty());
  }

  @Test
  public void shouldReturnTheRunningDevWorkspacesOfEveryNamespace() throws Exception {
    createDevWorkspace(NAMESPACE, "running", "Running");
    createDevWorkspace(NAMESPACE, "stopped", "Stopped");
    createDevWorkspace(OTHER_NAMESPACE, "running-elsewhere", "Running");

    assertEquals(
        names(devWorkspaces.getRunningInAllNamespaces()), List.of("running", "running-elsewhere"));
  }

  /** The cluster wide read covers the workspaces being brought up, just like the scoped one. */
  @Test
  public void shouldReturnTheStartingDevWorkspacesOfEveryNamespace() throws Exception {
    createDevWorkspace(NAMESPACE, "starting", "Starting");
    createDevWorkspace(NAMESPACE, "stopping", "Stopping");
    createDevWorkspace(NAMESPACE, "failed", "Failed");
    createDevWorkspace(OTHER_NAMESPACE, "starting-elsewhere", "Starting");

    assertEquals(
        names(devWorkspaces.getRunningInAllNamespaces()),
        List.of("starting", "starting-elsewhere"));
  }

  @Test
  public void shouldReturnTheRemotesOfEveryProjectKind() {
    GenericKubernetesResource devWorkspace =
        devWorkspaceWithProjects(
            Map.of(
                "projects",
                List.of(
                    gitProject(Map.of("origin", "https://github.com/che/first.git")),
                    gitProject(
                        Map.of(
                            "origin",
                            "https://github.com/che/second.git",
                            "fork",
                            "https://gitlab.com/che/second.git"))),
                "starterProjects",
                List.of(gitProject(Map.of("origin", "https://github.com/che/starter.git"))),
                "dependentProjects",
                List.of(gitProject(Map.of("origin", "https://github.com/che/dependent.git")))));

    assertEquals(
        KubernetesDevWorkspaces.getRepositoryUrls(devWorkspace),
        Set.of(
            "https://github.com/che/first.git",
            "https://github.com/che/second.git",
            "https://gitlab.com/che/second.git",
            "https://github.com/che/starter.git",
            "https://github.com/che/dependent.git"));
  }

  /** A zip project, and a project of a devfile Che has not filled in, have no git remote. */
  @Test
  public void shouldReturnNoRepositoryUrlForAProjectThatDoesNotComeFromGit() {
    GenericKubernetesResource devWorkspace =
        devWorkspaceWithProjects(
            Map.of(
                "projects",
                List.of(
                    Map.of(
                        "name", "zipped", "zip", Map.of("location", "https://example.com/a.zip")),
                    Map.of("name", "bare"),
                    gitProject(Map.of()))));

    assertTrue(KubernetesDevWorkspaces.getRepositoryUrls(devWorkspace).isEmpty());
  }

  @Test
  public void shouldReturnNoRepositoryUrlForADevWorkspaceWithoutProjects() {
    assertTrue(
        KubernetesDevWorkspaces.getRepositoryUrls(devWorkspaceWithProjects(Map.of())).isEmpty());
    assertTrue(
        KubernetesDevWorkspaces.getRepositoryUrls(new GenericKubernetesResource()).isEmpty());
  }

  /** A field the CRD does not constrain to a single type must not blow the reader up. */
  @Test
  public void shouldSkipProjectsOfAnUnexpectedShape() {
    GenericKubernetesResource devWorkspace = new GenericKubernetesResource();
    devWorkspace.setAdditionalProperty("spec", Map.of("template", Map.of("projects", "nonsense")));

    assertTrue(KubernetesDevWorkspaces.getRepositoryUrls(devWorkspace).isEmpty());
  }

  /** A Kubernetes API failure is reported as an {@link InfrastructureException}. */
  @Test(expectedExceptions = InfrastructureException.class)
  public void shouldFailWhenTheDevWorkspacesCannotBeRead() throws Exception {
    kubernetesMockServer
        .expect()
        .get()
        .withPath("/apis/workspace.devfile.io/v1alpha2/namespaces/" + NAMESPACE + "/devworkspaces")
        .andReturn(403, "forbidden")
        .always();

    devWorkspaces.get(NAMESPACE);
  }

  private List<String> names(List<GenericKubernetesResource> devWorkspaces) {
    return devWorkspaces.stream()
        .map(devWorkspace -> devWorkspace.getMetadata().getName())
        .sorted()
        .collect(Collectors.toList());
  }

  private void createDevWorkspace(String namespace, String name, Map<String, String> labels) {
    createDevWorkspace(namespace, name, labels, Map.of("mainUrl", mainUrlOf(name)));
  }

  private void createDevWorkspace(String namespace, String name, String phase) {
    createDevWorkspace(
        namespace, name, Map.of(), Map.of("mainUrl", mainUrlOf(name), "phase", phase));
  }

  private void createDevWorkspace(
      String namespace, String name, Map<String, String> labels, Map<String, Object> status) {
    GenericKubernetesResource devWorkspace = new GenericKubernetesResource();
    devWorkspace.setApiVersion("workspace.devfile.io/v1alpha2");
    devWorkspace.setKind("DevWorkspace");
    devWorkspace.setMetadata(
        new ObjectMetaBuilder().withName(name).withNamespace(namespace).withLabels(labels).build());
    if (status != null) {
      devWorkspace.setAdditionalProperty("status", status);
    }
    kubernetesClient
        .genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
        .inNamespace(namespace)
        .resource(devWorkspace)
        .create();
  }

  private static GenericKubernetesResource devWorkspaceWithProjects(
      Map<String, Object> templateFields) {
    GenericKubernetesResource devWorkspace = new GenericKubernetesResource();
    devWorkspace.setAdditionalProperty("spec", Map.of("template", templateFields));
    return devWorkspace;
  }

  private static Map<String, Object> gitProject(Map<String, String> remotes) {
    return Map.of("name", "project", "git", Map.of("remotes", remotes));
  }

  private static String mainUrlOf(String name) {
    return "https://che.example.com/alice/" + name + "/3100/";
  }
}
