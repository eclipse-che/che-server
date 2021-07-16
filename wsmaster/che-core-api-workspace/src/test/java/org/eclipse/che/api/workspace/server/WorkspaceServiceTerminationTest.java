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
package org.eclipse.che.api.workspace.server;

import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.system.shared.dto.SystemServiceItemStoppedEventDto;
import org.eclipse.che.api.workspace.server.spi.RuntimeInfrastructure;
import org.eclipse.che.api.workspace.shared.dto.event.WorkspaceStatusEvent;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests {@link WorkspaceServiceTermination}.
 *
 * @author Yevhenii Voevodin
 */
@Listeners(MockitoTestNGListener.class)
public class WorkspaceServiceTerminationTest {

  @Mock private EventService eventService;

  @Mock private WorkspaceManager workspaceManager;

  @Mock private WorkspaceSharedPool sharedPool;

  @Mock private WorkspaceRuntimes workspaceRuntimes;

  @Mock private RuntimeInfrastructure runtimeInfrastructure;

  @InjectMocks private WorkspaceServiceTermination termination;

  @BeforeMethod
  public void setUp() {
    when(workspaceRuntimes.refuseStart()).thenReturn(true);
  }

  @Test(dataProvider = "workspaceStoppedOnTerminationStatuses", timeOut = 1000L)
  public void shutsDownWorkspaceServiceIfFullShutdownRequested(WorkspaceStatus status)
      throws Exception {
    String workspaceId = "workspace123";

    AtomicBoolean isAnyRunning = new AtomicBoolean(true);
    when(workspaceRuntimes.isAnyActive()).thenAnswer(inv -> isAnyRunning.get());

    // one workspace is running
    when(workspaceRuntimes.getActive()).thenReturn(Collections.singleton(workspaceId));
    when(workspaceRuntimes.getStatus(workspaceId)).thenReturn(status);

    // once stopped change the flag
    doAnswer(
            inv -> {
              isAnyRunning.set(false);
              return null;
            })
        .when(workspaceManager)
        .stopWorkspace("workspace123", Collections.emptyMap());

    // do the actual termination
    termination.terminate();
  }

  @Test
  public void suspendsWorkspaceService() throws Exception {
    AtomicBoolean isAnyRunning = new AtomicBoolean(true);

    when(workspaceRuntimes.isAnyInProgress())
        .thenAnswer(
            inv -> {
              boolean prev = isAnyRunning.get();
              isAnyRunning.set(false);
              return prev;
            });
    // do the actual termination
    termination.suspend();
    // verify checked progress workspaces
    verify(workspaceRuntimes, atLeastOnce()).isAnyInProgress();
    verify(workspaceManager, never()).stopWorkspace(anyString(), anyMap());
  }

  @Test
  public void publishesStoppedWorkspaceStoppedEventsAsServiceItemStoppedEvents() throws Exception {
    when(workspaceRuntimes.getActive()).thenReturn(ImmutableSet.of("id1", "id2", "id3"));
    doAnswer(
            inv -> {
              @SuppressWarnings("unchecked")
              EventSubscriber<WorkspaceStatusEvent> subscriber =
                  (EventSubscriber<WorkspaceStatusEvent>) inv.getArguments()[0];

              // id1
              subscriber.onEvent(newWorkspaceStatusEvent(WorkspaceStatus.STARTING, "id1"));
              subscriber.onEvent(newWorkspaceStatusEvent(WorkspaceStatus.RUNNING, "id1"));
              subscriber.onEvent(newWorkspaceStatusEvent(WorkspaceStatus.STOPPING, "id1"));
              subscriber.onEvent(newWorkspaceStatusEvent(WorkspaceStatus.STOPPED, "id1"));

              // id2
              subscriber.onEvent(newWorkspaceStatusEvent(WorkspaceStatus.RUNNING, "id2"));
              subscriber.onEvent(newWorkspaceStatusEvent(WorkspaceStatus.STOPPING, "id2"));
              subscriber.onEvent(newWorkspaceStatusEvent(WorkspaceStatus.STOPPED, "id2"));

              // id3
              subscriber.onEvent(newWorkspaceStatusEvent(WorkspaceStatus.STOPPED, "id3"));

              return null;
            })
        .when(eventService)
        .subscribe(any());

    termination.terminate();

    verify(eventService)
        .publish(
            newDto(SystemServiceItemStoppedEventDto.class)
                .withService("workspace")
                .withItem("id1")
                .withCurrent(1)
                .withTotal(3));
    verify(eventService)
        .publish(
            newDto(SystemServiceItemStoppedEventDto.class)
                .withService("workspace")
                .withItem("id2")
                .withCurrent(2)
                .withTotal(3));
    verify(eventService)
        .publish(
            newDto(SystemServiceItemStoppedEventDto.class)
                .withService("workspace")
                .withItem("id3")
                .withCurrent(3)
                .withTotal(3));
  }

  @DataProvider
  private static Object[][] workspaceStoppedOnTerminationStatuses() {
    return new Object[][] {{WorkspaceStatus.RUNNING}, {WorkspaceStatus.STARTING}};
  }

  private static WorkspaceStatusEvent newWorkspaceStatusEvent(
      WorkspaceStatus status, String workspaceId) {
    return newDto(WorkspaceStatusEvent.class).withStatus(status).withWorkspaceId(workspaceId);
  }
}
