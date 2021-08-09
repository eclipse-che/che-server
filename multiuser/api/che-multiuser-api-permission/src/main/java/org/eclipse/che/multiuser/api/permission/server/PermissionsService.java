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
package org.eclipse.che.multiuser.api.permission.server;

import static com.google.common.base.Strings.isNullOrEmpty;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Collections.singletonList;

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
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.annotations.Required;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.multiuser.api.permission.server.model.impl.AbstractPermissions;
import org.eclipse.che.multiuser.api.permission.shared.dto.DomainDto;
import org.eclipse.che.multiuser.api.permission.shared.dto.PermissionsDto;
import org.eclipse.che.multiuser.api.permission.shared.model.Permissions;
import org.eclipse.che.multiuser.api.permission.shared.model.PermissionsDomain;

/**
 * Defines Permissions REST API
 *
 * @author Sergii Leschenko
 */
@Tag(name = "permissions", description = "Permissions REST API")
@Path("/permissions")
public class PermissionsService extends Service {
  private final PermissionsManager permissionsManager;
  private final InstanceParameterValidator instanceValidator;

  @Inject
  public PermissionsService(
      PermissionsManager permissionsManager, InstanceParameterValidator instanceValidator) {
    this.permissionsManager = permissionsManager;
    this.instanceValidator = instanceValidator;
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get all supported domains or only requested if domain parameter specified",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The domains successfully fetched",
            content =
                @Content(array = @ArraySchema(schema = @Schema(implementation = DomainDto.class)))),
        @ApiResponse(responseCode = "404", description = "Requested domain is not supported"),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error occurred during domains fetching")
      })
  public List<DomainDto> getSupportedDomains(
      @Parameter(description = "Id of requested domain") @QueryParam("domain") String domainId)
      throws NotFoundException {
    if (isNullOrEmpty(domainId)) {
      return permissionsManager.getDomains().stream().map(this::asDto).collect(Collectors.toList());
    } else {
      return singletonList(asDto(permissionsManager.getDomain(domainId)));
    }
  }

  @POST
  @Consumes(APPLICATION_JSON)
  @Operation(
      summary = "Store given permissions",
      responses = {
        @ApiResponse(responseCode = "200", description = "The permissions successfully stored"),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(responseCode = "404", description = "Domain of permissions is not supported"),
        @ApiResponse(
            responseCode = "409",
            description = "New permissions removes last 'setPermissions' of given instance"),
        @ApiResponse(
            responseCode = "409",
            description = "Given domain requires non nullable value for instance but it is null"),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error occurred during permissions storing")
      })
  public void storePermissions(
      @Parameter(description = "The permissions to store", required = true)
          PermissionsDto permissionsDto)
      throws ServerException, BadRequestException, ConflictException, NotFoundException {
    checkArgument(permissionsDto != null, "Permissions descriptor required");
    checkArgument(!isNullOrEmpty(permissionsDto.getUserId()), "User required");
    checkArgument(!isNullOrEmpty(permissionsDto.getDomainId()), "Domain required");
    instanceValidator.validate(permissionsDto.getDomainId(), permissionsDto.getInstanceId());
    checkArgument(!permissionsDto.getActions().isEmpty(), "One or more actions required");

    permissionsManager.storePermission(permissionsDto);
  }

  @GET
  @Path("/{domain}")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Get permissions of current user which are related to specified sdomain and instance",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The permissions successfully fetched",
            content = @Content(schema = @Schema(implementation = PermissionsDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(responseCode = "404", description = "Specified domain is unsupported"),
        @ApiResponse(
            responseCode = "404",
            description =
                "Permissions for current user with specified domain and instance was not found"),
        @ApiResponse(
            responseCode = "409",
            description = "Given domain requires non nullable value for instance but it is null"),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error occurred during permissions fetching")
      })
  public PermissionsDto getCurrentUsersPermissions(
      @Parameter(description = "Domain id to retrieve user's permissions") @PathParam("domain")
          String domain,
      @Parameter(description = "Instance id to retrieve user's permissions") @QueryParam("instance")
          String instance)
      throws BadRequestException, NotFoundException, ConflictException, ServerException {
    instanceValidator.validate(domain, instance);
    return toDto(
        permissionsManager.get(
            EnvironmentContext.getCurrent().getSubject().getUserId(), domain, instance));
  }

  @GET
  @Path("/{domain}/all")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get permissions which are related to specified domain and instance",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The permissions successfully fetched",
            content =
                @Content(
                    array = @ArraySchema(schema = @Schema(implementation = PermissionsDto.class)))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(responseCode = "404", description = "Specified domain is unsupported"),
        @ApiResponse(
            responseCode = "409",
            description = "Given domain requires non nullable value for instance but it is null"),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error occurred during permissions fetching")
      })
  public Response getUsersPermissions(
      @Parameter(description = "Domain id to retrieve users' permissions") @PathParam("domain")
          String domain,
      @Parameter(description = "Instance id to retrieve users' permissions") @QueryParam("instance")
          String instance,
      @Parameter(description = "Max items") @QueryParam("maxItems") @DefaultValue("30")
          int maxItems,
      @Parameter(description = "Skip count") @QueryParam("skipCount") @DefaultValue("0")
          int skipCount)
      throws ServerException, NotFoundException, ConflictException, BadRequestException {
    instanceValidator.validate(domain, instance);
    checkArgument(maxItems >= 0, "The number of items to return can't be negative.");
    checkArgument(skipCount >= 0, "The number of items to skip can't be negative.");

    final Page<AbstractPermissions> permissionsPage =
        permissionsManager.getByInstance(domain, instance, maxItems, skipCount);
    return Response.ok()
        .entity(permissionsPage.getItems(this::toDto))
        .header("Link", createLinkHeader(permissionsPage))
        .build();
  }

  @DELETE
  @Path("/{domain}")
  @Operation(
      summary = "Removes user's permissions related to the particular instance of specified domain",
      responses = {
        @ApiResponse(responseCode = "204", description = "The permissions successfully removed"),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(responseCode = "404", description = "Specified domain is unsupported"),
        @ApiResponse(
            responseCode = "409",
            description = "User has last 'setPermissions' of given instance"),
        @ApiResponse(
            responseCode = "409",
            description = "Given domain requires non nullable value for instance but it is null"),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error occurred during permissions removing")
      })
  public void removePermissions(
      @Parameter(description = "Domain id to remove user's permissions") @PathParam("domain")
          String domain,
      @Parameter(description = "Instance id to remove user's permissions") @QueryParam("instance")
          String instance,
      @Parameter(description = "User id", required = true) @QueryParam("user") @Required
          String user)
      throws BadRequestException, NotFoundException, ConflictException, ServerException {
    instanceValidator.validate(domain, instance);
    permissionsManager.remove(user, domain, instance);
  }

  private DomainDto asDto(PermissionsDomain domain) {
    return DtoFactory.newDto(DomainDto.class)
        .withId(domain.getId())
        .withAllowedActions(domain.getAllowedActions());
  }

  private void checkArgument(boolean expression, String message) throws BadRequestException {
    if (!expression) {
      throw new BadRequestException(message);
    }
  }

  private PermissionsDto toDto(Permissions permissions) {
    return DtoFactory.newDto(PermissionsDto.class)
        .withUserId(permissions.getUserId())
        .withDomainId(permissions.getDomainId())
        .withInstanceId(permissions.getInstanceId())
        .withActions(permissions.getActions());
  }
}
