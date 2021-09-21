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
package org.eclipse.che.api.system.server;

import static java.util.Collections.singletonList;
import static org.eclipse.che.api.core.util.LinksHelper.createLink;
import static org.eclipse.che.api.system.server.SystemEventsWebsocketBroadcaster.SYSTEM_STATE_METHOD_NAME;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import javax.inject.Inject;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.core.rest.shared.dto.LinkParameter;
import org.eclipse.che.api.system.shared.dto.SystemStateDto;
import org.eclipse.che.dto.server.DtoFactory;

/**
 * REST API for system state management.
 *
 * @author Yevhenii Voevodin
 */
@Tag(name = "system", description = "API for system state management")
@Path("/system")
public class SystemService extends Service {

  private final SystemManager manager;

  @Inject
  public SystemService(SystemManager manager) {
    this.manager = manager;
  }

  @POST
  @Path("/stop")
  @Operation(
      summary = "Stops system services. Prepares system to shutdown",
      responses = {
        @ApiResponse(responseCode = "204", description = "The system is preparing to stop"),
        @ApiResponse(responseCode = "409", description = "Stop has been already called")
      })
  public void stop(@QueryParam("shutdown") @DefaultValue("false") boolean shutdown)
      throws ConflictException {
    if (shutdown) {
      manager.stopServices();
    } else {
      manager.suspendServices();
    }
  }

  @GET
  @Path("/state")
  @Produces("application/json")
  @Operation(
      summary = "Gets current system state",
      responses = {
        @ApiResponse(responseCode = "200", description = "The response contains system status"),
        @ApiResponse(responseCode = "409", description = "Stop has been already called")
      })
  public SystemStateDto getState() {
    Link wsLink =
        createLink(
            "GET",
            getServiceContext()
                .getBaseUriBuilder()
                .scheme("https".equals(uriInfo.getBaseUri().getScheme()) ? "wss" : "ws")
                .path("websocket")
                .build()
                .toString(),
            "system.state.channel",
            singletonList(
                DtoFactory.newDto(LinkParameter.class)
                    .withName("channel")
                    .withDefaultValue(SYSTEM_STATE_METHOD_NAME)
                    .withRequired(true)));
    return DtoFactory.newDto(SystemStateDto.class)
        .withStatus(manager.getSystemStatus())
        .withLinks(singletonList(wsLink));
  }
}
