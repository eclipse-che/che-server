/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.security.oauth;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.gson.JsonObject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Base64;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ForbiddenException;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class OAuthIdeRedirectServiceTest {

  private static final String CHE_API = "https://che-server.apps.cluster.example.com/api";
  private OAuthIdeRedirectService service;

  @BeforeMethod
  public void setUp() {
    service = new OAuthIdeRedirectService(CHE_API, null);
  }

  @Test
  public void shouldRedirectWithCodeAndState() throws Exception {
    String csrfState = "random-csrf-state-123";
    String callbackUrl =
        "https://che-server.apps.cluster.example.com/callback?vscode-reqid=1&vscode-scheme=vscode&vscode-authority=gitlab.gitlab-workflow&vscode-path=%2Fauthentication";

    String compositeState = encodeCompositeState(csrfState, callbackUrl);
    UriInfo uriInfo = mockUriInfo("code", "auth-code-abc", "state", compositeState);

    Response response = service.ideRedirect(uriInfo);

    assertEquals(response.getStatus(), 307);
    URI location = (URI) response.getMetadata().getFirst("Location");
    String locationStr = location.toString();
    assertTrue(locationStr.startsWith(callbackUrl));
    assertTrue(locationStr.contains("code=auth-code-abc"));
    assertTrue(locationStr.contains("state=random-csrf-state-123"));
  }

  @Test
  public void shouldForwardErrorParams() throws Exception {
    String csrfState = "csrf";
    String callbackUrl = "https://che-server.apps.cluster.example.com/callback?vscode-reqid=1";
    String compositeState = encodeCompositeState(csrfState, callbackUrl);

    UriInfo uriInfo =
        mockUriInfo(
            "state", compositeState,
            "error", "access_denied",
            "error_description", "User denied access");

    Response response = service.ideRedirect(uriInfo);

    URI location = (URI) response.getMetadata().getFirst("Location");
    String locationStr = location.toString();
    assertTrue(locationStr.contains("error=access_denied"));
    assertTrue(locationStr.contains("error_description=User+denied+access"));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectMissingState() throws Exception {
    UriInfo uriInfo = mockUriInfo("code", "abc");
    service.ideRedirect(uriInfo);
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectInvalidBase64State() throws Exception {
    UriInfo uriInfo = mockUriInfo("state", "not-valid-base64!!!", "code", "abc");
    service.ideRedirect(uriInfo);
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectStateMissingFields() throws Exception {
    JsonObject json = new JsonObject();
    json.addProperty("x", "something");
    String badState =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().getBytes());
    UriInfo uriInfo = mockUriInfo("state", badState, "code", "abc");
    service.ideRedirect(uriInfo);
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectNonHttpsCallback() throws Exception {
    String compositeState = encodeCompositeState("csrf", "http://workspace.example.com/callback");
    UriInfo uriInfo = mockUriInfo("state", compositeState, "code", "abc");
    service.ideRedirect(uriInfo);
  }

  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRejectCallbackHostNotInAllowlist() throws Exception {
    service = new OAuthIdeRedirectService(CHE_API, "*.apps.cluster.example.com");
    String compositeState = encodeCompositeState("csrf", "https://evil.attacker.com/callback");
    UriInfo uriInfo = mockUriInfo("state", compositeState, "code", "abc");
    service.ideRedirect(uriInfo);
  }

  @Test
  public void shouldAllowCallbackHostMatchingPattern() throws Exception {
    service = new OAuthIdeRedirectService(CHE_API, "*.apps.cluster.example.com");
    String compositeState =
        encodeCompositeState("csrf", "https://workspace.apps.cluster.example.com/callback");
    UriInfo uriInfo = mockUriInfo("state", compositeState, "code", "abc");

    Response response = service.ideRedirect(uriInfo);
    assertEquals(response.getStatus(), 307);
  }

  @Test
  public void shouldDeriveAllowedHostFromCheApi() throws Exception {
    // Default: no explicit allowedCallbackHosts, should allow exact Che server host
    service = new OAuthIdeRedirectService(CHE_API, null);
    String compositeState =
        encodeCompositeState(
            "csrf", "https://che-server.apps.cluster.example.com/callback?vscode-reqid=1");
    UriInfo uriInfo = mockUriInfo("state", compositeState, "code", "abc");

    Response response = service.ideRedirect(uriInfo);
    assertEquals(response.getStatus(), 307);
  }

  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRejectSubdomainWhenDefaultExactMatch() throws Exception {
    // Without explicit allowed_hosts, subdomains of the cluster should be rejected
    service = new OAuthIdeRedirectService(CHE_API, null);
    String compositeState =
        encodeCompositeState(
            "csrf", "https://workspace.apps.cluster.example.com/callback?vscode-reqid=1");
    UriInfo uriInfo = mockUriInfo("state", compositeState, "code", "abc");
    service.ideRedirect(uriInfo);
  }

  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRejectHostNotMatchingDerivedPattern() throws Exception {
    service = new OAuthIdeRedirectService(CHE_API, null);
    String compositeState = encodeCompositeState("csrf", "https://evil.other-domain.com/callback");
    UriInfo uriInfo = mockUriInfo("state", compositeState, "code", "abc");
    service.ideRedirect(uriInfo);
  }

  @Test
  public void matchesHostPattern_wildcardMatch() {
    assertTrue(
        OAuthIdeRedirectService.matchesHostPattern(
            "workspace.apps.cluster.example.com", "*.apps.cluster.example.com"));
  }

  @Test
  public void matchesHostPattern_exactDomainAfterWildcard() {
    assertTrue(
        OAuthIdeRedirectService.matchesHostPattern(
            "apps.cluster.example.com", "*.apps.cluster.example.com"));
  }

  @Test
  public void matchesHostPattern_exactMatch() {
    assertTrue(
        OAuthIdeRedirectService.matchesHostPattern(
            "che-server.example.com", "che-server.example.com"));
  }

  @Test
  public void matchesHostPattern_noMatch() {
    assertFalse(
        OAuthIdeRedirectService.matchesHostPattern("evil.com", "*.apps.cluster.example.com"));
  }

  private static String encodeCompositeState(String csrfState, String callbackUrl) {
    JsonObject json = new JsonObject();
    json.addProperty("s", csrfState);
    json.addProperty("c", callbackUrl);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().getBytes());
  }

  private static UriInfo mockUriInfo(String... keyValues) {
    MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      params.putSingle(keyValues[i], keyValues[i + 1]);
    }
    UriInfo uriInfo = Mockito.mock(UriInfo.class);
    Mockito.when(uriInfo.getQueryParameters()).thenReturn(params);
    return uriInfo;
  }
}
