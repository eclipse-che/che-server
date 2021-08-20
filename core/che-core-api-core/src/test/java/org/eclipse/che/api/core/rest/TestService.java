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

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.core.rest.shared.dto.ServiceError;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.dto.server.JsonArrayImpl;

/**
 * Test service class, used in {@link DefaultHttpJsonRequestTest}.
 *
 * @author Yevhenii Voevodin
 */
@Path("/test")
public class TestService extends Service {

  public static final String JSON_OBJECT = new JsonArrayImpl<>(singletonList("element")).toJson();

  @GET
  @Path("/{response-code}/response-code-test")
  public Response getRequestedResponseCode(@PathParam("response-code") int responseCode) {
    return Response.status(responseCode)
        .entity(DtoFactory.newDto(ServiceError.class).withMessage("response code test method"))
        .build();
  }

  @GET
  @Path("/text-plain")
  @Produces(TEXT_PLAIN)
  public String getTextPlain() {
    return "this is text/plain message";
  }

  @GET
  @Path("/application-json")
  @Produces(APPLICATION_JSON)
  public String getJsonObject() {
    return JSON_OBJECT;
  }

  @POST
  @Path("/application-json")
  @Produces(APPLICATION_JSON)
  public List<Link> receiveJsonObject(List<Link> elements) {
    return elements;
  }

  @PUT
  @Path("/query-parameters")
  @Produces(APPLICATION_JSON)
  public Map<String, String> queryParamsTest(
      @QueryParam("param1") String qp1, @QueryParam("param2") String qp2) {
    final Map<String, String> map = new HashMap<>();
    map.put("param1", qp1);
    map.put("param2", qp2);
    return map;
  }

  @PUT
  @Path("/multi-query-parameters")
  @Produces(APPLICATION_JSON)
  public Map<String, List<String>> queryParamsTest(@QueryParam("param1") List<String> values) {
    final Map<String, List<String>> map = new HashMap<>();
    map.put("param1", values);
    return map;
  }

  @POST
  @Path("/token")
  public void checkAuthorization(@HeaderParam(HttpHeaders.AUTHORIZATION) String token)
      throws UnauthorizedException {
    if (!EnvironmentContext.getCurrent().getSubject().getToken().equals(token)) {
      throw new UnauthorizedException(
          "Token '" + token + "' it is different from token in EnvironmentContext");
    }
  }

  @GET
  @Path("/decode")
  @Produces(APPLICATION_JSON)
  public String getUriInfo(@QueryParam("query") String query, @Context UriInfo uriInfo) {
    return URLDecoder.decode(uriInfo.getRequestUri().toString());
  }

  @GET
  @Path("/paging/{value}")
  @Produces(APPLICATION_JSON)
  public Response getStringList(
      @PathParam("value") String value, @QueryParam("query-param") String param) {
    final Page<String> page = new Page<>(asList("item3", "item4", "item5"), 3, 3, 7);

    return Response.ok()
        .entity(page.getItems())
        .header(
            "Link",
            createLinkHeader(page, "getStringList", singletonMap("query-param", param), value))
        .build();
  }

  @DELETE
  @Path("no-content")
  public Response noContent() {
    return Response.noContent().build();
  }
}
