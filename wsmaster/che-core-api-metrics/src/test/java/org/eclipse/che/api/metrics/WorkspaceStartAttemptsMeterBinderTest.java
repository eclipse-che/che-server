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
package org.eclipse.che.api.metrics;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.workspace.shared.Constants;
import org.eclipse.che.api.workspace.shared.dto.event.WorkspaceStatusEvent;
import org.eclipse.che.dto.server.DtoFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class WorkspaceStartAttemptsMeterBinderTest {

  private EventService eventService;
  private MeterRegistry registry;

  @BeforeMethod
  public void setUp() {
    eventService = new EventService();
    registry = new SimpleMeterRegistry();
  }

  @Test(dataProvider = "allStatusTransitionsWithoutStarting")
  public void shouldNotCollectEvents(WorkspaceStatus from, WorkspaceStatus to) {
    // given
    WorkspaceStartAttemptsMeterBinder meterBinder =
        new WorkspaceStartAttemptsMeterBinder(eventService);
    meterBinder.bindTo(registry);

    // when

    eventService.publish(
        DtoFactory.newDto(WorkspaceStatusEvent.class)
            .withPrevStatus(from)
            .withStatus(to)
            .withWorkspaceId("id1"));

    // then
    Counter successful = registry.find("che.workspace.starting_attempts.total").counter();
    Assert.assertEquals(successful.count(), 0.0);
  }

  @Test
  public void shouldCollectOnlyStart() {
    // given
    WorkspaceStartAttemptsMeterBinder meterBinder =
        new WorkspaceStartAttemptsMeterBinder(eventService);
    meterBinder.bindTo(registry);

    // when
    eventService.publish(
        DtoFactory.newDto(WorkspaceStatusEvent.class)
            .withPrevStatus(WorkspaceStatus.STOPPED)
            .withStatus(WorkspaceStatus.STARTING)
            .withWorkspaceId("id1"));
    // then
    Counter successful =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "false").counter();
    Assert.assertEquals(successful.count(), 1.0);

    Counter debug =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "true").counter();
    Assert.assertEquals(debug.count(), 0.0);
  }

  @Test
  public void shouldCollectOnlyDebugStart() {
    // given
    WorkspaceStartAttemptsMeterBinder meterBinder =
        new WorkspaceStartAttemptsMeterBinder(eventService);
    meterBinder.bindTo(registry);

    // when
    eventService.publish(
        DtoFactory.newDto(WorkspaceStatusEvent.class)
            .withPrevStatus(WorkspaceStatus.STOPPED)
            .withStatus(WorkspaceStatus.STARTING)
            .withWorkspaceId("id1")
            .withOptions(singletonMap(Constants.DEBUG_WORKSPACE_START, Boolean.TRUE.toString())));

    // then
    Counter successful =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "false").counter();
    Assert.assertEquals(successful.count(), 0.0);

    Counter debug =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "true").counter();
    Assert.assertEquals(debug.count(), 1.0);
  }

  @Test
  public void shouldNotCollectDebugStartWhenSetToFalse() {
    // given
    WorkspaceStartAttemptsMeterBinder meterBinder =
        new WorkspaceStartAttemptsMeterBinder(eventService);
    meterBinder.bindTo(registry);

    // when
    eventService.publish(
        DtoFactory.newDto(WorkspaceStatusEvent.class)
            .withPrevStatus(WorkspaceStatus.STOPPED)
            .withStatus(WorkspaceStatus.STARTING)
            .withWorkspaceId("id1")
            .withOptions(singletonMap(Constants.DEBUG_WORKSPACE_START, Boolean.FALSE.toString())));

    // then
    Counter successful =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "false").counter();
    Assert.assertEquals(successful.count(), 1.0);

    Counter debug =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "true").counter();
    Assert.assertEquals(debug.count(), 0.0);
  }

  @Test
  public void shouldNotCollectDebugStartWhenSetToStrangeString() {
    // given
    WorkspaceStartAttemptsMeterBinder meterBinder =
        new WorkspaceStartAttemptsMeterBinder(eventService);
    meterBinder.bindTo(registry);

    // when
    eventService.publish(
        DtoFactory.newDto(WorkspaceStatusEvent.class)
            .withPrevStatus(WorkspaceStatus.STOPPED)
            .withStatus(WorkspaceStatus.STARTING)
            .withWorkspaceId("id1")
            .withOptions(singletonMap(Constants.DEBUG_WORKSPACE_START, "strange")));

    // then
    Counter successful =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "false").counter();
    Assert.assertEquals(successful.count(), 1.0);

    Counter debug =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "true").counter();
    Assert.assertEquals(debug.count(), 0.0);
  }

  @Test
  public void shouldNotCollectDebugStartWhenNotSetInOptions() {
    // given
    WorkspaceStartAttemptsMeterBinder meterBinder =
        new WorkspaceStartAttemptsMeterBinder(eventService);
    meterBinder.bindTo(registry);

    // when
    eventService.publish(
        DtoFactory.newDto(WorkspaceStatusEvent.class)
            .withPrevStatus(WorkspaceStatus.STOPPED)
            .withStatus(WorkspaceStatus.STARTING)
            .withWorkspaceId("id1")
            .withOptions(singletonMap("bla", "bol")));

    // then
    Counter successful =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "false").counter();
    Assert.assertEquals(successful.count(), 1.0);

    Counter debug =
        registry.find("che.workspace.starting_attempts.total").tag("debug", "true").counter();
    Assert.assertEquals(debug.count(), 0.0);
  }

  @DataProvider
  public Object[][] allStatusTransitionsWithoutStarting() {
    List<List<WorkspaceStatus>> transitions = new ArrayList<>(9);

    for (WorkspaceStatus from : WorkspaceStatus.values()) {
      for (WorkspaceStatus to : WorkspaceStatus.values()) {
        if (from == WorkspaceStatus.STOPPED && to == WorkspaceStatus.STARTING) {
          continue;
        }

        transitions.add(asList(from, to));
      }
    }

    return transitions.stream().map(List::toArray).toArray(Object[][]::new);
  }
}
