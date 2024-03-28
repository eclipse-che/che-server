/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.security.oauth1;

import static io.restassured.RestAssured.given;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_NAME;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_PASSWORD;
import static org.everrest.assured.JettyHttpServer.SECURE_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import io.restassured.response.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import org.eclipse.che.api.core.rest.Service;
import org.everrest.assured.EverrestJetty;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({EverrestJetty.class, MockitoTestNGListener.class})
public class OAuthAuthenticationServiceTest {

  private final String REDIRECT_URI = "/dashboard";
  private final String STATE =
      "oauth_provider=test-server&request_method=POST&signature_method=rsa&redirect_after_login="
          + REDIRECT_URI;
  private final String OAUTH_TOKEN = "JeZlJxu8bd1ewAmCkG668PCLC5kJ9ne1";
  private final String OAUTH_VERIFIER = "hfdp7dh39dks9884";

  @Mock private OAuthAuthenticator oAuthAuthenticator;

  @Mock private UriInfo uriInfo;

  @Mock private OAuthAuthenticatorProvider oAuthProvider;

  @InjectMocks private OAuthAuthenticationService oAuthAuthenticationService;

  @Test
  public void shouldResolveCallbackWithoutError() throws OAuthAuthenticationException {
    when(oAuthProvider.getAuthenticator("test-server")).thenReturn(oAuthAuthenticator);
    when(oAuthAuthenticator.callback(any(URL.class))).thenReturn("user1");
    final Response response =
        given()
            .redirects()
            .follow(false)
            .queryParam("state", STATE)
            .queryParam("oauth_token", OAUTH_TOKEN)
            .queryParam("oauth_verifier", OAUTH_VERIFIER)
            .auth()
            .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
            .when()
            .get(SECURE_PATH + "/oauth/1.0/callback");
    assertEquals(response.statusCode(), 307);
    assertEquals(response.header("Location"), REDIRECT_URI);
  }

  @Test
  public void shouldResolveCallbackWithAccessDeniedError() throws OAuthAuthenticationException {
    when(oAuthProvider.getAuthenticator("test-server")).thenReturn(oAuthAuthenticator);
    when(oAuthAuthenticator.callback(any(URL.class)))
        .thenThrow(new UserDeniedOAuthAuthenticationException("Access denied"));
    final Response response =
        given()
            .redirects()
            .follow(false)
            .queryParam("state", STATE)
            .queryParam("oauth_token", OAUTH_TOKEN)
            .queryParam("oauth_verifier", "denied")
            .auth()
            .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
            .when()
            .get(SECURE_PATH + "/oauth/1.0/callback");
    assertEquals(response.statusCode(), 307);
    assertEquals(response.header("Location"), REDIRECT_URI + "?error_code=access_denied");
  }

  @Test
  public void shouldResolveCallbackWithInvalidRequestError() throws OAuthAuthenticationException {
    when(oAuthProvider.getAuthenticator("test-server")).thenReturn(oAuthAuthenticator);
    when(oAuthAuthenticator.callback(any(URL.class)))
        .thenThrow(new OAuthAuthenticationException("Invalid request"));
    final Response response =
        given()
            .redirects()
            .follow(false)
            .queryParam("state", STATE)
            .queryParam("oauth_token", OAUTH_TOKEN)
            .queryParam("oauth_verifier", OAUTH_VERIFIER)
            .auth()
            .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
            .when()
            .get(SECURE_PATH + "/oauth/1.0/callback");
    assertEquals(response.statusCode(), 307);
    assertEquals(response.header("Location"), REDIRECT_URI + "?error_code=invalid_request");
  }

  @Test
  public void shouldEncodeRedirectUrl() throws Exception {
    // given
    Field uriInfoField = Service.class.getDeclaredField("uriInfo");
    uriInfoField.setAccessible(true);
    uriInfoField.set(oAuthAuthenticationService, uriInfo);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=bitbucket-server&redirect_after_login=https://redirecturl.com?params="
                            + encode("{}", UTF_8),
                        UTF_8)));
    when(oAuthProvider.getAuthenticator("bitbucket-server"))
        .thenReturn(mock(OAuthAuthenticator.class));

    // when
    jakarta.ws.rs.core.Response callback = oAuthAuthenticationService.callback();

    // then
    assertEquals(callback.getLocation().toString(), "https://redirecturl.com?params%3D%7B%7D");
  }

  @Test
  public void shouldNotEncodeRedirectUrl() throws Exception {
    // given
    Field uriInfoField = Service.class.getDeclaredField("uriInfo");
    uriInfoField.setAccessible(true);
    uriInfoField.set(oAuthAuthenticationService, uriInfo);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=bitbucket-server&redirect_after_login=https://redirecturl.com?params="
                            + encode(encode("{}", UTF_8), UTF_8),
                        UTF_8)));
    when(oAuthProvider.getAuthenticator("bitbucket-server"))
        .thenReturn(mock(OAuthAuthenticator.class));

    // when
    jakarta.ws.rs.core.Response callback = oAuthAuthenticationService.callback();

    // then
    assertEquals(callback.getLocation().toString(), "https://redirecturl.com?params=%7B%7D");
  }
}
