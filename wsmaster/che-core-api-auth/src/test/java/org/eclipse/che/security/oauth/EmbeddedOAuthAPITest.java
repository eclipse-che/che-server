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
import static org.eclipse.che.commons.lang.UrlUtils.getParameter;
import static org.eclipse.che.commons.lang.UrlUtils.getQueryParameters;
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
import static org.testng.Assert.assertNotNull;
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
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.security.oauth.shared.dto.OAuthAuthenticatorDescriptor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
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

  /** The Che host of these tests is `redirecturl.com`, the one a redirect may address. */
  @Spy
  RedirectAfterLoginUrlValidator redirectUrlValidator =
      new RedirectAfterLoginUrlValidator("https://redirecturl.com/api");

  /** Nonces are real here: a callback is only served when its state carries one of them. */
  @Spy OAuthCsrfNonceStore csrfNonceStore = new OAuthCsrfNonceStore();

  @InjectMocks EmbeddedOAuthAPI embeddedOAuthAPI;

  @AfterMethod
  public void resetSubject() {
    EnvironmentContext.reset();
  }

  /** The nonce a callback of the user of the current request has to carry in its state. */
  private String issuedNonce() {
    return csrfNonceStore.issue(EnvironmentContext.getCurrent().getSubject());
  }

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
  public void shouldRedirectToTheUrlOfTheFlowOnAccessDenied() throws Exception {
    // given
    String nonce = issuedNonce();
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state="
                    + encode(
                        "redirect_after_login=https://redirecturl.com?quary=param&csrf_nonce="
                            + nonce,
                        UTF_8)));

    // when
    Response callback = embeddedOAuthAPI.callback(uriInfo, singletonList("access_denied"));

    // then
    assertEquals(
        callback.getLocation().toString(),
        "https://redirecturl.com?quary=param&error_code=access_denied");
  }

  /** A URL carrying characters that `java.net.URI` refuses, such as JSON, has to be encoded. */
  @Test
  public void shouldEncodeRejectErrorForRedirectUrl() throws Exception {
    // given
    String nonce = issuedNonce();
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state="
                    + encode(
                        "redirect_after_login=https://redirecturl.com?params="
                            + encode("{}", UTF_8)
                            + "&csrf_nonce="
                            + nonce,
                        UTF_8)));

    // when
    Response callback = embeddedOAuthAPI.callback(uriInfo, singletonList("access_denied"));

    // then
    assertEquals(
        callback.getLocation().toString(),
        "https://redirecturl.com?params%3D%7B%7D&error_code=access_denied");
  }

  /**
   * The URL to come back to belongs to the flow the callback answers. Holding it in a field of this
   * singleton sent the user to whatever URL the last authorization request, of any user, carried
   * (CWE-488).
   */
  @Test
  public void shouldNotCarryTheRedirectUrlOfOneFlowIntoAnother() throws Exception {
    // given a flow started with one redirect URL
    UriInfo authenticateUriInfo = mock(UriInfo.class);
    when(authenticateUriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che/api/oauth/authenticate?oauth_provider=github"
                    + "&redirect_after_login="
                    + encode("https://redirecturl.com/of-another-user", UTF_8)));
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.getAuthenticateUrl(any(URL.class), anyList()))
        .thenReturn("https://github.com");
    when(oauth2Providers.getAuthenticator("github")).thenReturn(authenticator);
    embeddedOAuthAPI.authenticate(
        authenticateUriInfo,
        "github",
        emptyList(),
        "https://redirecturl.com/of-another-user",
        null);

    // when the callback of a flow started with another one comes in
    String nonce = issuedNonce();
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state="
                    + encode(
                        "redirect_after_login=https://redirecturl.com/own?x=1&csrf_nonce=" + nonce,
                        UTF_8)));
    Response callback = embeddedOAuthAPI.callback(uriInfo, singletonList("access_denied"));

    // then
    assertEquals(
        callback.getLocation().toString(),
        "https://redirecturl.com/own?x=1&error_code=access_denied");
  }

  @Test
  public void shouldAddSslErrorCode() throws Exception {
    // given
    String nonce = issuedNonce();
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.callback(any(URL.class), anyList()))
        .thenThrow(new ScmCommunicationException("", SSL_ERROR_CODE));
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=github&redirect_after_login=https://redirecturl.com?params="
                            + "&csrf_nonce="
                            + nonce,
                        UTF_8)));
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
    String nonce = issuedNonce();
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    TokenResponse tokenResponse = mock(TokenResponse.class);
    when(authenticator.getEndpointUrl()).thenReturn("http://eclipse.che");
    when(tokenResponse.getAccessToken()).thenReturn("token");
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(tokenResponse);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider%3Dgithub%26redirect_after_login%3DredirectUrl"
                    + "%26csrf_nonce%3D"
                    + nonce));
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
    String nonce = issuedNonce();
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(mock(TokenResponse.class));
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=github&redirect_after_login=https://redirecturl.com?params="
                            + encode("{}", UTF_8)
                            + "&csrf_nonce="
                            + nonce,
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
    String nonce = issuedNonce();
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(mock(TokenResponse.class));
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=github&redirect_after_login=https://redirecturl.com?params="
                            + encode(encode("{}", UTF_8), UTF_8)
                            + "&csrf_nonce="
                            + nonce,
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
    String nonce = issuedNonce();
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
                "http://eclipse.che?state=oauth_provider%3Dgithub%26redirect_after_login%3DredirectUrl"
                    + "%26csrf_nonce%3D"
                    + nonce));
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
    String nonce = issuedNonce();
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
                "http://eclipse.che?state=oauth_provider%3Dgithub%26redirect_after_login%3DredirectUrl"
                    + "%26csrf_nonce%3D"
                    + nonce));
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

  /**
   * The `state` parameter is echoed back by the OAuth provider as the authorization URL asked for
   * it, so the redirect URL it carries is chosen by whoever built that URL.
   */
  @Test(
      expectedExceptions = ForbiddenException.class,
      expectedExceptionsMessageRegExp = "The redirect after login URL is missing or not allowed")
  public void shouldNotRedirectToAForeignHostOnCallback() throws Exception {
    // given
    String nonce = issuedNonce();
    UriInfo uriInfo = mock(UriInfo.class);
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.getEndpointUrl()).thenReturn("http://eclipse.che");
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(mock(TokenResponse.class));
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=github&redirect_after_login=https://attacker.com/&csrf_nonce=" + nonce,
                        UTF_8)));
    when(oauth2Providers.getAuthenticator("github")).thenReturn(authenticator);

    // when
    embeddedOAuthAPI.callback(uriInfo, emptyList());
  }

  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldNotRedirectToAForeignHostOnAccessDenied() throws Exception {
    // given
    String nonce = issuedNonce();
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state="
                    + encode(
                        "redirect_after_login=https://attacker.com?quary=param&csrf_nonce=" + nonce,
                        UTF_8)));

    // when
    embeddedOAuthAPI.callback(uriInfo, singletonList("access_denied"));
  }

  /** Refusing the URL up front spares the user an authorization that leads nowhere. */
  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldNotStartTheFlowWithAForeignRedirectUrl() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    when(oauth2Providers.getAuthenticator("github")).thenReturn(mock(OAuthAuthenticator.class));

    // when
    embeddedOAuthAPI.authenticate(uriInfo, "github", emptyList(), "https://attacker.com/", null);
  }

  /**
   * The nonce the flow is started with travels in the `state`, which the provider echoes back to
   * the callback, and is what the callback recognises the flow by.
   */
  @Test
  public void shouldPutACsrfNonceInTheStateOfTheAuthorizationUrl() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(new URI("http://eclipse.che/api/oauth/authenticate?oauth_provider=github"));
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.getAuthenticateUrl(any(URL.class), anyList()))
        .thenReturn("https://github.com/login/oauth/authorize");
    when(oauth2Providers.getAuthenticator("github")).thenReturn(authenticator);
    ArgumentCaptor<URL> requestUrlCaptor = ArgumentCaptor.forClass(URL.class);

    // when
    embeddedOAuthAPI.authenticate(uriInfo, "github", emptyList(), null, null);

    // then the callback made of that state is served
    verify(authenticator).getAuthenticateUrl(requestUrlCaptor.capture(), anyList());
    String nonce =
        getParameter(
            getQueryParameters(requestUrlCaptor.getValue()), OAuthCsrfNonceStore.CSRF_NONCE_PARAM);
    assertNotNull(nonce);
    csrfNonceStore.verify(nonce, EnvironmentContext.getCurrent().getSubject());
  }

  /**
   * Without a nonce, a callback URL carrying the attacker's own authorization code, opened by a
   * logged-in victim, has the victim's Che identity hold the attacker's SCM token (CWE-352).
   */
  @Test(
      expectedExceptions = ForbiddenException.class,
      expectedExceptionsMessageRegExp =
          "The OAuth callback does not answer an authorization request of the current user")
  public void shouldRefuseACallbackWithoutACsrfNonce() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?code=attacker-code&state=oauth_provider"
                    + encode("=github&redirect_after_login=/dashboard", UTF_8)));

    // when
    embeddedOAuthAPI.callback(uriInfo, emptyList());

    // then no token is exchanged, let alone stored
  }

  /** A nonce the attacker had issued to themselves does not make the callback the victim's. */
  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRefuseACallbackWithANonceIssuedToAnotherUser() throws Exception {
    // given
    String attackersNonce =
        csrfNonceStore.issue(new SubjectImpl("mallory", emptyList(), "mallory-id", "token", false));
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?code=attacker-code&state=oauth_provider"
                    + encode(
                        "=github&redirect_after_login=/dashboard&csrf_nonce=" + attackersNonce,
                        UTF_8)));

    // when the victim, who is the user of this request, opens it
    embeddedOAuthAPI.callback(uriInfo, emptyList());
  }

  /** A nonce answers one callback, so a callback URL cannot be handed out to be opened again. */
  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRefuseAReplayedCallback() throws Exception {
    // given
    String nonce = issuedNonce();
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(authenticator.getEndpointUrl()).thenReturn("http://eclipse.che");
    when(authenticator.callback(any(URL.class), anyList())).thenReturn(mock(TokenResponse.class));
    when(oauth2Providers.getAuthenticator("github")).thenReturn(authenticator);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            new URI(
                "http://eclipse.che?state=oauth_provider"
                    + encode(
                        "=github&redirect_after_login=/dashboard&csrf_nonce=" + nonce, UTF_8)));

    // when
    embeddedOAuthAPI.callback(uriInfo, emptyList());
    embeddedOAuthAPI.callback(uriInfo, emptyList());
  }
}
