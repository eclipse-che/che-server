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
package org.eclipse.che.api.core.rest;

import static io.restassured.RestAssured.expect;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.everrest.assured.EverrestJetty;
import org.hamcrest.Matchers;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(value = {EverrestJetty.class})
public class RuntimeExceptionMapperTest {
  @Path("/runtime-exception")
  public static class RuntimeExceptionService {
    @GET
    @Path("/re-empty-msg")
    public String reWithEmptyMessage() {
      throw new NullPointerException();
    }
  }

  RuntimeExceptionService service;

  RuntimeExceptionMapper mapper;

  @Test
  public void shouldHandleRuntimeException() {
    final String expectedErrorMessage =
        "{\"message\":\"Internal Server Error occurred, error time:";

    expect()
        .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
        .body(Matchers.startsWith(expectedErrorMessage))
        .when()
        .get("/runtime-exception/re-empty-msg");
  }
}
