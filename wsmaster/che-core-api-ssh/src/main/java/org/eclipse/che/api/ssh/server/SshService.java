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
package org.eclipse.che.api.ssh.server;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.api.ssh.shared.Constants.LINK_REL_GET_PAIR;
import static org.eclipse.che.api.ssh.shared.Constants.LINK_REL_REMOVE_PAIR;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.fileupload.FileItem;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.annotations.GenerateLink;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.core.util.LinksHelper;
import org.eclipse.che.api.ssh.server.model.impl.SshPairImpl;
import org.eclipse.che.api.ssh.shared.Constants;
import org.eclipse.che.api.ssh.shared.dto.GenerateSshPairRequest;
import org.eclipse.che.api.ssh.shared.dto.SshPairDto;
import org.eclipse.che.api.ssh.shared.model.SshPair;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Defines Ssh Rest API.
 *
 * @author Sergii Leschenko
 */
@Tag(name = "ssh", description = "Ssh REST API")
@Path("/ssh")
public class SshService extends Service {
  private final SshManager sshManager;

  @Inject
  public SshService(SshManager sshManager) {
    this.sshManager = sshManager;
  }

  @POST
  @Path("generate")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @GenerateLink(rel = Constants.LINK_REL_GENERATE_PAIR)
  @Operation(
      summary =
          "Generate and stores ssh pair based on the request. This operation can be performed only by authorized user,"
              + "this user will be the owner of the created ssh pair",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "The ssh pair successfully generated",
            content = @Content(schema = @Schema(implementation = SshPairDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(
            responseCode = "409",
            description =
                "Conflict error occurred during the ssh pair generation"
                    + "(e.g. The Ssh pair with such name and service already exists)"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public Response generatePair(
      @Parameter(description = "The configuration to generate the new ssh pair", required = true)
          GenerateSshPairRequest request)
      throws BadRequestException, ServerException, ConflictException {
    requiredNotNull(request, "Generate ssh pair request required");
    requiredNotNull(request.getService(), "Service name required");
    requiredNotNull(request.getName(), "Name required");
    final SshPairImpl generatedPair =
        sshManager.generatePair(getCurrentUserId(), request.getService(), request.getName());

    return Response.status(Response.Status.CREATED)
        .entity(asDto(injectLinks(asDto(generatedPair))))
        .build();
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.TEXT_HTML)
  @GenerateLink(rel = Constants.LINK_REL_CREATE_PAIR)
  public Response createPair(Iterator<FileItem> formData)
      throws BadRequestException, ServerException, ConflictException {
    String service = null;
    String name = null;
    String privateKey = null;
    String publicKey = null;

    while (formData.hasNext()) {
      FileItem item = formData.next();
      String fieldName = item.getFieldName();
      switch (fieldName) {
        case "service":
          service = item.getString();
          break;
        case "name":
          name = item.getString();
          break;
        case "privateKey":
          privateKey = item.getString();
          break;
        case "publicKey":
          publicKey = item.getString();
          break;
        default:
          // do nothing
      }
    }

    requiredNotNull(service, "Service name required");
    requiredNotNull(name, "Name required");
    if (privateKey == null && publicKey == null) {
      throw new BadRequestException("Key content was not provided.");
    }

    sshManager.createPair(
        new SshPairImpl(getCurrentUserId(), service, name, publicKey, privateKey));

    // We should send 200 response code and body with empty line
    // through specific of html form that doesn't invoke complete submit handler
    return Response.ok("", MediaType.TEXT_HTML).build();
  }

  @POST
  @Consumes(APPLICATION_JSON)
  @GenerateLink(rel = Constants.LINK_REL_CREATE_PAIR)
  @Operation(
      summary =
          "Create a new ssh pair. This operation can be performed only by authorized user,"
              + "this user will be the owner of the created ssh pair",
      responses = {
        @ApiResponse(responseCode = "204", description = "The ssh pair successfully created"),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(
            responseCode = "409",
            description =
                "Conflict error occurred during the ssh pair creation"
                    + "(e.g. The Ssh pair with such name and service already exists)"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public void createPair(
      @Parameter(description = "The ssh pair to create", required = true) SshPairDto sshPair)
      throws BadRequestException, ServerException, ConflictException {
    requiredNotNull(sshPair, "Ssh pair required");
    requiredNotNull(sshPair.getService(), "Service name required");
    requiredNotNull(sshPair.getName(), "Name required");
    if (sshPair.getPublicKey() == null && sshPair.getPrivateKey() == null) {
      throw new BadRequestException("Key content was not provided.");
    }

    sshManager.createPair(new SshPairImpl(getCurrentUserId(), sshPair));
  }

  @GET
  @Path("{service}/find")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Get the ssh pair by the name of pair and name of service owned by the current user. This operation can be performed only by authorized user.",
      responses = {
        @ApiResponse(responseCode = "200", description = "The ssh pair successfully fetched"),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(
            responseCode = "404",
            description =
                "The ssh pair with specified name and service does not exist for current user"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public SshPairDto getPair(
      @Parameter(description = "Name of service") @PathParam("service") String service,
      @Parameter(description = "Name of ssh pair", required = true) @QueryParam("name") String name)
      throws NotFoundException, ServerException, BadRequestException {
    requiredNotNull(name, "Name of ssh pair");
    return injectLinks(asDto(sshManager.getPair(getCurrentUserId(), service, name)));
  }

  @DELETE
  @Path("{service}")
  @Operation(
      summary =
          "Remove the ssh pair by the name of pair and name of service owned by the current user",
      responses = {
        @ApiResponse(responseCode = "204", description = "The ssh pair successfully removed"),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, parameters are not valid"),
        @ApiResponse(responseCode = "404", description = "The ssh pair doesn't exist"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public void removePair(
      @Parameter(description = "Name of service") @PathParam("service") String service,
      @Parameter(description = "Name of ssh pair", required = true) @QueryParam("name") String name)
      throws ServerException, NotFoundException, BadRequestException {
    requiredNotNull(name, "Name of ssh pair");
    sshManager.removePair(getCurrentUserId(), service, name);
  }

  @GET
  @Path("{service}")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Get the ssh pairs by name of service owned by the current user. This operation can be performed only by authorized user.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The ssh pairs successfully fetched",
            content =
                @Content(
                    array = @ArraySchema(schema = @Schema(implementation = SshPairDto.class)))),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public List<SshPairDto> getPairs(
      @Parameter(description = "Name of service") @PathParam("service") String service)
      throws ServerException {
    return sshManager.getPairs(getCurrentUserId(), service).stream()
        .map(sshPair -> injectLinks(asDto(sshPair)))
        .collect(Collectors.toList());
  }

  private static String getCurrentUserId() {
    return EnvironmentContext.getCurrent().getSubject().getUserId();
  }

  private static SshPairDto asDto(SshPair pair) {
    return newDto(SshPairDto.class)
        .withService(pair.getService())
        .withName(pair.getName())
        .withPublicKey(pair.getPublicKey())
        .withPrivateKey(pair.getPrivateKey());
  }

  private SshPairDto injectLinks(SshPairDto sshPairDto) {
    final UriBuilder uriBuilder = getServiceContext().getServiceUriBuilder();
    final Link getPairsLink =
        LinksHelper.createLink(
            "GET",
            uriBuilder
                .clone()
                .path(getClass(), "getPairs")
                .build(sshPairDto.getService())
                .toString(),
            APPLICATION_JSON,
            LINK_REL_GET_PAIR);

    final Link removePairLink =
        LinksHelper.createLink(
            "DELETE",
            uriBuilder
                .clone()
                .path(getClass(), "removePair")
                .build(sshPairDto.getService(), sshPairDto.getName())
                .toString(),
            APPLICATION_JSON,
            LINK_REL_REMOVE_PAIR);

    final Link getPairLink =
        LinksHelper.createLink(
            "GET",
            uriBuilder
                .clone()
                .path(getClass(), "getPair")
                .build(sshPairDto.getService(), sshPairDto.getName())
                .toString(),
            APPLICATION_JSON,
            LINK_REL_GET_PAIR);

    return sshPairDto.withLinks(Arrays.asList(getPairsLink, removePairLink, getPairLink));
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
}
