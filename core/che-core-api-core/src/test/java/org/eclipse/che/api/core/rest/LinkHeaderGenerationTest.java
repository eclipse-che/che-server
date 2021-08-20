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

import static com.google.common.collect.Sets.symmetricDifference;
import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static org.eclipse.che.commons.lang.UrlUtils.getQueryParameters;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_NAME;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_PASSWORD;
import static org.everrest.assured.JettyHttpServer.SECURE_PATH;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import io.restassured.response.Response;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.che.api.core.util.PagingUtil;
import org.everrest.assured.EverrestJetty;
import org.testng.ITestContext;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests of {@link Service#createLinkHeader} methods.
 *
 * @author Yevhenii Voevodin
 */
@Listeners(EverrestJetty.class)
public class LinkHeaderGenerationTest {

  @SuppressWarnings("unused") // used by EverrestJetty
  private static final TestService TEST_SERVICE = new TestService();

  @Test
  public void linksHeaderShouldBeCorrectlyGenerated(ITestContext ctx) throws Exception {
    final Response response =
        given()
            .auth()
            .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
            .contentType("application/json")
            .when()
            .get(SECURE_PATH + "/test/paging/test-path-param?query-param=test-query-param");

    assertEquals(response.getStatusCode(), 200);

    final String headerValue = response.getHeader("Link");
    assertNotNull(headerValue, "Link header is missing in the response");

    final Map<String, String> relToLinkMap = PagingUtil.parseLinkHeader(headerValue);
    final Set<String> expectedRels = new HashSet<>(asList("first", "last", "prev", "next"));
    assertEquals(
        relToLinkMap.keySet(),
        expectedRels,
        "Rels are different " + symmetricDifference(expectedRels, relToLinkMap.keySet()));

    final String expectedUri =
        "http://localhost:"
            + ctx.getAttribute(EverrestJetty.JETTY_PORT)
            + "/rest/private/test/paging/test-path-param";
    for (String link : relToLinkMap.values()) {
      final URI uri = URI.create(link);
      final Map<String, List<String>> params = getQueryParameters(uri.toURL());
      assertEquals(params.size(), 3);
      assertNotNull(params.get("skipCount"));
      assertNotNull(params.get("maxItems"));
      assertEquals(params.get("query-param").get(0), "test-query-param");
      assertEquals(link, expectedUri + '?' + uri.getQuery());
    }
  }
}
