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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.pvc;

import static com.google.common.collect.ImmutableMap.of;
import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.eclipse.che.api.workspace.shared.Constants.DEBUG_WORKSPACE_START;
import static org.eclipse.che.api.workspace.shared.Constants.DEBUG_WORKSPACE_START_LOG_LIMIT_BYTES;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.pvc.PVCSubPathHelper.JOB_MOUNT_PATH;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.pvc.PVCSubPathHelper.MKDIR_COMMAND_BASE;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.pvc.PVCSubPathHelper.POD_PHASE_FAILED;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.pvc.PVCSubPathHelper.POD_PHASE_SUCCEEDED;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.pvc.PVCSubPathHelper.RM_COMMAND_BASE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.observability.NoopExecutorServiceWrapper;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesDeployments;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespace;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesPersistentVolumeClaims;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.event.PodEvent;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.event.PodEventHandler;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.NodeSelectorProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.SecurityContextProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.TolerationsProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.util.RuntimeEventsPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests {@link PVCSubPathHelper}.
 *
 * @author Anton Korneta
 */
@Listeners(MockitoTestNGListener.class)
public class PVCSubPathHelperTest {

  private static final String WORKSPACE_ID = "workspace132";
  private static final String NAMESPACE = "namespace";
  private static final String PVC_NAME = "che-workspace-claim";
  private static final String PVC_PHASE_BOUND = "Bound";
  private static final String PVC_PHASE_TERMINATING = "Terminating";
  private static final String jobMemoryLimit = "250Mi";
  private static final String jobImage = "centos:centos7";
  private static final String PROJECTS_PATH = "/projects";
  private static final String M2_PATH = "/.m2";

  @Mock private SecurityContextProvisioner securityContextProvisioner;
  @Mock private NodeSelectorProvisioner nodeSelectorProvisioner;
  @Mock private TolerationsProvisioner tolerationsProvisioner;
  @Mock private KubernetesNamespaceFactory k8sNamespaceFactory;
  @Mock private KubernetesNamespace k8sNamespace;
  @Mock private KubernetesDeployments osDeployments;
  @Mock private KubernetesPersistentVolumeClaims kubernetesPVCs;
  @Mock private PersistentVolumeClaim pvc;
  @Mock private ObjectMeta pvcMetadata;
  @Mock private PersistentVolumeClaimStatus pvcStatus;
  @Mock private Pod pod;
  @Mock private PodStatus podStatus;
  @Mock private RuntimeEventsPublisher eventsPublisher;
  @Mock private RuntimeIdentity identity;

  @Captor private ArgumentCaptor<Pod> podCaptor;

  private PVCSubPathHelper pvcSubPathHelper;

  @BeforeMethod
  public void setup() throws Exception {
    pvcSubPathHelper =
        new PVCSubPathHelper(
            jobMemoryLimit,
            jobImage,
            "IfNotPresent",
            k8sNamespaceFactory,
            securityContextProvisioner,
            nodeSelectorProvisioner,
            tolerationsProvisioner,
            new NoopExecutorServiceWrapper(),
            eventsPublisher);
    lenient().when(identity.getInfrastructureNamespace()).thenReturn(NAMESPACE);
    lenient().when(k8sNamespaceFactory.access(WORKSPACE_ID, NAMESPACE)).thenReturn(k8sNamespace);
    lenient().when(k8sNamespace.deployments()).thenReturn(osDeployments);
    lenient().when(k8sNamespace.persistentVolumeClaims()).thenReturn(kubernetesPVCs);
    lenient().when(kubernetesPVCs.get()).thenReturn(Arrays.asList(pvc));
    lenient().when(pvc.getMetadata()).thenReturn(pvcMetadata);
    lenient().when(pvcMetadata.getName()).thenReturn(PVC_NAME);
    lenient().when(pvc.getStatus()).thenReturn(pvcStatus);
    lenient().when(pvcStatus.getPhase()).thenReturn(PVC_PHASE_BOUND);
    lenient().when(pod.getStatus()).thenReturn(podStatus);
    lenient().when(osDeployments.deploy(nullable(Pod.class))).thenReturn(pod);
    lenient()
        .when(osDeployments.waitAsync(anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(pod));
    lenient().doNothing().when(osDeployments).delete(anyString());
  }

  @Test
  public void testBuildsCommandByGivenBaseAndPaths() throws Exception {
    final String[] paths = {WORKSPACE_ID + PROJECTS_PATH, WORKSPACE_ID + M2_PATH};

    final String[] actual = pvcSubPathHelper.buildCommand(MKDIR_COMMAND_BASE, paths);

    final String[] expected = new String[MKDIR_COMMAND_BASE.length + 2];
    System.arraycopy(MKDIR_COMMAND_BASE, 0, expected, 0, MKDIR_COMMAND_BASE.length);
    expected[expected.length - 1] = JOB_MOUNT_PATH + '/' + WORKSPACE_ID + M2_PATH;
    expected[expected.length - 2] = JOB_MOUNT_PATH + '/' + WORKSPACE_ID + PROJECTS_PATH;
    assertEquals(actual, expected);
  }

  @Test
  public void testSuccessfullyCreatesWorkspaceDirs() throws Exception {
    when(podStatus.getPhase()).thenReturn(POD_PHASE_SUCCEEDED);

    pvcSubPathHelper.createDirs(
        identity, WORKSPACE_ID, PVC_NAME, emptyMap(), WORKSPACE_ID + PROJECTS_PATH);

    verify(osDeployments).create(podCaptor.capture());
    final List<String> actual = podCaptor.getValue().getSpec().getContainers().get(0).getCommand();

    for (Container container : podCaptor.getValue().getSpec().getContainers()) {
      assertEquals(container.getImagePullPolicy(), "IfNotPresent");
    }
    final List<String> expected =
        Stream.concat(
                Arrays.stream(MKDIR_COMMAND_BASE),
                Stream.of(JOB_MOUNT_PATH + '/' + WORKSPACE_ID + PROJECTS_PATH))
            .collect(toList());
    assertEquals(actual, expected);
    verify(osDeployments).waitAsync(anyString(), any());
    verify(podStatus).getPhase();
    verify(osDeployments).delete(anyString());
    verify(securityContextProvisioner).provision(any());
    verify(nodeSelectorProvisioner).provision(any());
    verify(tolerationsProvisioner).provision(any());
  }

  @Test
  public void testWatchLogsWhenCreatingWorkspaceDirs() throws InfrastructureException {
    when(podStatus.getPhase()).thenReturn(POD_PHASE_SUCCEEDED);

    pvcSubPathHelper.createDirs(
        identity,
        WORKSPACE_ID,
        PVC_NAME,
        ImmutableMap.of(
            DEBUG_WORKSPACE_START, TRUE.toString(), DEBUG_WORKSPACE_START_LOG_LIMIT_BYTES, "123"),
        WORKSPACE_ID + PROJECTS_PATH);

    verify(osDeployments).watchLogs(any(), any(), any(), any(), eq(123L));
  }

  @Test
  public void testDoNotWatchFailureEventsWhenCreatingWorkspaceDirs()
      throws InfrastructureException {
    when(podStatus.getPhase()).thenReturn(POD_PHASE_SUCCEEDED);

    pvcSubPathHelper.createDirs(
        identity,
        WORKSPACE_ID,
        PVC_NAME,
        ImmutableMap.of(
            DEBUG_WORKSPACE_START, TRUE.toString(), DEBUG_WORKSPACE_START_LOG_LIMIT_BYTES, "123"),
        WORKSPACE_ID + PROJECTS_PATH);

    verify(osDeployments, never()).watchEvents(any(PodEventHandler.class));
  }

  @Test
  public void testSetMemoryLimitAndRequest() throws Exception {
    when(podStatus.getPhase()).thenReturn(POD_PHASE_SUCCEEDED);

    pvcSubPathHelper.createDirs(
        identity, WORKSPACE_ID, PVC_NAME, emptyMap(), WORKSPACE_ID + PROJECTS_PATH);

    verify(osDeployments).create(podCaptor.capture());
    ResourceRequirements actual =
        podCaptor.getValue().getSpec().getContainers().get(0).getResources();
    ResourceRequirements expected =
        new ResourceRequirementsBuilder()
            .addToLimits(of("memory", new Quantity(jobMemoryLimit)))
            .addToRequests(of("memory", new Quantity(jobMemoryLimit)))
            .build();
    assertEquals(actual, expected);
    verify(osDeployments).waitAsync(anyString(), any());
    verify(podStatus).getPhase();
    verify(osDeployments).delete(anyString());
    verify(securityContextProvisioner).provision(any());
    verify(nodeSelectorProvisioner).provision(any());
    verify(tolerationsProvisioner).provision(any());
  }

  @Test
  public void testLogErrorWhenJobExecutionFailed() throws Exception {
    when(podStatus.getPhase()).thenReturn(POD_PHASE_FAILED);

    pvcSubPathHelper.execute(
        WORKSPACE_ID, NAMESPACE, PVC_NAME, MKDIR_COMMAND_BASE, WORKSPACE_ID + PROJECTS_PATH);

    verify(osDeployments).create(any());
    verify(osDeployments).waitAsync(anyString(), any());
    verify(podStatus).getPhase();
    verify(osDeployments).getPodLogs(any());
    verify(osDeployments).delete(anyString());
  }

  @Test
  public void testLogErrorWhenKubernetesProjectCreationFailed() throws Exception {
    when(osDeployments.create(any()))
        .thenThrow(new InfrastructureException("Kubernetes namespace creation failed"));

    pvcSubPathHelper.execute(
        WORKSPACE_ID, NAMESPACE, PVC_NAME, MKDIR_COMMAND_BASE, WORKSPACE_ID + PROJECTS_PATH);

    verify(k8sNamespaceFactory).access(WORKSPACE_ID, NAMESPACE);
    verify(osDeployments).create(any());
    verify(osDeployments, never()).waitAsync(anyString(), any());
  }

  @Test
  public void testLogErrorWhenKubernetesPodCreationFailed() throws Exception {
    when(osDeployments.create(any()))
        .thenThrow(new InfrastructureException("Kubernetes pod creation failed"));

    pvcSubPathHelper.execute(
        WORKSPACE_ID, NAMESPACE, PVC_NAME, MKDIR_COMMAND_BASE, WORKSPACE_ID + PROJECTS_PATH);

    verify(k8sNamespaceFactory).access(WORKSPACE_ID, NAMESPACE);
    verify(k8sNamespace).deployments();
    verify(osDeployments).create(any());
    verify(osDeployments, never()).waitAsync(anyString(), any());
  }

  @Test
  public void testIgnoreExceptionWhenPodJobRemovalFailed() throws Exception {
    when(podStatus.getPhase()).thenReturn(POD_PHASE_SUCCEEDED);
    doThrow(InfrastructureException.class).when(osDeployments).delete(anyString());

    pvcSubPathHelper.execute(
        WORKSPACE_ID, NAMESPACE, PVC_NAME, MKDIR_COMMAND_BASE, WORKSPACE_ID + PROJECTS_PATH);

    verify(osDeployments).create(any());
    verify(osDeployments).waitAsync(anyString(), any());
    verify(podStatus).getPhase();
    verify(osDeployments).delete(anyString());
  }

  @Test
  public void shouldBeAbleToConfigureImagePullPolicy() throws InfrastructureException {
    // given
    pvcSubPathHelper =
        new PVCSubPathHelper(
            jobMemoryLimit,
            jobImage,
            "ToBeOrNotIfPresent",
            k8sNamespaceFactory,
            securityContextProvisioner,
            nodeSelectorProvisioner,
            tolerationsProvisioner,
            new NoopExecutorServiceWrapper(),
            eventsPublisher);
    // when
    pvcSubPathHelper.execute(
        WORKSPACE_ID, NAMESPACE, PVC_NAME, MKDIR_COMMAND_BASE, WORKSPACE_ID + PROJECTS_PATH);

    // then
    verify(osDeployments).create(podCaptor.capture());
    for (Container container : podCaptor.getValue().getSpec().getContainers()) {
      assertEquals(container.getImagePullPolicy(), "ToBeOrNotIfPresent");
    }
  }

  @Test
  public void testCancelAsyncWaitWhenFailureEventReceived()
      throws InfrastructureException, ExecutionException, InterruptedException, TimeoutException {
    // given
    CompletableFuture<Pod> futureToCancel = (CompletableFuture<Pod>) mock(CompletableFuture.class);
    when(osDeployments.waitAsync(anyString(), any())).thenReturn(futureToCancel);
    when(futureToCancel.get(anyLong(), any(TimeUnit.class))).thenReturn(pod);

    List<PodEventHandler> containerEventsHandlers = new ArrayList<>();
    Watcher<Event> watcher =
        new Watcher<>() {
          @Override
          public boolean reconnecting() {
            return Watcher.super.reconnecting();
          }

          @Override
          public void eventReceived(Action action, Event event) {
            containerEventsHandlers.forEach(
                h ->
                    h.handle(
                        new PodEvent(
                            RM_COMMAND_BASE[0] + "-" + WORKSPACE_ID,
                            "containerName",
                            event.getReason(),
                            "message",
                            "creationTimestamp",
                            "lastTimestamp")));
          }

          @Override
          public void onClose() {
            Watcher.super.onClose();
          }

          @Override
          public void onClose(WatcherException e) {}
        };

    doAnswer(invocation -> containerEventsHandlers.add(invocation.getArgument(0)))
        .when(osDeployments)
        .watchEvents(any(PodEventHandler.class));

    // when
    pvcSubPathHelper
        .removeDirsAsync(WORKSPACE_ID, NAMESPACE, PVC_NAME, WORKSPACE_ID + PROJECTS_PATH)
        .get();
    // simulate failure events
    watcher.eventReceived(Watcher.Action.ADDED, newEvent("Failed"));
    watcher.eventReceived(Watcher.Action.ADDED, newEvent("FailedScheduling"));
    watcher.eventReceived(Watcher.Action.ADDED, newEvent("FailedMount"));

    // then
    verify(futureToCancel, times(3)).cancel(anyBoolean());
  }

  @Test
  public void testWatchFailureEvents() throws InfrastructureException {
    pvcSubPathHelper.execute(
        WORKSPACE_ID, NAMESPACE, PVC_NAME, MKDIR_COMMAND_BASE, true, WORKSPACE_ID + PROJECTS_PATH);

    verify(osDeployments).watchEvents(any(PodEventHandler.class));
  }

  @Test
  public void testDoNotWatchFailureEvents() throws InfrastructureException {
    pvcSubPathHelper.execute(
        WORKSPACE_ID, NAMESPACE, PVC_NAME, MKDIR_COMMAND_BASE, WORKSPACE_ID + PROJECTS_PATH);

    verify(osDeployments, never()).watchEvents(any(PodEventHandler.class));
  }

  @Test
  public void testDoNotCreatePodWhenPVCDoesNotExist() throws InfrastructureException {
    when(kubernetesPVCs.get()).thenReturn(Collections.emptyList());
    pvcSubPathHelper.execute(
        WORKSPACE_ID, NAMESPACE, PVC_NAME, MKDIR_COMMAND_BASE, WORKSPACE_ID + PROJECTS_PATH);
    verify(osDeployments, never()).create(any());
  }

  @Test
  public void testDoNotCreatePodWhenPVCIsTerminating() throws InfrastructureException {
    when(pvcStatus.getPhase()).thenReturn(PVC_PHASE_TERMINATING);
    pvcSubPathHelper.execute(
        WORKSPACE_ID, NAMESPACE, PVC_NAME, MKDIR_COMMAND_BASE, WORKSPACE_ID + PROJECTS_PATH);
    verify(osDeployments, never()).create(any());
  }

  private static Event newEvent(String reason) {
    Event event = new Event();
    event.setReason(reason);
    return event;
  }
}
