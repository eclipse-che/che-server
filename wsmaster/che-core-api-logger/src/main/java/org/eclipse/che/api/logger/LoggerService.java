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
package org.eclipse.che.api.logger;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.logger.shared.dto.LoggerDto;
import org.slf4j.LoggerFactory;

/**
 * Defines Logger REST API. It allows to manage the loggers (with log level) dynamically.
 *
 * @author Florent Benoit
 */
@Tag(name = "logger", description = "Logger REST API")
@Path("/logger")
public class LoggerService extends Service {

  @GET
  @Path("/{name}")
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Get the logger level for the given logger",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The response contains requested logger entity"),
        @ApiResponse(
            responseCode = "404",
            description = "The logger with specified name does not exist")
      })
  public LoggerDto getLoggerByName(
      @Parameter(description = "logger name") @PathParam("name") String name)
      throws NotFoundException {
    return asDto(getLogger(name));
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Operation(
      summary =
          "Get loggers which are configured. This operation can be performed only by authorized user",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The loggers successfully fetched",
            content =
                @Content(array = @ArraySchema(schema = @Schema(implementation = LoggerDto.class)))),
      })
  public List<LoggerDto> getLoggers(
      @Parameter(description = "The number of the items to skip")
          @DefaultValue("0")
          @QueryParam("skipCount")
          Integer skipCount,
      @Parameter(description = "The limit of the items in the response, default is 30")
          @DefaultValue("30")
          @QueryParam("maxItems")
          Integer maxItems) {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    return loggerContext.getLoggerList().stream()
        .filter(log -> log.getLevel() != null || log.iteratorForAppenders().hasNext())
        .skip(skipCount)
        .limit(maxItems)
        .map(this::asDto)
        .collect(Collectors.toList());
  }

  protected LoggerDto asDto(final Logger log) {
    return newDto(LoggerDto.class).withName(log.getName()).withLevel(log.getLevel().levelStr);
  }

  @PUT
  @Path("/{name}")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Update the logger level",
      responses = {
        @ApiResponse(responseCode = "200", description = "The logger successfully updated"),
      })
  public LoggerDto updateLogger(
      @Parameter(description = "logger name") @PathParam("name") String name, LoggerDto update)
      throws NotFoundException {
    Logger logger = getLogger(name);
    logger.setLevel(Level.toLevel(update.getLevel()));
    return asDto(logger);
  }

  @POST
  @Path("/{name}")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Create a new logger level",
      responses = {
        @ApiResponse(responseCode = "200", description = "The logger successfully created"),
      })
  public LoggerDto createLogger(
      @Parameter(description = "logger name") @PathParam("name") String name,
      LoggerDto createdLogger)
      throws NotFoundException {
    Logger logger = getLogger(name, false);
    logger.setLevel(Level.toLevel(createdLogger.getLevel()));
    return asDto(logger);
  }

  /** Check if given logger exists */
  protected Logger getLogger(String name) throws NotFoundException {
    return getLogger(name, true);
  }

  /**
   * Gets a logger, if checkLevel is true and if logger has no level defined it will return a
   * NameNotFound exception
   */
  protected Logger getLogger(String name, boolean checkLevel) throws NotFoundException {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    Logger log = loggerContext.getLogger(name);
    if (checkLevel && log.getLevel() == null) {
      throw new NotFoundException("The logger with name " + name + " is not existing.");
    }
    return log;
  }
}
