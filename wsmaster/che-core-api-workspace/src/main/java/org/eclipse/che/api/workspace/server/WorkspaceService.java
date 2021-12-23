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

import static com.google.common.base.Strings.isNullOrEmpty;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.eclipse.che.api.workspace.server.DtoConverter.asDto;
import static org.eclipse.che.api.workspace.server.WorkspaceKeyValidator.validateKey;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_DEVWORKSPACES_ENABLED_PROPERTY;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_FACTORY_DEFAULT_EDITOR_PROPERTY;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_FACTORY_DEFAULT_PLUGINS_PROPERTY;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_WORKSPACE_AUTO_START;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_WORKSPACE_DEVFILE_REGISTRY_INTERNAL_URL_PROPERTY;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_WORKSPACE_DEVFILE_REGISTRY_URL_PROPERTY;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_WORKSPACE_PLUGIN_REGISTRY_INTERNAL_URL_PROPERTY;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_WORKSPACE_PLUGIN_REGISTRY_URL_PROPERTY;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_WORKSPACE_STORAGE_AVAILABLE_TYPES;
import static org.eclipse.che.api.workspace.shared.Constants.CHE_WORKSPACE_STORAGE_PREFERRED_TYPE;
import static org.eclipse.che.api.workspace.shared.Constants.DEBUG_WORKSPACE_START;
import static org.eclipse.che.api.workspace.shared.Constants.DEBUG_WORKSPACE_START_LOG_LIMIT_BYTES;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.Pages;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.ValidationException;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.core.model.workspace.config.ServerConfig;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.URLFileContentProvider;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.token.MachineAccessForbidden;
import org.eclipse.che.api.workspace.server.token.MachineTokenException;
import org.eclipse.che.api.workspace.server.token.MachineTokenProvider;
import org.eclipse.che.api.workspace.shared.Constants;
import org.eclipse.che.api.workspace.shared.dto.EnvironmentDto;
import org.eclipse.che.api.workspace.shared.dto.MachineDto;
import org.eclipse.che.api.workspace.shared.dto.RecipeDto;
import org.eclipse.che.api.workspace.shared.dto.RuntimeDto;
import org.eclipse.che.api.workspace.shared.dto.ServerDto;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceConfigDto;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.DevfileDto;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Defines Workspace REST API.
 *
 * @author Yevhenii Voevodin
 * @author Igor Vinokur
 */
@Tag(name = "workspace", description = "Workspace REST API")
@Path("/workspace")
public class WorkspaceService extends Service {

  private final WorkspaceManager workspaceManager;
  private final MachineTokenProvider machineTokenProvider;
  private final WorkspaceLinksGenerator linksGenerator;
  private final String pluginRegistryUrl;
  private final String pluginRegistryInternalUrl;
  private final String devfileRegistryUrl;
  private final String devfileRegistryInternalUrl;
  private final String apiEndpoint;
  private final boolean cheWorkspaceAutoStart;
  private final boolean cheDevWorkspacesEnabled;
  private final FileContentProvider devfileContentProvider;
  private final Long logLimitBytes;
  private final String availableStorageTypes;
  private final String preferredStorageType;
  private final String defaultEditor;
  private final String defaultPlugins;

  @Inject
  public WorkspaceService(
      @Named("che.api") String apiEndpoint,
      @Named(CHE_WORKSPACE_AUTO_START) boolean cheWorkspaceAutoStart,
      WorkspaceManager workspaceManager,
      MachineTokenProvider machineTokenProvider,
      WorkspaceLinksGenerator linksGenerator,
      @Named(CHE_WORKSPACE_PLUGIN_REGISTRY_URL_PROPERTY) @Nullable String pluginRegistryUrl,
      @Named(CHE_WORKSPACE_PLUGIN_REGISTRY_INTERNAL_URL_PROPERTY) @Nullable
          String pluginRegistryInternalUrl,
      @Named(CHE_WORKSPACE_DEVFILE_REGISTRY_URL_PROPERTY) @Nullable String devfileRegistryUrl,
      @Named(CHE_WORKSPACE_DEVFILE_REGISTRY_INTERNAL_URL_PROPERTY) @Nullable
          String devfileRegistryInternalUrl,
      URLFetcher urlFetcher,
      @Named(DEBUG_WORKSPACE_START_LOG_LIMIT_BYTES) Long logLimitBytes,
      @Named(CHE_WORKSPACE_STORAGE_AVAILABLE_TYPES) String availableStorageTypes,
      @Named(CHE_WORKSPACE_STORAGE_PREFERRED_TYPE) String preferredStorageType,
      @Named(CHE_FACTORY_DEFAULT_EDITOR_PROPERTY) String defaultEditor,
      @Named(CHE_FACTORY_DEFAULT_PLUGINS_PROPERTY) @Nullable String defaultPlugins,
      @Named(CHE_DEVWORKSPACES_ENABLED_PROPERTY) boolean cheDevWorkspacesEnabled) {
    this.apiEndpoint = apiEndpoint;
    this.cheWorkspaceAutoStart = cheWorkspaceAutoStart;
    this.workspaceManager = workspaceManager;
    this.machineTokenProvider = machineTokenProvider;
    this.linksGenerator = linksGenerator;
    this.pluginRegistryInternalUrl = pluginRegistryInternalUrl;
    this.pluginRegistryUrl = pluginRegistryUrl;
    this.devfileRegistryUrl = devfileRegistryUrl;
    this.devfileRegistryInternalUrl = devfileRegistryInternalUrl;
    this.devfileContentProvider = new URLFileContentProvider(null, urlFetcher);
    this.logLimitBytes = logLimitBytes;
    this.availableStorageTypes = availableStorageTypes;
    this.preferredStorageType = preferredStorageType;
    this.defaultEditor = defaultEditor;
    this.defaultPlugins = defaultPlugins;
    this.cheDevWorkspacesEnabled = cheDevWorkspacesEnabled;
  }

  @Path("/devfile")
  @POST
  @Consumes({APPLICATION_JSON, "text/yaml", "text/x-yaml"})
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Creates a new workspace based on the Devfile.",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "The workspace successfully created",
            content = @Content(schema = @Schema(implementation = WorkspaceDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(
            responseCode = "403",
            description = "The user does not have access to create a new workspace"),
        @ApiResponse(
            responseCode = "409",
            description =
                "Conflict error occurred during the workspace creation"
                    + "(e.g. The workspace with such name already exists)"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public Response create(
      @Parameter(description = "The devfile of the workspace to create", required = true)
          DevfileDto devfile,
      @Parameter(
              description =
                  "Workspace attribute defined in 'attrName:attrValue' format. "
                      + "The first ':' is considered as attribute name and value separator",
              examples = @ExampleObject(value = "attrName:value-with:colon"))
          @QueryParam("attribute")
          List<String> attrsList,
      @Parameter(
              description =
                  "If true then the workspace will be immediately "
                      + "started after it is successfully created")
          @QueryParam("start-after-create")
          @DefaultValue("false")
          Boolean startAfterCreate,
      @Parameter(description = "Che namespace where workspace should be created")
          @QueryParam("namespace")
          String namespace,
      @HeaderParam(CONTENT_TYPE) MediaType contentType)
      throws ConflictException, BadRequestException, ForbiddenException, NotFoundException,
          ServerException {
    requiredNotNull(devfile, "Devfile");
    final Map<String, String> attributes = parseAttrs(attrsList);
    if (namespace == null) {
      namespace = EnvironmentContext.getCurrent().getSubject().getUserName();
    }
    WorkspaceImpl workspace;
    try {
      workspace =
          workspaceManager.createWorkspace(
              devfile,
              namespace,
              attributes,
              // create a new cache for each request so that we don't have to care about lifetime
              // of the cache, etc. The content is cached only for the duration of this call
              // (i.e. all the validation and provisioning of the devfile will download each
              // referenced file only once per request)
              FileContentProvider.cached(devfileContentProvider));
    } catch (ValidationException x) {
      throw new BadRequestException(x.getMessage());
    }

    if (startAfterCreate) {
      workspaceManager.startWorkspace(workspace.getId(), null, new HashMap<>());
    }
    return Response.status(201).entity(asDtoWithLinksAndToken(workspace)).build();
  }

  @GET
  @Path("/{key:.*}")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Get the workspace by the composite key. Composite key can be just workspace ID or in the "
              + "namespace:workspace_name form, where namespace is optional (e.g :workspace_name is valid key too."
              + "namespace/workspace_name form, where namespace can contain '/' character.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The response contains requested workspace entity"),
        @ApiResponse(
            responseCode = "404",
            description = "The workspace with specified id does not exist"),
        @ApiResponse(responseCode = "403", description = "The user is not workspace owner"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public WorkspaceDto getByKey(
      @Parameter(
              description = "Composite key",
              examples = {
                @ExampleObject(name = "workspace12345678"),
                @ExampleObject(name = "namespace/workspace_name"),
                @ExampleObject(name = "namespace_part_1/namespace_part_2/workspace_name")
              })
          @PathParam("key")
          String key,
      @Parameter(description = "Whether to include internal servers into runtime or not")
          @DefaultValue("false")
          @QueryParam("includeInternalServers")
          String includeInternalServers)
      throws NotFoundException, ServerException, ForbiddenException, BadRequestException {
    validateKey(key);
    boolean bIncludeInternalServers =
        isNullOrEmpty(includeInternalServers) || Boolean.parseBoolean(includeInternalServers);
    return filterServers(
        asDtoWithLinksAndToken(workspaceManager.getWorkspace(key)), bIncludeInternalServers);
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Get workspaces which user can read. This operation can be performed only by authorized user",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The workspaces successfully fetched",
            content =
                @Content(
                    array = @ArraySchema(schema = @Schema(implementation = WorkspaceDto.class)))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error occurred during workspaces fetching")
      })
  public Response getWorkspaces(
      @Parameter(description = "The number of the items to skip")
          @DefaultValue("0")
          @QueryParam("skipCount")
          Integer skipCount,
      @Parameter(description = "The limit of the items in the response, default is 30")
          @DefaultValue("30")
          @QueryParam("maxItems")
          Integer maxItems,
      @Parameter(description = "Workspace status") @QueryParam("status") String status)
      throws ServerException, BadRequestException {
    Page<WorkspaceImpl> workspacesPage =
        workspaceManager.getWorkspaces(
            EnvironmentContext.getCurrent().getSubject().getUserId(), false, maxItems, skipCount);
    return Response.ok()
        .entity(
            workspacesPage.getItems().stream()
                .filter(ws -> status == null || status.equalsIgnoreCase(ws.getStatus().toString()))
                .map(DtoConverter::asDto)
                .collect(toList()))
        .header("Link", createLinkHeader(workspacesPage))
        .build();
  }

  @GET
  @Path("/namespace/{namespace:.*}")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Get workspaces by given namespace. This operation can be performed only by authorized user",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The workspaces successfully fetched",
            content =
                @Content(
                    array = @ArraySchema(schema = @Schema(implementation = WorkspaceDto.class)))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error occurred during workspaces fetching")
      })
  public List<WorkspaceDto> getByNamespace(
      @Parameter(description = "Workspace status") @QueryParam("status") String status,
      @Parameter(description = "The namespace") @PathParam("namespace") String namespace)
      throws ServerException, BadRequestException {
    return asDtosWithLinks(
        Pages.stream(
                (maxItems, skipCount) ->
                    workspaceManager.getByNamespace(namespace, false, maxItems, skipCount))
            .filter(ws -> status == null || status.equalsIgnoreCase(ws.getStatus().toString()))
            .collect(toList()));
  }

  @PUT
  @Path("/{id}")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Update the workspace by replacing all the existing data with update. This operation can be performed only by the workspace owner",
      responses = {
        @ApiResponse(responseCode = "200", description = "The workspace successfully updated"),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(
            responseCode = "403",
            description = "The user does not have access to update the workspace"),
        @ApiResponse(
            responseCode = "409",
            description =
                "Conflict error occurred during workspace update"
                    + "(e.g. Workspace with such name already exists)"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public WorkspaceDto update(
      @Parameter(description = "The workspace id") @PathParam("id") String id,
      @Parameter(description = "The workspace update", required = true) WorkspaceDto update)
      throws BadRequestException, ServerException, ForbiddenException, NotFoundException,
          ConflictException {
    checkArgument(
        update.getConfig() != null ^ update.getDevfile() != null,
        "Required non-null workspace configuration or devfile update but not both");
    relativizeRecipeLinks(update.getConfig());
    return asDtoWithLinksAndToken(doUpdate(id, update));
  }

  @DELETE
  @Path("/{id}")
  @Operation(
      summary =
          "Removes the workspace. This operation can be performed only by the workspace owner",
      responses = {
        @ApiResponse(responseCode = "204", description = "The workspace successfully removed"),
        @ApiResponse(
            responseCode = "403",
            description = "The user does not have access to remove the workspace"),
        @ApiResponse(responseCode = "404", description = "The workspace doesn't exist"),
        @ApiResponse(
            responseCode = "409",
            description = "The workspace is not stopped(has runtime)"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public void delete(@Parameter(description = "The workspace id") @PathParam("id") String id)
      throws BadRequestException, ServerException, NotFoundException, ConflictException,
          ForbiddenException {
    workspaceManager.removeWorkspace(id);
  }

  @POST
  @Path("/{id}/runtime")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Start the workspace by the id. This operation can be performed only by the workspace owner."
              + "The workspace starts asynchronously",
      responses = {
        @ApiResponse(responseCode = "200", description = "The workspace is starting"),
        @ApiResponse(
            responseCode = "404",
            description = "The workspace with specified id doesn't exist"),
        @ApiResponse(
            responseCode = "403",
            description =
                "The user is not workspace owner." + "The operation is not allowed for the user"),
        @ApiResponse(
            responseCode = "409",
            description = "Any conflict occurs during the workspace start"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public WorkspaceDto startById(
      @Parameter(description = "The workspace id") @PathParam("id") String workspaceId,
      @Parameter(
              description = "The name of the workspace environment that should be used for start")
          @QueryParam("environment")
          String envName,
      @QueryParam(DEBUG_WORKSPACE_START) @DefaultValue("false") Boolean debugWorkspaceStart)
      throws ServerException, BadRequestException, NotFoundException, ForbiddenException,
          ConflictException {

    Map<String, String> options = new HashMap<>();
    if (debugWorkspaceStart) {
      options.put(DEBUG_WORKSPACE_START, debugWorkspaceStart.toString());
      options.put(DEBUG_WORKSPACE_START_LOG_LIMIT_BYTES, logLimitBytes.toString());
    }

    return asDtoWithLinksAndToken(workspaceManager.startWorkspace(workspaceId, envName, options));
  }

  @DELETE
  @Path("/{id}/runtime")
  @Operation(
      summary =
          "Stop the workspace. This operation can be performed only by the workspace owner."
              + "The workspace stops asynchronously",
      responses = {
        @ApiResponse(responseCode = "204", description = "The workspace is stopping"),
        @ApiResponse(
            responseCode = "404",
            description = "The workspace with specified id doesn't exist"),
        @ApiResponse(responseCode = "403", description = "The user is not workspace owner"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public void stop(@Parameter(description = "The workspace id") @PathParam("id") String id)
      throws ForbiddenException, NotFoundException, ServerException, ConflictException {
    workspaceManager.stopWorkspace(id, emptyMap());
  }

  @GET
  @Path("/settings")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get workspace server configuration values",
      responses = {
        @ApiResponse(responseCode = "200", description = "The response contains server settings")
      })
  public Map<String, String> getSettings() {
    Builder<String, String> settings = ImmutableMap.builder();

    settings.put(
        Constants.SUPPORTED_RECIPE_TYPES,
        Joiner.on(",").join(workspaceManager.getSupportedRecipes()));
    settings.put(CHE_WORKSPACE_AUTO_START, Boolean.toString(cheWorkspaceAutoStart));

    if (pluginRegistryUrl != null) {
      settings.put("cheWorkspacePluginRegistryUrl", pluginRegistryUrl);
    }

    if (pluginRegistryInternalUrl != null) {
      settings.put("cheWorkspacePluginRegistryInternalUrl", pluginRegistryInternalUrl);
    }

    if (devfileRegistryUrl != null) {
      settings.put("cheWorkspaceDevfileRegistryUrl", devfileRegistryUrl);
    }

    if (devfileRegistryInternalUrl != null) {
      settings.put("cheWorkspaceDevfileRegistryInternalUrl", devfileRegistryInternalUrl);
    }

    if (defaultPlugins != null) {
      settings.put(CHE_FACTORY_DEFAULT_PLUGINS_PROPERTY, defaultPlugins);
    }
    settings.put(CHE_FACTORY_DEFAULT_EDITOR_PROPERTY, defaultEditor);
    settings.put(CHE_WORKSPACE_STORAGE_AVAILABLE_TYPES, availableStorageTypes);
    settings.put(CHE_WORKSPACE_STORAGE_PREFERRED_TYPE, preferredStorageType);
    settings.put(CHE_DEVWORKSPACES_ENABLED_PROPERTY, Boolean.toString(cheDevWorkspacesEnabled));
    return settings.build();
  }

  private static Map<String, String> parseAttrs(List<String> attributes)
      throws BadRequestException, ForbiddenException {
    if (attributes == null) {
      // we need to make room for the potential infrastructure namespace that can be put into
      // this map by the callers...
      return Maps.newHashMapWithExpectedSize(1);
    }
    final Map<String, String> res = Maps.newHashMapWithExpectedSize(attributes.size());
    for (String attribute : attributes) {
      final int colonIdx = attribute.indexOf(':');
      if (colonIdx == -1) {
        throw new BadRequestException(
            "Attribute '"
                + attribute
                + "' is not valid, "
                + "it should contain name and value separated with colon. "
                + "For example: attributeName:attributeValue");
      }
      String name = attribute.substring(0, colonIdx);
      String value = attribute.substring(colonIdx + 1);

      if (name.isEmpty())
        throw new BadRequestException(
            "Attribute '"
                + attribute
                + "' is not valid, "
                + "Empty attribute name is not allowed. ");
      if (name.startsWith("codenvy"))
        throw new ForbiddenException(
            "Attribute '" + attribute + "' is not allowed. 'codenvy' prefix is reserved. ");

      res.put(name, value);
    }
    return res;
  }

  /**
   * Checks object reference is not {@code null}
   *
   * @param object object reference to check
   * @param subject used as subject of exception message "{subject} required"
   * @throws BadRequestException when object reference is {@code null}
   */
  private void requiredNotNull(Object object, String subject) throws BadRequestException {
    if (object == null) {
      throw new BadRequestException(subject + " required");
    }
  }

  /**
   * Checks the specified expression.
   *
   * @param expression the expression to check
   * @param errorMessage error message that should be used if expression is false
   * @throws BadRequestException when the expression is false
   */
  private void checkArgument(boolean expression, String errorMessage) throws BadRequestException {
    if (!expression) {
      throw new BadRequestException(String.valueOf(errorMessage));
    }
  }

  private void relativizeRecipeLinks(WorkspaceConfigDto config) {
    if (config != null) {
      Map<String, EnvironmentDto> environments = config.getEnvironments();
      if (environments != null && !environments.isEmpty()) {
        for (EnvironmentDto environment : environments.values()) {
          relativizeRecipeLinks(environment);
        }
      }
    }
  }

  private void relativizeRecipeLinks(EnvironmentDto environment) {
    if (environment != null) {
      RecipeDto recipe = environment.getRecipe();
      if (recipe != null) {
        if ("dockerfile".equals(recipe.getType())) {
          String location = recipe.getLocation();
          if (location != null && location.startsWith(apiEndpoint)) {
            recipe.setLocation(location.substring(apiEndpoint.length()));
          }
        }
      }
    }
  }

  private WorkspaceImpl doUpdate(String id, Workspace update)
      throws BadRequestException, ConflictException, NotFoundException, ServerException {
    try {
      return workspaceManager.updateWorkspace(id, update);
    } catch (ValidationException x) {
      throw new BadRequestException(x.getMessage());
    }
  }

  private List<WorkspaceDto> asDtosWithLinks(List<WorkspaceImpl> workspaces)
      throws ServerException {
    List<WorkspaceDto> result = new ArrayList<>();
    for (WorkspaceImpl workspace : workspaces) {
      result.add(
          asDto(workspace).withLinks(linksGenerator.genLinks(workspace, getServiceContext())));
    }
    return result;
  }

  private WorkspaceDto asDtoWithLinksAndToken(WorkspaceImpl workspace) throws ServerException {
    WorkspaceDto workspaceDto =
        asDto(workspace).withLinks(linksGenerator.genLinks(workspace, getServiceContext()));

    RuntimeDto runtimeDto = workspaceDto.getRuntime();
    if (runtimeDto != null) {
      try {
        runtimeDto.setMachineToken(machineTokenProvider.getToken(workspace.getId()));
      } catch (MachineAccessForbidden e) {
        // set runtime to null since user doesn't have the required permissions
        workspaceDto.setRuntime(null);
      } catch (MachineTokenException e) {
        throw new ServerException(e.getMessage(), e);
      }
    }

    return workspaceDto;
  }

  private WorkspaceDto filterServers(WorkspaceDto workspace, boolean includeInternal) {
    // no runtime - nothing to filter
    if (workspace.getRuntime() == null) {
      return workspace;
    }
    // if it is needed to include internal there is nothing to filter
    if (includeInternal) {
      return workspace;
    }
    for (MachineDto machine : workspace.getRuntime().getMachines().values()) {
      Map<String, ServerDto> filteredServers = new HashMap<>();
      machine
          .getServers()
          .forEach(
              (name, server) -> {
                if (!ServerConfig.isInternal(server.getAttributes())) {
                  filteredServers.put(name, server);
                }
              });
      machine.withServers(filteredServers);
    }

    return workspace;
  }
}
