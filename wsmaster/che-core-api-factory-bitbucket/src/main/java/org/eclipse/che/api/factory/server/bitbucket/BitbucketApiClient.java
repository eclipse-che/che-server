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

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.time.Duration.ofSeconds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bitbucket API operations helper. */
public class BitbucketApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(BitbucketApiClient.class);

  /** Bitbucket API endpoint URL. */
  public static final String BITBUCKET_API_SERVER = "https://api.bitbucket.org/2.0/";

  /** Bitbucket endpoint URL. */
  public static final String BITBUCKET_SERVER = "https://bitbucket.org";

  /** Bitbucket HTTP header containing OAuth scopes. */
  public static final String BITBUCKET_OAUTH_SCOPES_HEADER = "X-OAuth-Scopes";

  private final HttpClient httpClient;
  private final URI apiServerUrl;
  private final URI scmServerUrl;

  private static final Duration DEFAULT_HTTP_TIMEOUT = ofSeconds(10);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** Default constructor, binds http client to https://api.bitbucket.org */
  public BitbucketApiClient() {
    this(BITBUCKET_API_SERVER);
  }

  /**
   * Used for URL injection in testing.
   *
   * @param apiServerUrl the Bitbucket API url
   */
  BitbucketApiClient(final String apiServerUrl) {
    this.apiServerUrl = URI.create(apiServerUrl);
    this.scmServerUrl = URI.create(BITBUCKET_SERVER);
    this.httpClient =
        HttpClient.newBuilder()
            .executor(
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder()
                        .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                        .setNameFormat(BitbucketApiClient.class.getName() + "-%d")
                        .setDaemon(true)
                        .build()))
            .connectTimeout(DEFAULT_HTTP_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  /**
   * Returns the user associated with the provided OAuth access token.
   *
   * @param authenticationToken OAuth access token used by the user.
   * @return Information about the user associated with the token
   * @throws ScmItemNotFoundException
   * @throws ScmCommunicationException
   * @throws ScmBadRequestException
   */
  public BitbucketUser getUser(String authenticationToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    final URI uri = apiServerUrl.resolve("user");
    HttpRequest request = buildBitbucketApiRequest(uri, authenticationToken);
    LOG.trace("executeRequest={}", request);
    return executeRequest(
        httpClient,
        request,
        response -> {
          try {
            String result =
                CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
            return OBJECT_MAPPER.readValue(result, BitbucketUser.class);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  public String getFileContent(
      String workspace, String repository, String source, String path, String authenticationToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    final URI uri =
        apiServerUrl.resolve(
            String.format("repositories/%s/%s/src/%s/%s", workspace, repository, source, path));
    HttpRequest request = buildBitbucketApiRequest(uri, authenticationToken);
    LOG.trace("executeRequest={}", request);
    return executeRequest(
        httpClient,
        request,
        response -> {
          try {
            return CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  /**
   * Returns email of the user, associated with the provided OAuth access token.
   *
   * @param authenticationToken OAuth access token used by the user.
   * @return Information about email of the user, associated with the token
   * @throws ScmItemNotFoundException
   * @throws ScmCommunicationException
   * @throws ScmBadRequestException
   */
  public BitbucketUserEmail getEmail(String authenticationToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    final URI uri = apiServerUrl.resolve("user/emails");
    HttpRequest request = buildBitbucketApiRequest(uri, authenticationToken);
    LOG.trace("executeRequest={}", request);
    return executeRequest(
        httpClient,
        request,
        response -> {
          try {
            String result =
                CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
            return OBJECT_MAPPER.readValue(result, BitbucketUserEmail.class);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  /**
   * Returns a pair of the username and array of scopes of the OAuth token.
   *
   * @param authenticationToken The OAuth token to inspect.
   * @return A pair of the username and array of scopes from the supplied token, empty array if no
   *     scopes.
   */
  public Pair<String, String[]> getTokenScopes(String authenticationToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    final URI uri = apiServerUrl.resolve("user");
    HttpRequest request = buildBitbucketApiRequest(uri, authenticationToken);
    LOG.trace("executeRequest={}", request);
    return executeRequest(
        httpClient,
        request,
        response -> {
          try {
            String result =
                CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
            BitbucketUser user = OBJECT_MAPPER.readValue(result, BitbucketUser.class);
            Optional<String> responseScopes =
                response.headers().firstValue(BITBUCKET_OAUTH_SCOPES_HEADER);
            String[] scopes =
                Splitter.on(',')
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(responseScopes.orElse(""))
                    .toArray(String[]::new);
            return Pair.of(user.getName(), scopes);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  private HttpRequest buildBitbucketApiRequest(URI uri, String authenticationToken) {
    return HttpRequest.newBuilder(uri)
        .headers("Authorization", "Bearer " + authenticationToken, "Accept", "application/json")
        .timeout(DEFAULT_HTTP_TIMEOUT)
        .build();
  }

  private <T> T executeRequest(
      HttpClient httpClient,
      HttpRequest request,
      Function<HttpResponse<InputStream>, T> responseConverter)
      throws ScmBadRequestException, ScmItemNotFoundException, ScmCommunicationException,
          ScmUnauthorizedException {
    try {
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      LOG.trace("executeRequest={} response {}", request, response.statusCode());
      if (response.statusCode() == HTTP_OK) {
        return responseConverter.apply(response);
      } else if (response.statusCode() == HTTP_NO_CONTENT) {
        return null;
      } else {
        String body = CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
        switch (response.statusCode()) {
          case HTTP_BAD_REQUEST:
            throw new ScmBadRequestException(body);
          case HTTP_NOT_FOUND:
            throw new ScmItemNotFoundException(body);
          case HTTP_UNAUTHORIZED:
            throw new ScmUnauthorizedException(body, "bitbucket", "v2", "");
          default:
            throw new ScmCommunicationException(
                "Unexpected status code " + response.statusCode() + " " + response,
                response.statusCode(),
                "bitbucket");
        }
      }
    } catch (IOException | InterruptedException | UncheckedIOException e) {
      throw new ScmCommunicationException(e.getMessage(), e, "bitbucket");
    }
  }

  /**
   * Checks if the provided url belongs to this client (Bitbucket)
   *
   * @param scmServerUrl the SCM url to verify
   * @return {@code true} If the provided url is recognized by the current client
   */
  public boolean isConnected(String scmServerUrl) {
    return this.scmServerUrl.equals(URI.create(scmServerUrl));
  }
}
