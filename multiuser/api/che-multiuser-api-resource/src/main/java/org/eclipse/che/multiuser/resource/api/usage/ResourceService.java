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
package org.eclipse.che.multiuser.resource.api.usage;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.multiuser.resource.api.DtoConverter.asDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.multiuser.resource.api.DtoConverter;
import org.eclipse.che.multiuser.resource.shared.dto.ResourceDto;
import org.eclipse.che.multiuser.resource.shared.dto.ResourcesDetailsDto;

/**
 * Defines Resource REST API.
 *
 * @author Sergii Leschenko
 */
@Tag(name = "resource", description = "Resource REST API")
@Path("/resource")
public class ResourceService extends Service {

  private final ResourceManager resourceManager;

  @Inject
  public ResourceService(ResourceManager resourceManager) {
    this.resourceManager = resourceManager;
  }

  @GET
  @Path("/{accountId}")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get list of resources which are available for given account",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The total resources are successfully fetched",
            content =
                @Content(
                    array = @ArraySchema(schema = @Schema(implementation = ResourceDto.class)))),
        @ApiResponse(responseCode = "404", description = "Account with specified id was not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public List<ResourceDto> getTotalResources(
      @Parameter(description = "Account id") @PathParam("accountId") String accountId)
      throws NotFoundException, ServerException, ConflictException {
    return resourceManager.getTotalResources(accountId).stream()
        .map(DtoConverter::asDto)
        .collect(Collectors.toList());
  }

  @GET
  @Path("/{accountId}/available")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get list of resources which are available for usage by given account",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The available resources are successfully fetched",
            content =
                @Content(
                    array = @ArraySchema(schema = @Schema(implementation = ResourceDto.class)))),
        @ApiResponse(responseCode = "404", description = "Account with specified id was not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public List<ResourceDto> getAvailableResources(@PathParam("accountId") String accountId)
      throws NotFoundException, ServerException {
    return resourceManager.getAvailableResources(accountId).stream()
        .map(DtoConverter::asDto)
        .collect(Collectors.toList());
  }

  @GET
  @Path("/{accountId}/used")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get list of resources which are used by given account",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The used resources are successfully fetched",
            content =
                @Content(
                    array = @ArraySchema(schema = @Schema(implementation = ResourceDto.class)))),
        @ApiResponse(responseCode = "404", description = "Account with specified id was not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public List<ResourceDto> getUsedResources(@PathParam("accountId") String accountId)
      throws NotFoundException, ServerException {
    return resourceManager.getUsedResources(accountId).stream()
        .map(DtoConverter::asDto)
        .collect(Collectors.toList());
  }

  @GET
  @Path("{accountId}/details")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get detailed information about resources for given account",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The resources details successfully fetched",
            content = @Content(schema = @Schema(implementation = ResourcesDetailsDto.class))),
        @ApiResponse(responseCode = "404", description = "Account with specified id was not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public ResourcesDetailsDto getResourceDetails(
      @Parameter(description = "Account id") @PathParam("accountId") String accountId)
      throws NotFoundException, ServerException {
    return asDto(resourceManager.getResourceDetails(accountId));
  }
}
