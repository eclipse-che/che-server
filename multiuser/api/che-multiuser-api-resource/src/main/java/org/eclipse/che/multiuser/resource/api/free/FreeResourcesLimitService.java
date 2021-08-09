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
package org.eclipse.che.multiuser.resource.api.free;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import javax.inject.Inject;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.multiuser.resource.api.DtoConverter;
import org.eclipse.che.multiuser.resource.model.FreeResourcesLimit;
import org.eclipse.che.multiuser.resource.shared.dto.FreeResourcesLimitDto;

/**
 * Defines REST API for managing of free resources limits
 *
 * @author Sergii Leschenko
 */
@Tag(name = "resource-free", description = "Free resources limit REST API")
@Path("/resource/free")
public class FreeResourcesLimitService extends Service {
  private final FreeResourcesLimitManager freeResourcesLimitManager;
  private final FreeResourcesLimitValidator freeResourcesLimitValidator;

  @Inject
  public FreeResourcesLimitService(
      FreeResourcesLimitValidator freeResourcesLimitValidator,
      FreeResourcesLimitManager freeResourcesLimitManager) {
    this.freeResourcesLimitManager = freeResourcesLimitManager;
    this.freeResourcesLimitValidator = freeResourcesLimitValidator;
  }

  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Store free resources limit",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "The resources limit successfully stored",
            content = @Content(schema = @Schema(implementation = FreeResourcesLimitDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(responseCode = "409", description = "The specified account doesn't exist"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public Response storeFreeResourcesLimit(
      @Parameter(description = "Free resources limit") FreeResourcesLimitDto resourcesLimit)
      throws BadRequestException, NotFoundException, ConflictException, ServerException {
    freeResourcesLimitValidator.check(resourcesLimit);
    return Response.status(201)
        .entity(DtoConverter.asDto(freeResourcesLimitManager.store(resourcesLimit)))
        .build();
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get free resources limits",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The resources limits successfully fetched",
            content =
                @Content(
                    array =
                        @ArraySchema(
                            schema = @Schema(implementation = FreeResourcesLimitDto.class)))),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public Response getFreeResourcesLimits(
      @Parameter(description = "Max items") @QueryParam("maxItems") @DefaultValue("30")
          int maxItems,
      @Parameter(description = "Skip count") @QueryParam("skipCount") @DefaultValue("0")
          int skipCount)
      throws ServerException {

    final Page<? extends FreeResourcesLimit> limitsPage =
        freeResourcesLimitManager.getAll(maxItems, skipCount);

    return Response.ok()
        .entity(limitsPage.getItems(DtoConverter::asDto))
        .header("Link", createLinkHeader(limitsPage))
        .build();
  }

  @GET
  @Path("/{accountId}")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get free resources limit for account with given id",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The resources limit successfully fetched",
            content = @Content(schema = @Schema(implementation = FreeResourcesLimitDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(
            responseCode = "404",
            description = "Resources limit for given account was not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public FreeResourcesLimitDto getFreeResourcesLimit(
      @Parameter(description = "Account id") @PathParam("accountId") String accountId)
      throws BadRequestException, NotFoundException, ServerException {
    return DtoConverter.asDto(freeResourcesLimitManager.get(accountId));
  }

  @DELETE
  @Path("/{accountId}")
  @Operation(
      summary = "Remove free resources limit for account with given id",
      responses = {
        @ApiResponse(
            responseCode = "204",
            description = "The resources limit successfully removed",
            content = @Content(schema = @Schema(implementation = FreeResourcesLimitDto.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public void removeFreeResourcesLimit(
      @Parameter(description = "Account id") @PathParam("accountId") String accountId)
      throws ServerException {
    freeResourcesLimitManager.remove(accountId);
  }
}
