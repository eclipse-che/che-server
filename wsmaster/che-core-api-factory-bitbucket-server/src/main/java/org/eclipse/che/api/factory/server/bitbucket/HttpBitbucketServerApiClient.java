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
package org.eclipse.che.api.factory.server.bitbucket;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.time.Duration.ofSeconds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketPersonalAccessToken;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketServerApiClient;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketUser;
import org.eclipse.che.api.factory.server.bitbucket.server.Page;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.eclipse.che.security.oauth1.NoopOAuthAuthenticator;
import org.eclipse.che.security.oauth1.OAuthAuthenticationException;
import org.eclipse.che.security.oauth1.OAuthAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of @{@link BitbucketServerApiClient} that is using @{@link HttpClient} to
 * communicate with Bitbucket Server.
 */
public class HttpBitbucketServerApiClient implements BitbucketServerApiClient {

  private static final ObjectMapper OM = new ObjectMapper();

  private static final Logger LOG = LoggerFactory.getLogger(HttpBitbucketServerApiClient.class);
  private static final Duration DEFAULT_HTTP_TIMEOUT = ofSeconds(10);
  private final URI serverUri;
  private final OAuthAuthenticator authenticator;
  private final OAuthAPI oAuthAPI;
  private final String apiEndpoint;
  private final HttpClient httpClient;

  public HttpBitbucketServerApiClient(
      String serverUrl, OAuthAuthenticator authenticator, OAuthAPI oAuthAPI, String apiEndpoint) {
    this.serverUri = URI.create(serverUrl.endsWith("/") ? serverUrl : serverUrl + "/");
    this.authenticator = authenticator;
    this.oAuthAPI = oAuthAPI;
    this.apiEndpoint = apiEndpoint;
    this.httpClient =
        HttpClient.newBuilder()
            .executor(
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder()
                        .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                        .setNameFormat(HttpBitbucketServerApiClient.class.getName() + "-%d")
                        .setDaemon(true)
                        .build()))
            .connectTimeout(DEFAULT_HTTP_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  @Override
  public boolean isConnected(String bitbucketServerUrl) {
    return serverUri.equals(
        URI.create(
            bitbucketServerUrl.endsWith("/") ? bitbucketServerUrl : bitbucketServerUrl + "/"));
  }

  @Override
  public BitbucketUser getUser(String token)
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException {
    return getUser(Optional.of(token));
  }

  @Override
  public BitbucketUser getUser()
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException {
    return getUser(Optional.empty());
  }

  @Override
  public List<BitbucketUser> getUsers()
      throws ScmBadRequestException, ScmUnauthorizedException, ScmCommunicationException {
    try {
      return doGetItems(Optional.empty(), BitbucketUser.class, "./rest/api/1.0/users", null);
    } catch (ScmItemNotFoundException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public List<BitbucketUser> getUsers(String filter)
      throws ScmBadRequestException, ScmUnauthorizedException, ScmCommunicationException {
    try {
      return doGetItems(Optional.empty(), BitbucketUser.class, "./rest/api/1.0/users", filter);
    } catch (ScmItemNotFoundException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public void deletePersonalAccessTokens(String tokenId)
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException {
    URI uri =
        serverUri.resolve(
            "./rest/access-tokens/1.0/users/" + getUserSlug(Optional.empty()) + "/" + tokenId);
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .DELETE()
            .headers(
                HttpHeaders.AUTHORIZATION,
                computeAuthorizationHeader("DELETE", uri.toString()),
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON,
                HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON)
            .timeout(DEFAULT_HTTP_TIMEOUT)
            .build();

    try {
      LOG.trace("executeRequest={}", request);
      executeRequest(
          httpClient,
          request,
          inputStream -> {
            try {
              String result =
                  CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
              return OM.readValue(result, String.class);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (ScmBadRequestException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public BitbucketPersonalAccessToken createPersonalAccessTokens(
      String tokenName, Set<String> permissions)
      throws ScmBadRequestException, ScmUnauthorizedException, ScmCommunicationException,
          ScmItemNotFoundException {
    BitbucketPersonalAccessToken token =
        new BitbucketPersonalAccessToken(tokenName, permissions, 90);
    URI uri = serverUri.resolve("./rest/access-tokens/1.0/users/" + getUserSlug(Optional.empty()));

    try {
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .PUT(
                  HttpRequest.BodyPublishers.ofString(
                      OM.writeValueAsString(
                          // set maximum allowed expiryDays to 90
                          token)))
              .headers(
                  HttpHeaders.AUTHORIZATION,
                  computeAuthorizationHeader("PUT", uri.toString()),
                  HttpHeaders.ACCEPT,
                  MediaType.APPLICATION_JSON,
                  HttpHeaders.CONTENT_TYPE,
                  MediaType.APPLICATION_JSON)
              .timeout(DEFAULT_HTTP_TIMEOUT)
              .build();
      LOG.trace("executeRequest={}", request);
      return executeRequest(
          httpClient,
          request,
          inputStream -> {
            try {
              String result =
                  CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
              return OM.readValue(result, BitbucketPersonalAccessToken.class);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (ScmItemNotFoundException | JsonProcessingException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public List<BitbucketPersonalAccessToken> getPersonalAccessTokens()
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException {
    try {
      return doGetItems(
          Optional.empty(),
          BitbucketPersonalAccessToken.class,
          "./rest/access-tokens/1.0/users/" + getUserSlug(Optional.empty()),
          null);
    } catch (ScmBadRequestException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public BitbucketPersonalAccessToken getPersonalAccessToken(String tokenId, String oauthToken)
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException {
    URI uri =
        serverUri.resolve(
            "./rest/access-tokens/1.0/users/"
                + getUserSlug(Optional.of(oauthToken))
                + "/"
                + tokenId);
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .headers(
                "Authorization",
                "Bearer " + oauthToken,
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON)
            .timeout(DEFAULT_HTTP_TIMEOUT)
            .build();

    try {
      LOG.trace("executeRequest={}", request);
      return executeRequest(
          httpClient,
          request,
          inputStream -> {
            try {
              String result =
                  CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
              return OM.readValue(result, BitbucketPersonalAccessToken.class);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (ScmBadRequestException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private String getUserSlug(Optional<String> token)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmUnauthorizedException {
    return getUser(token).getSlug();
  }

  private BitbucketUser getUser(Optional<String> token)
      throws ScmCommunicationException, ScmUnauthorizedException, ScmItemNotFoundException {
    URI uri;
    try {
      uri = serverUri.resolve("./plugins/servlet/applinks/whoami");
    } catch (IllegalArgumentException e) {
      // if the slug contains invalid characters (space for example) then the URI will be invalid
      throw new ScmCommunicationException(e.getMessage(), e);
    }

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .headers(
                "Authorization",
                token.isPresent()
                    ? "Bearer " + token.get()
                    : computeAuthorizationHeader("GET", uri.toString()))
            .timeout(DEFAULT_HTTP_TIMEOUT)
            .build();

    String username;
    try {
      LOG.trace("executeRequest={}", request);
      username =
          executeRequest(
              httpClient,
              request,
              inputStream -> {
                try {
                  return CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    } catch (ScmBadRequestException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }

    // Only authenticated users can do the request below, so we must ensure that the username is not
    // empty
    if (isNullOrEmpty(username)) {
      throw buildScmUnauthorizedException();
    }

    try {
      List<BitbucketUser> users =
          doGetItems(token, BitbucketUser.class, "./rest/api/1.0/users", username);
      // The result can contain multiple users, but we only want the one with the correct username
      // See https://docs.atlassian.com/bitbucket-server/rest/7.9.0/bitbucket-rest.html#idp434
      return users.stream()
          .filter(user -> user.getName().equals(username))
          .findFirst()
          .orElseThrow(
              () -> new ScmItemNotFoundException("User '" + username + "' not found in Bitbucket"));
    } catch (ScmBadRequestException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private <T> List<T> doGetItems(Optional<String> token, Class<T> tClass, String api, String filter)
      throws ScmUnauthorizedException, ScmCommunicationException, ScmBadRequestException,
          ScmItemNotFoundException {
    Page<T> currentPage = doGetPage(token, tClass, api, 0, 25, filter);
    List<T> result = new ArrayList<>(currentPage.getValues());
    while (!currentPage.isLastPage()) {
      currentPage = doGetPage(token, tClass, api, currentPage.getNextPageStart(), 25, filter);
      result.addAll(currentPage.getValues());
    }
    return result;
  }

  private <T> Page<T> doGetPage(
      Optional<String> token, Class<T> tClass, String api, int start, int limit, String filter)
      throws ScmUnauthorizedException, ScmBadRequestException, ScmCommunicationException,
          ScmItemNotFoundException {
    String suffix = api + "?start=" + start + "&limit=" + limit;
    if (!isNullOrEmpty(filter)) {
      suffix += "&filter=" + filter;
    }

    URI uri = serverUri.resolve(suffix);
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .headers(
                "Authorization",
                token.isPresent()
                    ? "Bearer " + token.get()
                    : computeAuthorizationHeader("GET", uri.toString()))
            .timeout(DEFAULT_HTTP_TIMEOUT)
            .build();
    LOG.trace("executeRequest={}", request);
    final JavaType typeReference =
        TypeFactory.defaultInstance().constructParametricType(Page.class, tClass);
    return executeRequest(
        httpClient,
        request,
        inputStream -> {
          try {
            String result =
                CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
            return OM.readValue(result, typeReference);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  private <T> T executeRequest(
      HttpClient httpClient, HttpRequest request, Function<InputStream, T> bodyConverter)
      throws ScmBadRequestException, ScmItemNotFoundException, ScmCommunicationException,
          ScmUnauthorizedException {
    try {
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      LOG.trace("executeRequest={} response {}", request, response.statusCode());
      if (response.statusCode() == 200) {
        return bodyConverter.apply(response.body());
      } else if (response.statusCode() == 204) {
        return null;
      } else {
        String body = CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
        switch (response.statusCode()) {
          case HTTP_UNAUTHORIZED:
            throw buildScmUnauthorizedException();
          case HTTP_BAD_REQUEST:
            throw new ScmBadRequestException(body);
          case HTTP_NOT_FOUND:
            throw new ScmItemNotFoundException(body);
          default:
            throw new ScmCommunicationException(
                "Unexpected status code " + response.statusCode() + " " + response,
                response.statusCode(),
                "bitbucket");
        }
      }
    } catch (IOException | InterruptedException | UncheckedIOException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private @Nullable String getToken() throws ScmUnauthorizedException {
    try {
      OAuthToken token = oAuthAPI.getOrRefreshToken("bitbucket-server");
      return token.getToken();
    } catch (NotFoundException
        | ServerException
        | ForbiddenException
        | BadRequestException
        | ConflictException e) {
      LOG.error(e.getMessage());
      return null;
    } catch (UnauthorizedException e) {
      throw buildScmUnauthorizedException();
    }
  }

  private String computeAuthorizationHeader(String requestMethod, String requestUrl)
      throws ScmUnauthorizedException, ScmCommunicationException {
    if (authenticator instanceof NoopOAuthAuthenticator) {
      String token = getToken();
      if (!isNullOrEmpty(token)) {
        return "Bearer " + token;
      }
    }
    try {
      Subject subject = EnvironmentContext.getCurrent().getSubject();
      return authenticator.computeAuthorizationHeader(
          subject.getUserId(), requestMethod, requestUrl);
    } catch (OAuthAuthenticationException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private ScmUnauthorizedException buildScmUnauthorizedException() {
    return new ScmUnauthorizedException(
        EnvironmentContext.getCurrent().getSubject().getUserName()
            + " is not authorized in bitbucket OAuth provider",
        "bitbucket",
        authenticator instanceof NoopOAuthAuthenticator ? "2.0" : "1.0",
        authenticator instanceof NoopOAuthAuthenticator
            ? apiEndpoint + "/oauth/authenticate?oauth_provider=bitbucket-server&scope=ADMIN_WRITE"
            : authenticator.getLocalAuthenticateUrl());
  }
}
