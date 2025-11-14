/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.user.server;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import javax.inject.Inject;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * User REST API.
 *
 * @author Yevhenii Voevodin
 * @author Anton Korneta
 */
@Path("/user")
@Tag(name = "user", description = "User REST API")
public class UserService extends Service {

  @Inject
  public UserService() {}

  @GET
  @Path("/id")
  @Produces(TEXT_PLAIN)
  @Operation(
      summary = "Get current user's id",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description =
                "The response contains current user's id ('0000-00-0000' is returned for the anonymous user)"),
      })
  public String getId() {
    return userId();
  }

  private static String userId() {
    return EnvironmentContext.getCurrent().getSubject().getUserId();
  }
}
