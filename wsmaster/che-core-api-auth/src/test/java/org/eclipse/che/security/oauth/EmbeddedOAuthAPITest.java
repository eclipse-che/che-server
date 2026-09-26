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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.TokenResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.Set;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.shared.dto.OAuthAuthenticatorDescriptor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * @author Mykhailo Kuznietsov
 */
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
  public void shouldRestoreCredentialAndRefreshPersistedTokenOnGet() throws Exception {
    // given
    String provider = "github";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(provider)).thenReturn(authenticator);
    // the in-memory credential store is empty, e.g. after a server restart
    when(authenticator.getOrRefreshToken(anyString())).thenReturn(null);
    when(authenticator.refreshToken("0000-00-0000"))
        .thenReturn(newDto(OAuthToken.class).withToken("new-access-token"));

    AuthorizationCodeFlow flow = mock(AuthorizationCodeFlow.class);
    Field flowField = OAuthAuthenticator.class.getDeclaredField("flow");
    flowField.setAccessible(true);
    flowField.set(authenticator, flow);

    PersonalAccessToken persistedToken =
        new PersonalAccessToken(
            "https://github.com",
            provider,
            "0000-00-0000",
            null,
            null,
            "oauth2-token",
            "id-token",
            "old-access-token",
            "refresh-token-123",
            3600);
    when(personalAccessTokenManager.getStored(any(Subject.class), eq(provider), eq(null), eq(null)))
        .thenReturn(Optional.of(persistedToken));
    ArgumentCaptor<TokenResponse> tokenResponseCaptor =
        ArgumentCaptor.forClass(TokenResponse.class);

    // when
    OAuthToken result = embeddedOAuthAPI.getOrRefreshToken(provider);

    // then the persisted token is refreshed instead of being handed out as stored, since it may
    // already have expired
    assertEquals(result.getToken(), "new-access-token");
    verify(flow).createAndStoreCredential(tokenResponseCaptor.capture(), eq("0000-00-0000"));
    assertEquals(tokenResponseCaptor.getValue().getRefreshToken(), "refresh-token-123");
  }

  @Test
  public void shouldReturnPersistedTokenOnGetWhenItHasNoRefreshToken() throws Exception {
    // given
    String provider = "github";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(provider)).thenReturn(authenticator);
    when(authenticator.getOrRefreshToken(anyString())).thenReturn(null);

    // a user-supplied personal access token has nothing to refresh with
    PersonalAccessToken persistedToken =
        new PersonalAccessToken(
            "https://github.com",
            provider,
            "0000-00-0000",
            null,
            null,
            "token-name",
            "id-token",
            "persisted-token",
            null,
            0);
    when(personalAccessTokenManager.getStored(any(Subject.class), eq(provider), eq(null), eq(null)))
        .thenReturn(Optional.of(persistedToken));

    // when
    OAuthToken result = embeddedOAuthAPI.getOrRefreshToken(provider);

    // then
    assertEquals(result.getToken(), "persisted-token");
    verify(authenticator, never()).refreshToken(anyString());
  }

  @Test(
      expectedExceptions = UnauthorizedException.class,
      expectedExceptionsMessageRegExp = "OAuth token for user 0000-00-0000 was not found")
  public void shouldThrowUnauthorizedOnGetWhenRefreshOfPersistedTokenFails() throws Exception {
    // given
    String provider = "github";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(provider)).thenReturn(authenticator);
    when(authenticator.getOrRefreshToken(anyString())).thenReturn(null);
    when(authenticator.refreshToken(anyString())).thenReturn(null);

    AuthorizationCodeFlow flow = mock(AuthorizationCodeFlow.class);
    Field flowField = OAuthAuthenticator.class.getDeclaredField("flow");
    flowField.setAccessible(true);
    flowField.set(authenticator, flow);

    PersonalAccessToken persistedToken =
        new PersonalAccessToken(
            "https://github.com",
            provider,
            "0000-00-0000",
            null,
            null,
            "oauth2-token",
            "id-token",
            "old-access-token",
            "refresh-token-123",
            3600);
    when(personalAccessTokenManager.getStored(any(Subject.class), eq(provider), eq(null), eq(null)))
        .thenReturn(Optional.of(persistedToken));

    // when
    embeddedOAuthAPI.getOrRefreshToken(provider);
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
    TokenResponse tokenResponse = mock(TokenResponse.class);
    when(authenticator.getEndpointUrl()).thenReturn("http://eclipse.che");
    when(tokenResponse.getAccessToken()).thenReturn("token");
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(tokenResponse);
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
  public void shouldEncodeRedirectUrl() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(mock(TokenResponse.class));
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
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(mock(TokenResponse.class));
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

  @Test
  public void shouldIncludeClientIdForOAuth2Providers() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://eclipse.che"));
    when(oauth2Providers.getRegisteredProviderNames()).thenReturn(Set.of("github"));
    when(oauth1Providers.getRegisteredProviderNames()).thenReturn(Set.of());
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.getClientId()).thenReturn("test-client-id");
    when(oauth2Providers.getAuthenticator("github")).thenReturn(authenticator);

    // when
    Set<OAuthAuthenticatorDescriptor> descriptors =
        embeddedOAuthAPI.getRegisteredAuthenticators(uriInfo);

    // then
    assertEquals(descriptors.size(), 1);
    OAuthAuthenticatorDescriptor descriptor = descriptors.iterator().next();
    assertEquals(descriptor.getName(), "github");
    assertEquals(descriptor.getClientId(), "test-client-id");
  }

  @Test
  public void shouldHaveNullClientIdForOAuth1Providers() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://eclipse.che"));
    when(oauth2Providers.getRegisteredProviderNames()).thenReturn(Set.of());
    when(oauth1Providers.getRegisteredProviderNames()).thenReturn(Set.of("bitbucket"));
    org.eclipse.che.security.oauth1.OAuthAuthenticator oauth1Authenticator =
        mock(org.eclipse.che.security.oauth1.OAuthAuthenticator.class);
    when(oauth1Providers.getAuthenticator("bitbucket")).thenReturn(oauth1Authenticator);

    // when
    Set<OAuthAuthenticatorDescriptor> descriptors =
        embeddedOAuthAPI.getRegisteredAuthenticators(uriInfo);

    // then
    assertEquals(descriptors.size(), 1);
    OAuthAuthenticatorDescriptor descriptor = descriptors.iterator().next();
    assertEquals(descriptor.getName(), "bitbucket");
    assertNull(descriptor.getClientId());
  }

  @Test
  public void shouldStoreRefreshTokenAndExpiryOnCallback() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    TokenResponse tokenResponse = mock(TokenResponse.class);
    when(authenticator.getEndpointUrl()).thenReturn("http://eclipse.che");
    when(tokenResponse.getAccessToken()).thenReturn("access-token");
    when(tokenResponse.getRefreshToken()).thenReturn("refresh-token");
    when(tokenResponse.getExpiresInSeconds()).thenReturn(3600L);
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(tokenResponse);
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
    assertEquals(token.getToken(), "access-token");
    assertEquals(token.getRefreshToken(), "refresh-token");
    assertEquals(token.getExpiresIn(), 3600L);
  }

  @Test
  public void shouldStoreZeroExpiryOnCallbackWhenTokenResponseHasNoExpiresIn() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    TokenResponse tokenResponse = mock(TokenResponse.class);
    when(authenticator.getEndpointUrl()).thenReturn("http://eclipse.che");
    when(tokenResponse.getAccessToken()).thenReturn("access-token");
    when(tokenResponse.getRefreshToken()).thenReturn("refresh-token");
    // providers that issue non-expiring tokens omit `expires_in`
    when(tokenResponse.getExpiresInSeconds()).thenReturn(null);
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(tokenResponse);
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
    assertEquals(token.getToken(), "access-token");
    assertEquals(token.getRefreshToken(), "refresh-token");
    assertEquals(token.getExpiresIn(), 0L);
  }

  @Test
  public void shouldRestoreCredentialFromPersistedTokenOnRefresh() throws Exception {
    // given
    String provider = "github";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(provider)).thenReturn(authenticator);

    OAuthToken refreshedToken =
        newDto(OAuthToken.class).withToken("new-access-token").withRefreshToken("new-refresh");
    when(authenticator.refreshToken("0000-00-0000")).thenReturn(null).thenReturn(refreshedToken);
    when(authenticator.refreshToken("Anonymous")).thenReturn(null);

    AuthorizationCodeFlow flow = mock(AuthorizationCodeFlow.class);
    Field flowField = OAuthAuthenticator.class.getDeclaredField("flow");
    flowField.setAccessible(true);
    flowField.set(authenticator, flow);

    PersonalAccessToken persistedToken =
        new PersonalAccessToken(
            "https://github.com",
            provider,
            "0000-00-0000",
            null,
            null,
            "oauth2-token",
            "id-token",
            "old-access-token",
            "refresh-token-123",
            3600);
    when(personalAccessTokenManager.getStored(any(Subject.class), eq(provider), eq(null), eq(null)))
        .thenReturn(Optional.of(persistedToken));
    ArgumentCaptor<TokenResponse> tokenResponseCaptor =
        ArgumentCaptor.forClass(TokenResponse.class);

    // when
    OAuthToken result = embeddedOAuthAPI.refreshToken(provider);

    // then
    assertEquals(result.getToken(), "new-access-token");
    verify(flow).createAndStoreCredential(tokenResponseCaptor.capture(), eq("0000-00-0000"));
    TokenResponse tokenResponse = tokenResponseCaptor.getValue();
    assertEquals(tokenResponse.getAccessToken(), "old-access-token");
    assertEquals(tokenResponse.getRefreshToken(), "refresh-token-123");
    assertEquals(tokenResponse.getExpiresInSeconds(), Long.valueOf(3600));
    // reading the token the regular way makes the manager refresh it, which comes back here
    // through the SCM token fetcher and never terminates
    verify(personalAccessTokenManager, never()).get(any(Subject.class), any(), any(), any());
  }

  @Test
  public void shouldNotSetExpiresInSecondsOnRefreshWhenPersistedTokenHasNoExpiry()
      throws Exception {
    // given
    String provider = "github";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(provider)).thenReturn(authenticator);

    OAuthToken refreshedToken =
        newDto(OAuthToken.class).withToken("new-access-token").withRefreshToken("new-refresh");
    when(authenticator.refreshToken("0000-00-0000")).thenReturn(null).thenReturn(refreshedToken);
    when(authenticator.refreshToken("Anonymous")).thenReturn(null);

    AuthorizationCodeFlow flow = mock(AuthorizationCodeFlow.class);
    Field flowField = OAuthAuthenticator.class.getDeclaredField("flow");
    flowField.setAccessible(true);
    flowField.set(authenticator, flow);

    // tokens persisted without `expires_in` are stored with the `0` default
    PersonalAccessToken persistedToken =
        new PersonalAccessToken(
            "https://github.com",
            provider,
            "0000-00-0000",
            null,
            null,
            "oauth2-token",
            "id-token",
            "old-access-token",
            "refresh-token-123",
            0);
    when(personalAccessTokenManager.getStored(any(Subject.class), eq(provider), eq(null), eq(null)))
        .thenReturn(Optional.of(persistedToken));
    ArgumentCaptor<TokenResponse> tokenResponseCaptor =
        ArgumentCaptor.forClass(TokenResponse.class);

    // when
    OAuthToken result = embeddedOAuthAPI.refreshToken(provider);

    // then
    assertEquals(result.getToken(), "new-access-token");
    verify(flow).createAndStoreCredential(tokenResponseCaptor.capture(), eq("0000-00-0000"));
    TokenResponse tokenResponse = tokenResponseCaptor.getValue();
    assertEquals(tokenResponse.getAccessToken(), "old-access-token");
    assertEquals(tokenResponse.getRefreshToken(), "refresh-token-123");
    assertNull(tokenResponse.getExpiresInSeconds());
  }

  @Test(
      expectedExceptions = UnauthorizedException.class,
      expectedExceptionsMessageRegExp = "OAuth token for user 0000-00-0000 was not found")
  public void shouldThrowUnauthorizedOnRefreshWhenPersistedTokenHasNoRefreshToken()
      throws Exception {
    // given
    String provider = "github";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(provider)).thenReturn(authenticator);
    when(authenticator.refreshToken(anyString())).thenReturn(null);

    PersonalAccessToken persistedToken =
        new PersonalAccessToken(
            "https://github.com",
            provider,
            "0000-00-0000",
            null,
            null,
            "oauth2-token",
            "id-token",
            "old-access-token",
            null,
            0);
    when(personalAccessTokenManager.getStored(any(Subject.class), eq(provider), eq(null), eq(null)))
        .thenReturn(Optional.of(persistedToken));

    // when
    embeddedOAuthAPI.refreshToken(provider);
  }

  @Test(
      expectedExceptions = UnauthorizedException.class,
      expectedExceptionsMessageRegExp = "OAuth token for user 0000-00-0000 was not found")
  public void shouldThrowUnauthorizedOnRefreshWhenNoPersistedTokenExists() throws Exception {
    // given
    String provider = "github";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(provider)).thenReturn(authenticator);
    when(authenticator.refreshToken(anyString())).thenReturn(null);
    when(personalAccessTokenManager.getStored(any(Subject.class), eq(provider), eq(null), eq(null)))
        .thenReturn(Optional.empty());

    // when
    embeddedOAuthAPI.refreshToken(provider);
  }

  @Test(
      expectedExceptions = UnauthorizedException.class,
      expectedExceptionsMessageRegExp = "OAuth token for user 0000-00-0000 was not found")
  public void shouldThrowUnauthorizedWhenRefreshStillFailsAfterRestoringCredential()
      throws Exception {
    // given
    String provider = "github";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(provider)).thenReturn(authenticator);
    // the refresh fails both before and after the credential is restored
    when(authenticator.refreshToken(anyString())).thenReturn(null);

    AuthorizationCodeFlow flow = mock(AuthorizationCodeFlow.class);
    Field flowField = OAuthAuthenticator.class.getDeclaredField("flow");
    flowField.setAccessible(true);
    flowField.set(authenticator, flow);

    PersonalAccessToken persistedToken =
        new PersonalAccessToken(
            "https://github.com",
            provider,
            "0000-00-0000",
            null,
            null,
            "oauth2-token",
            "id-token",
            "old-access-token",
            "refresh-token-123",
            3600);
    when(personalAccessTokenManager.getStored(any(Subject.class), eq(provider), eq(null), eq(null)))
        .thenReturn(Optional.of(persistedToken));

    // when
    embeddedOAuthAPI.refreshToken(provider);
  }

  @Test(expectedExceptions = ServerException.class)
  public void shouldWrapScmCommunicationExceptionInServerExceptionOnRefresh() throws Exception {
    // given
    String provider = "github";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(oauth2Providers.getAuthenticator(provider)).thenReturn(authenticator);
    when(authenticator.refreshToken(anyString())).thenReturn(null);
    when(personalAccessTokenManager.getStored(any(Subject.class), eq(provider), eq(null), eq(null)))
        .thenThrow(new ScmCommunicationException("SCM error"));

    // when
    embeddedOAuthAPI.refreshToken(provider);
  }
}
