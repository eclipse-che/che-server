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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
@Api("/system")
@Path("/system")
public class SystemService extends Service {

  private final SystemManager manager;

  @Inject
  public SystemService(SystemManager manager) {
    this.manager = manager;
  }

  @POST
  @Path("/stop")
  @ApiOperation("Stops system services. Prepares system to shutdown")
  @ApiResponses({
    @ApiResponse(code = 204, message = "The system is preparing to stop"),
    @ApiResponse(code = 409, message = "Stop has been already called")
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
  @ApiOperation("Gets current system state")
  @ApiResponses(@ApiResponse(code = 200, message = "The response contains system status"))
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
