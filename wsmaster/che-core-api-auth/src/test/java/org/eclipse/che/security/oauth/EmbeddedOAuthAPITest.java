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
package org.eclipse.che.security.oauth;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher.OAUTH_2_PREFIX;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.eclipse.che.security.oauth.OAuthAuthenticator.SSL_ERROR_CODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.Set;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.security.oauth.shared.dto.OAuthAuthenticatorDescriptor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Mykhailo Kuznietsov */
@Listeners(value = MockitoTestNGListener.class)
public class EmbeddedOAuthAPITest {

  @Mock OAuthAuthenticatorProvider oauth2Providers;
  @Mock org.eclipse.che.security.oauth1.OAuthAuthenticatorProvider oauth1Providers;
  @Mock PersonalAccessTokenManager personalAccessTokenManager;

  @InjectMocks EmbeddedOAuthAPI embeddedOAuthAPI;

  @Test(
      expectedExceptions = NotFoundException.class,
      expectedExceptionsMessageRegExp = "Unsupported OAuth provider unknown")
  public void shouldThrowExceptionIfNoSuchProviderFound() throws Exception {
    embeddedOAuthAPI.getOrRefreshToken("unknown");
  }

  @Test
  public void shouldBeAbleToGetUserToken() throws Exception {
    String provider = "myprovider";
    String token = "token123";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(eq(provider))).thenReturn(authenticator);

    when(authenticator.getOrRefreshToken(anyString()))
        .thenReturn(newDto(OAuthToken.class).withToken(token));

    OAuthToken result = embeddedOAuthAPI.getOrRefreshToken(provider);

    assertEquals(result.getToken(), token);
  }

  @Test
  public void shouldGetRegisteredAuthenticators() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://eclipse.che"));
    when(oauth2Providers.getRegisteredProviderNames()).thenReturn(Set.of("github"));
    when(oauth1Providers.getRegisteredProviderNames()).thenReturn(Set.of("bitbucket"));
    org.eclipse.che.security.oauth1.OAuthAuthenticator authenticator =
        mock(org.eclipse.che.security.oauth1.OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator("github")).thenReturn(mock(OAuthAuthenticator.class));
    when(oauth1Providers.getAuthenticator("bitbucket")).thenReturn(authenticator);

    // when
    Set<OAuthAuthenticatorDescriptor> registeredAuthenticators =
        embeddedOAuthAPI.getRegisteredAuthenticators(uriInfo);

    // then
    assertEquals(registeredAuthenticators.size(), 2);
  }

  @Test
  public void shouldEncodeRejectErrorForRedirectUrl() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri()).thenReturn(new URI("http://eclipse.che"));
    Field redirectAfterLogin = EmbeddedOAuthAPI.class.getDeclaredField("redirectAfterLogin");
    redirectAfterLogin.setAccessible(true);
    redirectAfterLogin.set(embeddedOAuthAPI, "http://eclipse.che?quary=param");

    // when
    Response callback = embeddedOAuthAPI.callback(uriInfo, singletonList("access_denied"));

    // then
    assertEquals(
        callback.getLocation().toString(),
        "http://eclipse.che?quary%3Dparam%26error_code%3Daccess_denied");
  }

  @Test
  public void shouldAddSslErrorCode() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.callback(any(URL.class), anyList()))
        .thenThrow(new ScmCommunicationException("", SSL_ERROR_CODE));
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=github&redirect_after_login=https://redirecturl.com?params=", UTF_8)));
    when(oauth2Providers.getAuthenticator("github")).thenReturn(authenticator);

    // when
    Response callback = embeddedOAuthAPI.callback(uriInfo, singletonList("ssl_exception"));

    // then
    assertEquals(
        callback.getLocation().toString(),
        "https://redirecturl.com?params=&error_code=ssl_exception");
  }

  @Test
  public void shouldStoreTokenOnCallback() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.getEndpointUrl()).thenReturn("http://eclipse.che");
    when(authenticator.callback(any(URL.class), anyList())).thenReturn("token");
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider%3Dgithub%26redirect_after_login%3DredirectUrl"));
    when(oauth2Providers.getAuthenticator("github")).thenReturn(authenticator);
    ArgumentCaptor<PersonalAccessToken> tokenCapture =
        ArgumentCaptor.forClass(PersonalAccessToken.class);

    // when
    embeddedOAuthAPI.callback(uriInfo, emptyList());

    // then
    verify(personalAccessTokenManager).store(tokenCapture.capture());
    PersonalAccessToken token = tokenCapture.getValue();
    assertEquals(token.getScmProviderUrl(), "http://eclipse.che");
    assertEquals(token.getCheUserId(), "0000-00-0000");
    assertTrue(token.getScmTokenId().startsWith("id-"));
    assertTrue(token.getScmTokenName().startsWith(OAUTH_2_PREFIX));
    assertEquals(token.getToken(), "token");
  }

  @Test
  public void shouldStoreBitbucketTokenOnCallback() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.getEndpointUrl()).thenReturn("http://eclipse.che");
    when(authenticator.callback(any(URL.class), anyList())).thenReturn("token");
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider%3Dbitbucket%26redirect_after_login%3DredirectUrl"));
    when(oauth2Providers.getAuthenticator("bitbucket")).thenReturn(authenticator);
    ArgumentCaptor<PersonalAccessToken> tokenCapture =
        ArgumentCaptor.forClass(PersonalAccessToken.class);

    // when
    embeddedOAuthAPI.callback(uriInfo, emptyList());

    // then
    verify(personalAccessTokenManager).store(tokenCapture.capture());
    PersonalAccessToken token = tokenCapture.getValue();
    assertEquals(token.getScmProviderUrl(), "http://eclipse.che");
    assertEquals(token.getScmProviderName(), "bitbucket");
    assertEquals(token.getCheUserId(), "0000-00-0000");
    assertTrue(token.getScmTokenId().startsWith("id-"));
    assertTrue(token.getScmTokenName().startsWith("bitbucket-"));
    assertEquals(token.getToken(), "token");
  }

  @Test
  public void shouldEncodeRedirectUrl() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=github&redirect_after_login=https://redirecturl.com?params="
                            + encode("{}", UTF_8),
                        UTF_8)));
    when(oauth2Providers.getAuthenticator("github")).thenReturn(authenticator);

    // when
    Response callback = embeddedOAuthAPI.callback(uriInfo, emptyList());

    // then
    assertEquals(callback.getLocation().toString(), "https://redirecturl.com?params%3D%7B%7D");
  }

  @Test
  public void shouldNotEncodeRedirectUrl() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=github&redirect_after_login=https://redirecturl.com?params="
                            + encode(encode("{}", UTF_8), UTF_8),
                        UTF_8)));
    when(oauth2Providers.getAuthenticator("github")).thenReturn(authenticator);

    // when
    Response callback = embeddedOAuthAPI.callback(uriInfo, emptyList());

    // then
    assertEquals(callback.getLocation().toString(), "https://redirecturl.com?params=%7B%7D");
  }
}
