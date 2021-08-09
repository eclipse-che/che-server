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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API for JVM manipulations.
 *
 * @author Sergii Kabashniuk
 */
@Tag(name = "jvm", description = "API for JVM manipulations")
@Path("/jvm")
public class JvmService {

  private static final Logger LOG = LoggerFactory.getLogger(JvmService.class);

  private final JvmManager manager;

  @Inject
  public JvmService(JvmManager manager) {
    this.manager = manager;
  }

  @GET
  @Path("/dump/thread")
  @Produces(MediaType.TEXT_PLAIN)
  @Operation(
      summary = "Get thread dump of jvm",
      responses = {
        @ApiResponse(responseCode = "200", description = "The response contains thread dump"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public StreamingOutput threadDump() {
    return manager::writeThreadDump;
  }

  @GET
  @Path("/dump/heap")
  @Produces("application/zip")
  @Operation(
      summary = "Get heap dump of jvm",
      responses = {
        @ApiResponse(responseCode = "200", description = "The response contains jvm heap dump"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public Response heapDump() throws IOException {
    File heapDump = manager.createZippedHeapDump();
    heapDump.deleteOnExit();
    return Response.ok(
            new FileInputStream(heapDump) {
              @Override
              public void close() throws IOException {
                super.close();
                if (!heapDump.delete()) {
                  LOG.warn("Not able to delete temporary file {}", heapDump);
                }
              }
            },
            "application/zip")
        .header("Content-Length", String.valueOf(Files.size(heapDump.toPath())))
        .header("Content-Disposition", "attachment; filename=heapdump.hprof.zip")
        .build();
  }
}
