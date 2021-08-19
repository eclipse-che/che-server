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
package org.eclipse.che.api.workspace.activity;

import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.RUNNING;

import com.google.common.annotations.Beta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.Pages;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.annotations.Required;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for accessing API for updating activity timestamp of running workspaces.
 *
 * @author Anton Korneta
 */
@Singleton
@Path("/activity")
@Tag(
    name = "activity",
    description = "Service for accessing API for updating activity timestamp of running workspaces")
public class WorkspaceActivityService extends Service {

  private static final Logger LOG = LoggerFactory.getLogger(WorkspaceActivityService.class);

  private final WorkspaceActivityManager workspaceActivityManager;
  private final WorkspaceManager workspaceManager;

  @Inject
  public WorkspaceActivityService(
      WorkspaceActivityManager workspaceActivityManager, WorkspaceManager wsManager) {
    this.workspaceActivityManager = workspaceActivityManager;
    this.workspaceManager = wsManager;
  }

  @PUT
  @Path("/{wsId}")
  @Operation(
      summary = "Notifies workspace activity to prevent stop by timeout when workspace is used.",
      responses = {@ApiResponse(responseCode = "204", description = "Activity counted")})
  public void active(@Parameter(description = "Workspace id") @PathParam("wsId") String wsId)
      throws ForbiddenException, NotFoundException, ServerException {
    final WorkspaceImpl workspace = workspaceManager.getWorkspace(wsId);
    if (workspace.getStatus() == RUNNING) {
      workspaceActivityManager.update(wsId, System.currentTimeMillis());
      LOG.debug("Updated activity on workspace {}", wsId);
    }
  }

  @Beta
  @GET
  @Operation(
      summary = "Retrieves the IDs of workspaces that have been in given state.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Array of workspace IDs produced.",
            content =
                @Content(array = @ArraySchema(schema = @Schema(implementation = String.class))))
      })
  @Produces(MediaType.APPLICATION_JSON)
  public Response getWorkspacesByActivity(
      @QueryParam("status")
          @Required
          @Parameter(description = "The requested status of the workspaces")
          WorkspaceStatus status,
      @QueryParam("threshold")
          @DefaultValue("-1")
          @Parameter(
              description =
                  "Optionally, limit the results only to workspaces that have been in the provided"
                      + " status since before this time (in epoch millis). If both threshold and minDuration"
                      + " are specified, minDuration is NOT taken into account.")
          long threshold,
      @QueryParam("minDuration")
          @DefaultValue("-1")
          @Parameter(
              description =
                  "Instead of a threshold, one can also use this parameter to specify the minimum"
                      + " duration that the workspaces need to have been in the given state. The duration is"
                      + " specified in milliseconds. If both threshold and minDuration are specified,"
                      + " minDuration is NOT taken into account.")
          long minDuration,
      @QueryParam("maxItems")
          @DefaultValue("" + Pages.DEFAULT_PAGE_SIZE)
          @Parameter(description = "Maximum number of items on a page of results.")
          int maxItems,
      @QueryParam("skipCount")
          @DefaultValue("0")
          @Parameter(description = "How many items to skip.")
          long skipCount)
      throws ServerException, BadRequestException {

    if (status == null) {
      throw new BadRequestException("The status query parameter is query.");
    }

    long limit = threshold;

    if (limit == -1) {
      limit = System.currentTimeMillis();
      if (minDuration != -1) {
        limit -= minDuration;
      }
    }

    Page<String> data =
        workspaceActivityManager.findWorkspacesInStatus(status, limit, maxItems, skipCount);

    return Response.ok(data.getItems()).header("Link", createLinkHeader(data)).build();
  }
}
