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

import static java.util.Optional.empty;
import static org.eclipse.che.workspace.infrastructure.kubernetes.devworkspace.KubernetesDevWorkspaces.DEV_WORKSPACE_CONTEXT;
import static org.eclipse.che.workspace.infrastructure.kubernetes.devworkspace.KubernetesDevWorkspaces.PAGE_SIZE;
import static org.mockito.Mockito.lenient;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
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
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesDevWorkspacesTest {

  private static final String DEV_WORKSPACES_PATH =
      "/apis/workspace.devfile.io/v1alpha2/devworkspaces";
  private static final String TERMINAL_SELECTOR = "console.openshift.io%2Fterminal%21%3Dtrue";
  private static final String FIRST_PAGE_PATH =
      DEV_WORKSPACES_PATH + "?labelSelector=" + TERMINAL_SELECTOR + "&limit=" + PAGE_SIZE;
  private static final String SECOND_PAGE_PATH =
      DEV_WORKSPACES_PATH
          + "?continue=continue-token&labelSelector="
          + TERMINAL_SELECTOR
          + "&limit="
          + PAGE_SIZE;

  private static final String NAMESPACE = "alice-che";
  private static final String OTHER_NAMESPACE = "bob-che";

  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;

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

    devWorkspaces = new KubernetesDevWorkspaces(cheServerKubernetesClientFactory);
  }

  @AfterMethod
  public void tearDown() {
    kubernetesMockServer.destroy();
  }

  @Test
  public void shouldNotReturnTheDevWorkspacesThatAreNotRunning() throws Exception {
    createDevWorkspace(NAMESPACE, "running", "Running");
    createDevWorkspace(NAMESPACE, "stopped", "Stopped");
    createDevWorkspace(NAMESPACE, "stopping", "Stopping");
    createDevWorkspace(NAMESPACE, "failed", "Failed");

    assertEquals(names(devWorkspaces.getRunningInAllNamespaces()), List.of("running"));
  }

  /** A DevWorkspace the operator has not reconciled yet has no status at all. */
  @Test
  public void shouldNotTreatADevWorkspaceWithoutAStatusAsRunning() throws Exception {
    createDevWorkspace(NAMESPACE, "fresh", Map.of(), null);

    assertTrue(devWorkspaces.getRunningInAllNamespaces().isEmpty());
  }

  @Test
  public void shouldReturnTheRunningDevWorkspacesOfEveryNamespace() throws Exception {
    createDevWorkspace(NAMESPACE, "running", "Running");
    createDevWorkspace(NAMESPACE, "stopped", "Stopped");
    createDevWorkspace(OTHER_NAMESPACE, "running-elsewhere", "Running");

    assertEquals(
        names(devWorkspaces.getRunningInAllNamespaces()), List.of("running", "running-elsewhere"));
  }

  /** A workspace that is being brought up already serves the secrets of the user. */
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

  @Test
  public void shouldReturnTheCreatorOfTheDevWorkspace() {
    GenericKubernetesResource devWorkspace = new GenericKubernetesResource();
    devWorkspace.setMetadata(
        new ObjectMetaBuilder()
            .withLabels(Map.of("controller.devfile.io/creator", "user123"))
            .build());

    assertEquals(KubernetesDevWorkspaces.getCreator(devWorkspace), Optional.of("user123"));
  }

  /**
   * An identity without a {@code uid}, an OIDC one for instance, leaves the operator nothing to
   * record, and an empty creator says nothing about who owns the DevWorkspace.
   */
  @Test
  public void shouldReturnNoCreatorWhenTheOperatorRecordedNone() {
    GenericKubernetesResource withoutLabels = new GenericKubernetesResource();
    withoutLabels.setMetadata(new ObjectMetaBuilder().build());
    GenericKubernetesResource withEmptyCreator = new GenericKubernetesResource();
    withEmptyCreator.setMetadata(
        new ObjectMetaBuilder().withLabels(Map.of("controller.devfile.io/creator", "")).build());

    assertEquals(KubernetesDevWorkspaces.getCreator(new GenericKubernetesResource()), empty());
    assertEquals(KubernetesDevWorkspaces.getCreator(withoutLabels), empty());
    assertEquals(KubernetesDevWorkspaces.getCreator(withEmptyCreator), empty());
  }

  /** The web terminals of the OpenShift console belong to the operator that owns them. */
  @Test
  public void shouldNotReturnTheWebTerminalDevWorkspaces() throws Exception {
    createDevWorkspace(NAMESPACE, "workspace", "Running");
    createDevWorkspace(
        NAMESPACE,
        "web-terminal",
        Map.of("console.openshift.io/terminal", "true"),
        Map.of("phase", "Running"));

    assertEquals(names(devWorkspaces.getRunningInAllNamespaces()), List.of("workspace"));
    // the API server does the leaving out, the response never carries the terminal
    assertTrue(
        kubernetesMockServer
            .getLastRequest()
            .getPath()
            .contains("labelSelector=console.openshift.io%2Fterminal%21%3Dtrue"));
  }

  /**
   * A cluster holds more DevWorkspaces than one response should carry, so they are read page by
   * page and the running ones of every page are kept.
   */
  @Test
  public void shouldReadTheDevWorkspacesPageByPage() throws Exception {
    kubernetesMockServer
        .expect()
        .get()
        .withPath(FIRST_PAGE_PATH)
        .andReturn(200, pageOf("continue-token", runningDevWorkspace("first")))
        .once();
    kubernetesMockServer
        .expect()
        .get()
        .withPath(SECOND_PAGE_PATH)
        .andReturn(200, pageOf(null, runningDevWorkspace("second"), stoppedDevWorkspace("third")))
        .once();

    assertEquals(names(devWorkspaces.getRunningInAllNamespaces()), List.of("first", "second"));
  }

  /** The walk is given up on when a page is lost, the pages read until then are kept. */
  @Test
  public void shouldReturnThePagesReadSoFarWhenAPageCannotBeRead() throws Exception {
    kubernetesMockServer
        .expect()
        .get()
        .withPath(FIRST_PAGE_PATH)
        .andReturn(200, pageOf("continue-token", runningDevWorkspace("first")))
        .once();
    kubernetesMockServer
        .expect()
        .get()
        .withPath(SECOND_PAGE_PATH)
        .andReturn(410, "the continue token has expired")
        .always();

    assertEquals(names(devWorkspaces.getRunningInAllNamespaces()), List.of("first"));
  }

  /** A Kubernetes API failure is reported as an {@link InfrastructureException}. */
  @Test(expectedExceptions = InfrastructureException.class)
  public void shouldFailWhenTheDevWorkspacesCannotBeRead() throws Exception {
    kubernetesMockServer
        .expect()
        .get()
        .withPath(FIRST_PAGE_PATH)
        .andReturn(403, "forbidden")
        .always();

    devWorkspaces.getRunningInAllNamespaces();
  }

  private static GenericKubernetesResourceList pageOf(
      String continueToken, GenericKubernetesResource... devWorkspaces) {
    GenericKubernetesResourceList page = new GenericKubernetesResourceList();
    page.setApiVersion("workspace.devfile.io/v1alpha2");
    page.setKind("DevWorkspaceList");
    page.setMetadata(new ListMetaBuilder().withContinue(continueToken).build());
    page.setItems(List.of(devWorkspaces));
    return page;
  }

  private static GenericKubernetesResource runningDevWorkspace(String name) {
    return devWorkspaceOf(name, "Running");
  }

  private static GenericKubernetesResource stoppedDevWorkspace(String name) {
    return devWorkspaceOf(name, "Stopped");
  }

  private static GenericKubernetesResource devWorkspaceOf(String name, String phase) {
    GenericKubernetesResource devWorkspace = new GenericKubernetesResource();
    devWorkspace.setApiVersion("workspace.devfile.io/v1alpha2");
    devWorkspace.setKind("DevWorkspace");
    devWorkspace.setMetadata(
        new ObjectMetaBuilder().withName(name).withNamespace(NAMESPACE).build());
    devWorkspace.setAdditionalProperty("status", Map.of("phase", phase));
    return devWorkspace;
  }

  private List<String> names(List<GenericKubernetesResource> devWorkspaces) {
    return devWorkspaces.stream()
        .map(devWorkspace -> devWorkspace.getMetadata().getName())
        .sorted()
        .collect(Collectors.toList());
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
