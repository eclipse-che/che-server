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

import static org.eclipse.che.dto.server.DtoFactory.newDto;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.commons.json.JsonParseException;
import org.eclipse.che.security.oauth.shared.OAuthTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Authentication service which allow get access token from OAuth provider site. */
public abstract class OAuthAuthenticator {
  protected static final String AUTHENTICATOR_IS_NOT_CONFIGURED = "Authenticator is not configured";
  protected static final int SSL_ERROR_CODE = 495;

  private static final Logger LOG = LoggerFactory.getLogger(OAuthAuthenticator.class);

  protected AuthorizationCodeFlow flow;
  protected Map<Pattern, String> redirectUrisMap;

  /**
   * @see {@link #configure(String, String, String[], String, String, MemoryDataStoreFactory, List)}
   */
  protected void configure(
      String clientId,
      String clientSecret,
      String[] redirectUris,
      String authUri,
      String tokenUri,
      MemoryDataStoreFactory dataStoreFactory)
      throws IOException {
    configure(
        clientId,
        clientSecret,
        redirectUris,
        authUri,
        tokenUri,
        dataStoreFactory,
        Collections.emptyList());
  }

  /**
   * This method should be invoked by child class for initialization default instance of {@link
   * AuthorizationCodeFlow} that will be used for authorization
   */
  protected void configure(
      String clientId,
      String clientSecret,
      String[] redirectUris,
      String authUri,
      String tokenUri,
      MemoryDataStoreFactory dataStoreFactory,
      List<String> scopes)
      throws IOException {
    final AuthorizationCodeFlow authorizationFlow =
        new AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(),
                new NetHttpTransport(),
                new JacksonFactory(),
                new GenericUrl(tokenUri),
                new ClientParametersAuthentication(clientId, clientSecret),
                clientId,
                authUri)
            .setDataStoreFactory(dataStoreFactory)
            .setScopes(scopes)
            .build();

    LOG.debug(
        "clientId={}, clientSecret={}, redirectUris={} , authUri={}, tokenUri={}, dataStoreFactory={}",
        clientId,
        clientSecret,
        redirectUris,
        authUri,
        tokenUri,
        dataStoreFactory);

    configure(authorizationFlow, Arrays.asList(redirectUris));
  }

  /**
   * This method should be invoked by child class for setting instance of {@link
   * AuthorizationCodeFlow} that will be used for authorization
   */
  protected void configure(AuthorizationCodeFlow flow, List<String> redirectUris) {
    this.flow = flow;
    this.redirectUrisMap = new HashMap<>(redirectUris.size());
    for (String uri : redirectUris) {
      // Redirect URI may be in form urn:ietf:wg:oauth:2.0:oob os use java.net.URI instead of
      // java.net.URL
      this.redirectUrisMap.put(
          Pattern.compile("([a-z0-9\\-]+\\.)?" + URI.create(uri).getHost()), uri);
    }
  }

  /**
   * Create authentication URL.
   *
   * @param requestUrl URL of current HTTP request. This parameter required to be able determine URL
   *     for redirection after authentication. If URL contains query parameters they will be copy to
   *     'state' parameter and returned to callback method.
   * @param scopes specify exactly what type of access needed
   * @return URL for authentication
   */
  public String getAuthenticateUrl(URL requestUrl, List<String> scopes)
      throws OAuthAuthenticationException {
    if (!isConfigured()) {
      throw new OAuthAuthenticationException(AUTHENTICATOR_IS_NOT_CONFIGURED);
    }

    AuthorizationCodeRequestUrl url =
        flow.newAuthorizationUrl().setRedirectUri(findRedirectUrl(requestUrl)).setScopes(scopes);
    url.setState(prepareState(requestUrl));
    return url.build();
  }

  protected String prepareState(URL requestUrl) {
    StringBuilder state = new StringBuilder();
    String query = requestUrl.getQuery();
    if (query != null) {
      if (state.length() > 0) {
        state.append('&');
      }
      state.append(query);
    }
    return URLEncoder.encode(state.toString(), StandardCharsets.UTF_8);
  }

  protected String findRedirectUrl(URL requestUrl) {
    final String requestHost = requestUrl.getHost();
    for (Map.Entry<Pattern, String> e : redirectUrisMap.entrySet()) {
      if (e.getKey().matcher(requestHost).matches()) {
        return e.getValue();
      }
    }
    return null; // TODO : throw exception instead of return null ???
  }

  /**
   * Process callback request.
   *
   * @param requestUrl request URI. URI should contain authorization code generated by authorization
   *     server
   * @param scopes specify exactly what type of access needed. This list must be exactly the same as
   *     list passed to the method {@link #getAuthenticateUrl(URL, java.util.List)}
   * @return access token
   * @throws OAuthAuthenticationException if authentication failed or <code>requestUrl</code> does
   *     not contain required parameters, e.g. 'code'
   * @throws ScmCommunicationException if communication with SCM failed
   */
  public String callback(URL requestUrl, List<String> scopes)
      throws OAuthAuthenticationException, ScmCommunicationException {
    if (!isConfigured()) {
      throw new OAuthAuthenticationException(AUTHENTICATOR_IS_NOT_CONFIGURED);
    }

    AuthorizationCodeResponseUrl authorizationCodeResponseUrl =
        new AuthorizationCodeResponseUrl(requestUrl.toString());
    final String error = authorizationCodeResponseUrl.getError();
    if (error != null) {
      throw new OAuthAuthenticationException("Authentication failed: " + error);
    }
    final String code = authorizationCodeResponseUrl.getCode();
    if (code == null) {
      throw new OAuthAuthenticationException("Missing authorization code. ");
    }

    try {
      TokenResponse tokenResponse =
          getAuthorizationCodeTokenRequest(requestUrl, scopes, code).execute();
      String userId = getUserFromUrl(authorizationCodeResponseUrl);
      if (userId == null) {
        userId = EnvironmentContext.getCurrent().getSubject().getUserId();
      }
      flow.createAndStoreCredential(tokenResponse, userId);
      return tokenResponse.getAccessToken();
    } catch (IOException ioe) {
      if (ioe instanceof SSLHandshakeException) {
        throw new ScmCommunicationException(
            "SSL handshake failed. Please contact your administrator.", SSL_ERROR_CODE);
      }
      throw new OAuthAuthenticationException(ioe.getMessage());
    }
  }

  /**
   * Creates a new {@link AuthorizationCodeTokenRequest} for the given authorization code. Intended
   * to be overridden by subclasses to customize the request.
   */
  protected AuthorizationCodeTokenRequest getAuthorizationCodeTokenRequest(
      URL requestUrl, List<String> scopes, String code) {
    return flow.newTokenRequest(code)
        .setRequestInitializer(
            request -> {
              if (request.getParser() == null) {
                request.setParser(flow.getJsonFactory().createJsonObjectParser());
              }
              request.getHeaders().setAccept(MediaType.APPLICATION_JSON);
            })
        .setRedirectUri(findRedirectUrl(requestUrl))
        .setScopes(scopes);
  }

  /**
   * Get the name of OAuth provider supported by current implementation.
   *
   * @return oauth provider name
   */
  public abstract String getOAuthProvider();

  private String getUserFromUrl(AuthorizationCodeResponseUrl authorizationCodeResponseUrl)
      throws IOException {
    String state = authorizationCodeResponseUrl.getState();
    if (!(state == null || state.isEmpty())) {
      String decoded = URLDecoder.decode(state, "UTF-8");
      String[] items = decoded.split("&");
      for (String str : items) {
        if (str.startsWith("userId=")) {
          return str.substring(7, str.length());
        }
      }
    }
    return null;
  }

  protected <O> O getJson(String getUserUrl, String accessToken, Class<O> userClass)
      throws OAuthAuthenticationException {
    HttpURLConnection urlConnection = null;
    InputStream urlInputStream = null;

    try {
      urlConnection = (HttpURLConnection) new URL(getUserUrl).openConnection();
      urlConnection.setRequestProperty("Authorization", "token " + accessToken);
      urlInputStream = urlConnection.getInputStream();
      return JsonHelper.fromJson(urlInputStream, userClass, null);
    } catch (JsonParseException | IOException e) {
      throw new OAuthAuthenticationException(e.getMessage(), e);
    } finally {
      if (urlInputStream != null) {
        try {
          urlInputStream.close();
        } catch (IOException ignored) {
        }
      }

      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
  }

  /**
   * Return authorization token by userId.
   *
   * <p>WARN!!!. DO not use it directly.
   *
   * @param userId user identifier
   * @return token value or {@code null}. When user have valid token then it will be returned, when
   *     user have expired token and it can be refreshed then refreshed value will be returned, when
   *     none token found for user then {@code null} will be returned, when user have expired token
   *     and it can't be refreshed then {@code null} will be returned
   * @throws IOException when error occurs during token loading
   * @see OAuthTokenProvider#getToken(String, String) TODO: return Optional<OAuthToken> to avoid
   *     returning null.
   */
  public OAuthToken getOrRefreshToken(String userId) throws IOException {
    if (!isConfigured()) {
      throw new IOException(AUTHENTICATOR_IS_NOT_CONFIGURED);
    }
    Credential credential = flow.loadCredential(userId);
    if (credential == null) {
      return null;
    }
    final Long expirationTime = credential.getExpiresInSeconds();
    if (expirationTime != null && expirationTime < 0) {
      boolean tokenRefreshed;
      try {
        tokenRefreshed = credential.refreshToken();
      } catch (IOException ioEx) {
        tokenRefreshed = false;
      }
      if (tokenRefreshed) {
        credential = flow.loadCredential(userId);
      } else {
        // if token is not refreshed then old value should be invalidated
        // and null result should be returned
        try {
          invalidateTokenByUser(userId);
        } catch (IOException ignored) {
        }
        return null;
      }
    }
    return newDto(OAuthToken.class).withToken(credential.getAccessToken());
  }

  /**
   * Refresh personal access token.
   *
   * @param userId user identifier
   * @return a refreshed token value or {@code null}
   * @throws IOException when error occurs during token loading
   */
  public OAuthToken refreshToken(String userId) throws IOException {
    if (!isConfigured()) {
      throw new IOException(AUTHENTICATOR_IS_NOT_CONFIGURED);
    }

    Credential credential = flow.loadCredential(userId);
    if (credential == null) {
      return null;
    }

    boolean tokenRefreshed;
    try {
      tokenRefreshed = credential.refreshToken();
    } catch (IOException ioEx) {
      tokenRefreshed = false;
    }

    if (tokenRefreshed) {
      credential = flow.loadCredential(userId);
    } else {
      // if token is not refreshed then old value should be invalidated
      // and null result should be returned
      try {
        invalidateTokenByUser(userId);
      } catch (IOException ignored) {
      }
      return null;
    }
    return newDto(OAuthToken.class).withToken(credential.getAccessToken());
  }

  /**
   * Invalidate OAuth token for specified user.
   *
   * @param userId user
   */
  private void invalidateTokenByUser(String userId) throws IOException {
    Credential credential = flow.loadCredential(userId);
    if (credential != null) {
      flow.getCredentialDataStore().delete(userId);
    }
  }

  /**
   * Invalidate OAuth token.
   *
   * @param token oauth token
   * @return <code>true</code> if OAuth token invalidated and <code>false</code> otherwise, e.g. if
   *     token was not found
   */
  public boolean invalidateToken(String token) throws IOException {
    throw new UnsupportedOperationException("Should be implemented by specific provider");
  }

  /**
   * Get endpoint URL.
   *
   * @return provider's endpoint URL
   */
  public abstract String getEndpointUrl();

  /**
   * Checks configuring of authenticator
   *
   * @return true only if authenticator have valid configuration data and it is able to authorize
   *     otherwise returns false
   */
  public boolean isConfigured() {
    return flow != null;
  }
}
