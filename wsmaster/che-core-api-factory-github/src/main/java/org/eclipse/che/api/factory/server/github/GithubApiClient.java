/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.github;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
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
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** GitHub API operations helper. */
public class GithubApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(GithubApiClient.class);
  private static final String GITHUB_API_SERVER = "https://api.github.com";
  // Trailing '/' is important since https://github.com is different from https://github.com/ and
  // the latter is what's being used.
  static final String GITHUB_SERVER = "https://github.com/";
  private final HttpClient httpClient;
  private final URI apiServerUrl;
  private final URI scmServerUrl;

  private static final Duration DEFAULT_HTTP_TIMEOUT = ofSeconds(10);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public GithubApiClient() {
    this.apiServerUrl = URI.create(GITHUB_API_SERVER);
    this.scmServerUrl = URI.create(GITHUB_SERVER);
    this.httpClient =
        HttpClient.newBuilder()
            .executor(
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder()
                        .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                        .setNameFormat(GithubApiClient.class.getName() + "-%d")
                        .setDaemon(true)
                        .build()))
            .connectTimeout(DEFAULT_HTTP_TIMEOUT)
            .build();
  }

  /**
   * Returns the user associated with the provided OAuth access token.
   *
   * @see https://docs.github.com/en/rest/reference/users#get-the-authenticated-user
   * @param authenticationToken OAuth access token used by the user.
   * @return Information about the user associated with the token
   * @throws ScmItemNotFoundException
   * @throws ScmCommunicationException
   * @throws ScmBadRequestException
   */
  public GithubUser getUser(String authenticationToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException {
    final URI uri = apiServerUrl.resolve("/user");
    HttpRequest request = buildGithubApiRequest(uri, authenticationToken);
    LOG.trace("executeRequest={}", request);
    return executeRequest(
        httpClient,
        request,
        response -> {
          try {
            return OBJECT_MAPPER.readValue(response.body(), GithubUser.class);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  /**
   * Returns the scopes of the OAuth token.
   *
   * @see https://docs.github.com/en/developers/apps/building-oauth-apps/scopes-for-oauth-apps
   * @param authenticationToken The OAuth token to inspect.
   * @return Array of scopes from the supplied token.
   * @throws ScmItemNotFoundException
   * @throws ScmCommunicationException
   * @throws ScmBadRequestException
   */
  public String[] getTokenScopes(String authenticationToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException {
    final URI uri = apiServerUrl.resolve("/user");
    HttpRequest request = buildGithubApiRequest(uri, authenticationToken);
    LOG.trace("executeRequest={}", request);
    return executeRequest(
        httpClient,
        request,
        response -> {
          Optional<String> scopes = response.headers().firstValue("X-OAuth-Scopes");
          return Splitter.on(',')
              .trimResults()
              .splitToList(scopes.orElse(""))
              .toArray(String[]::new);
        });
  }

  private HttpRequest buildGithubApiRequest(URI uri, String authenticationToken) {
    return HttpRequest.newBuilder(uri)
        .headers(
            "Authorization",
            "token " + authenticationToken,
            "Accept",
            "application/vnd.github.v3+json")
        .timeout(DEFAULT_HTTP_TIMEOUT)
        .build();
  }

  private <T> T executeRequest(
      HttpClient httpClient,
      HttpRequest request,
      Function<HttpResponse<InputStream>, T> responseConverter)
      throws ScmBadRequestException, ScmItemNotFoundException, ScmCommunicationException {
    try {
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      LOG.trace("executeRequest={} response {}", request, response.statusCode());
      if (response.statusCode() == 200) {
        return responseConverter.apply(response);
      } else if (response.statusCode() == 204) {
        return null;
      } else {
        String body = CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
        switch (response.statusCode()) {
          case HTTP_BAD_REQUEST:
            throw new ScmBadRequestException(body);
          case HTTP_NOT_FOUND:
            throw new ScmItemNotFoundException(body);
          default:
            throw new ScmCommunicationException(
                "Unexpected status code " + response.statusCode() + " " + response.toString());
        }
      }
    } catch (IOException | InterruptedException | UncheckedIOException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  public boolean isConnected(String scmServerUrl) {
    return this.scmServerUrl.equals(URI.create(scmServerUrl));
  }
}
