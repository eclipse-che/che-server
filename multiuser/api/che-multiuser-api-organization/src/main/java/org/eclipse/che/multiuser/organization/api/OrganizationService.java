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
package org.eclipse.che.multiuser.organization.api;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.multiuser.organization.api.DtoConverter.asDto;

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
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.multiuser.organization.shared.dto.OrganizationDto;
import org.eclipse.che.multiuser.organization.shared.model.Organization;

/**
 * Defines Organization REST API.
 *
 * @author Sergii Leschenko
 */
@Tag(name = "organization", description = "Organization REST API")
@Path("/organization")
public class OrganizationService extends Service {
  private final OrganizationManager organizationManager;
  private final OrganizationLinksInjector linksInjector;
  private final OrganizationValidator organizationValidator;

  @Inject
  public OrganizationService(
      OrganizationManager organizationManager,
      OrganizationLinksInjector linksInjector,
      OrganizationValidator organizationValidator) {
    this.organizationManager = organizationManager;
    this.linksInjector = linksInjector;
    this.organizationValidator = organizationValidator;
  }

  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Create new organization",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "The organization successfully created",
            content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(
            responseCode = "409",
            description =
                "Conflict error occurred during the organization creation"
                    + "(e.g. The organization with such name already exists)"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public Response create(
      @Parameter(description = "Organization to create", required = true)
          OrganizationDto organization)
      throws BadRequestException, NotFoundException, ConflictException, ServerException {
    organizationValidator.checkOrganization(organization);
    return Response.status(201)
        .entity(
            linksInjector.injectLinks(
                asDto(organizationManager.create(organization)), getServiceContext()))
        .build();
  }

  @POST
  @Path("/{id}")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Update organization",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The organization successfully updated",
            content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(
            responseCode = "404",
            description = "The organization with given id was not found"),
        @ApiResponse(
            responseCode = "409",
            description =
                "Conflict error occurred during the organization creation"
                    + "(e.g. The organization with such name already exists)"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public OrganizationDto update(
      @Parameter(description = "Organization id") @PathParam("id") String organizationId,
      @Parameter(description = "Organization to update", required = true)
          OrganizationDto organization)
      throws BadRequestException, ConflictException, NotFoundException, ServerException {
    organizationValidator.checkOrganization(organization);
    return linksInjector.injectLinks(
        asDto(organizationManager.update(organizationId, organization)), getServiceContext());
  }

  @DELETE
  @Path("/{id}")
  @Operation(
      summary = "Remove organization with given id",
      responses = {
        @ApiResponse(responseCode = "204", description = "The organization successfully removed"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public void remove(
      @Parameter(description = "Organization id") @PathParam("id") String organization)
      throws ServerException {
    organizationManager.remove(organization);
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Path("/{organizationId}")
  @Operation(
      summary = "Get organization by id",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The organization successfully fetched",
            content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
        @ApiResponse(
            responseCode = "404",
            description = "The organization with given id was not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public OrganizationDto getById(
      @Parameter(description = "Organization id") @PathParam("organizationId")
          String organizationId)
      throws NotFoundException, ServerException {
    return linksInjector.injectLinks(
        asDto(organizationManager.getById(organizationId)), getServiceContext());
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Path("/find")
  @Operation(
      summary = "Find organization by name",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The organization successfully fetched",
            content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(
            responseCode = "404",
            description = "The organization with given name was not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public OrganizationDto find(
      @Parameter(description = "Organization name", required = true) @QueryParam("name")
          String organizationName)
      throws NotFoundException, ServerException, BadRequestException {
    checkArgument(organizationName != null, "Missed organization's name");
    return linksInjector.injectLinks(
        asDto(organizationManager.getByName(organizationName)), getServiceContext());
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Path("/{parent}/organizations")
  @Operation(
      summary = "Get child organizations",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The child organizations successfully fetched",
            content =
                @Content(
                    array =
                        @ArraySchema(schema = @Schema(implementation = OrganizationDto.class)))),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public Response getByParent(
      @Parameter(description = "Parent organization id") @PathParam("parent") String parent,
      @Parameter(description = "Max items") @QueryParam("maxItems") @DefaultValue("30")
          int maxItems,
      @Parameter(description = "Skip count") @QueryParam("skipCount") @DefaultValue("0")
          int skipCount)
      throws ServerException, BadRequestException {

    checkArgument(maxItems >= 0, "The number of items to return can't be negative.");
    checkArgument(skipCount >= 0, "The number of items to skip can't be negative.");
    final Page<? extends Organization> organizationsPage =
        organizationManager.getByParent(parent, maxItems, skipCount);
    return Response.ok()
        .entity(
            organizationsPage.getItems(
                organization ->
                    linksInjector.injectLinks(asDto(organization), getServiceContext())))
        .header("Link", createLinkHeader(organizationsPage))
        .build();
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Get user's organizations. When user parameter is missed then will be fetched current user's organizations",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The organizations successfully fetched",
            content =
                @Content(
                    array =
                        @ArraySchema(schema = @Schema(implementation = OrganizationDto.class)))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public Response getOrganizations(
      @Parameter(description = "User id") @QueryParam("user") String userId,
      @Parameter(description = "Max items") @QueryParam("maxItems") @DefaultValue("30")
          int maxItems,
      @Parameter(description = "Skip count") @QueryParam("skipCount") @DefaultValue("0")
          int skipCount)
      throws ServerException, BadRequestException {

    checkArgument(maxItems >= 0, "The number of items to return can't be negative.");
    checkArgument(skipCount >= 0, "The number of items to skip can't be negative.");
    if (userId == null) {
      userId = EnvironmentContext.getCurrent().getSubject().getUserId();
    }
    final Page<? extends Organization> organizationsPage =
        organizationManager.getByMember(userId, maxItems, skipCount);
    return Response.ok()
        .entity(
            organizationsPage.getItems(
                organization ->
                    linksInjector.injectLinks(asDto(organization), getServiceContext())))
        .header("Link", createLinkHeader(organizationsPage))
        .build();
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails
   * @throws BadRequestException if {@code expression} is false
   */
  private void checkArgument(boolean expression, String errorMessage) throws BadRequestException {
    if (!expression) {
      throw new BadRequestException(errorMessage);
    }
  }
}
