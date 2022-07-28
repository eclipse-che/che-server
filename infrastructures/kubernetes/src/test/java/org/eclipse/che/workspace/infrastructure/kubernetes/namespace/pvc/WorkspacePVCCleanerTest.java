/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.workspace.shared.event.WorkspaceRemovedEvent;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests {@link WorkspacePVCCleaner}.
 *
 * @author Anton Korneta
 */
@Listeners(MockitoTestNGListener.class)
public class WorkspacePVCCleanerTest {

  @Mock private WorkspaceVolumesStrategy pvcStrategy;
  private EventService eventService;
  @Mock private Workspace workspace;
  @Mock WorkspaceRemovedEvent event;

  private WorkspacePVCCleaner workspacePVCCleaner;

  @BeforeMethod
  public void setUp() throws Exception {
    workspacePVCCleaner = new WorkspacePVCCleaner(pvcStrategy);
    eventService = spy(new EventService());
  }

  @Test
  public void testDoNotSubscribesCleanerWhenPVCDisabled() throws Exception {
    workspacePVCCleaner = spy(new WorkspacePVCCleaner(pvcStrategy));

    workspacePVCCleaner.subscribe(eventService);

    verify(eventService, never()).subscribe(any(), eq(WorkspaceRemovedEvent.class));
  }
}
