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
package org.eclipse.che.api.factory.server.github;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.time.Duration.ofSeconds;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

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
import java.net.URISyntaxException;
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
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** GitHub API operations helper. */
public class GithubApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(GithubApiClient.class);

  /** GitHub endpoint URL. */
  public static final String GITHUB_SAAS_ENDPOINT = "https://github.com";

  public static final String GITHUB_SAAS_ENDPOINT_API = "https://api.github.com";

  public static final String GITHUB_SAAS_ENDPOINT_RAW = "https://raw.githubusercontent.com";

  /** GitHub HTTP header containing OAuth scopes. */
  public static final String GITHUB_OAUTH_SCOPES_HEADER = "X-OAuth-Scopes";

  private final HttpClient httpClient;
  private final URI apiServerUrl;
  private final URI scmServerUrl;

  private static final Duration DEFAULT_HTTP_TIMEOUT = ofSeconds(10);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** Default constructor, binds http client to GitHub API url */
  public GithubApiClient(@Nullable String serverUrl) {
    String trimmedServerUrl = !isNullOrEmpty(serverUrl) ? trimEnd(serverUrl, '/') : null;
    this.apiServerUrl =
        URI.create(
            isNullOrEmpty(trimmedServerUrl) || trimmedServerUrl.equals(GITHUB_SAAS_ENDPOINT)
                ? GITHUB_SAAS_ENDPOINT_API + "/"
                : trimmedServerUrl + "/api/v3/");
    this.scmServerUrl =
        URI.create(isNullOrEmpty(trimmedServerUrl) ? GITHUB_SAAS_ENDPOINT : trimmedServerUrl);
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
            .version(HttpClient.Version.HTTP_1_1)
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
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    final URI uri = apiServerUrl.resolve("./user");
    HttpRequest request = buildGithubApiRequest(uri, authenticationToken);
    LOG.trace("executeRequest={}", request);
    return executeRequest(
        httpClient,
        request,
        response -> {
          try {
            String result =
                CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
            return OBJECT_MAPPER.readValue(result, GithubUser.class);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  /**
   * Fetches and returns a pull request.
   *
   * @param id pull request ID
   * @param username user name
   * @param repoName repository name
   * @param authenticationToken oauth access token, can be NULL as the GitHub can handle some
   *     requests without authentication
   * @return pull request
   */
  public GithubPullRequest getPullRequest(
      String id, String username, String repoName, String authenticationToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    final URI uri =
        apiServerUrl.resolve(String.format("./repos/%s/%s/pulls/%s", username, repoName, id));
    HttpRequest request = buildGithubApiRequest(uri, authenticationToken);
    LOG.trace("executeRequest={}", request);
    return executeRequest(
        httpClient,
        request,
        response -> {
          try {
            String result =
                CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
            return OBJECT_MAPPER.readValue(result, GithubPullRequest.class);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  /**
   * Returns the latest commit of the branch.
   *
   * <p>GitHub REST API documentation: https://docs.github.com/en/rest/commits/commits
   *
   * @param user user or organization name
   * @param repository repository name
   * @param branch required branch
   * @param authenticationToken OAuth access token, can be NULL as the GitHub can handle some
   *     requests without authentication
   * @return the latest commit of the branch
   */
  public GithubCommit getLatestCommit(
      String user, String repository, String branch, @Nullable String authenticationToken)
      throws ScmBadRequestException, ScmItemNotFoundException, ScmCommunicationException,
          URISyntaxException, ScmUnauthorizedException {

    final URI uri = apiServerUrl.resolve(String.format("./repos/%s/%s/commits", user, repository));

    final URI requestURI =
        new URI(
            uri.getScheme(),
            uri.getAuthority(),
            uri.getPath(),
            String.format("sha=%s&page=1&per_page=1", branch),
            null);
    HttpRequest request = buildGithubApiRequest(requestURI, authenticationToken);
    LOG.trace("executeRequest={}", request);

    return executeRequest(
        httpClient,
        request,
        response -> {
          try {
            String result =
                CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
            return OBJECT_MAPPER.readValue(result, GithubCommit[].class)[0];
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  /**
   * Returns the scopes of the OAuth token.
   *
   * <p>See GitHub documentation at
   * https://docs.github.com/en/developers/apps/building-oauth-apps/scopes-for-oauth-apps
   *
   * @param authenticationToken The OAuth token to inspect.
   * @return Array of scopes from the supplied token, empty array if no scope.
   * @throws ScmItemNotFoundException
   * @throws ScmCommunicationException
   * @throws ScmBadRequestException
   */
  public Pair<String, String[]> getTokenScopes(String authenticationToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    final URI uri = apiServerUrl.resolve("./user");
    HttpRequest request = buildGithubApiRequest(uri, authenticationToken);
    LOG.trace("executeRequest={}", request);
    return executeRequest(
        httpClient,
        request,
        response -> {
          Optional<String> responseScopes =
              response.headers().firstValue(GITHUB_OAUTH_SCOPES_HEADER);
          String[] scopes =
              Splitter.on(',')
                  .trimResults()
                  .omitEmptyStrings()
                  .splitToList(responseScopes.orElse(""))
                  .toArray(String[]::new);
          try {
            String result =
                CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
            GithubUser user = OBJECT_MAPPER.readValue(result, GithubUser.class);
            return Pair.of(user.getLogin(), scopes);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  /** Returns the GitHub endpoint URL. */
  public String getServerUrl() {
    return this.scmServerUrl.toString();
  }

  /**
   * Builds and returns HttpRequest to acces the GitHub API.
   *
   * @param uri request uri
   * @param authenticationToken authentication token, can be NULL as the GitHub can handle some
   *     requests without authentication
   * @return HttpRequest object
   */
  private HttpRequest buildGithubApiRequest(URI uri, @Nullable String authenticationToken) {
    if (isNullOrEmpty(authenticationToken)) {
      return HttpRequest.newBuilder(uri)
          .headers("Accept", "application/vnd.github.v3+json")
          .timeout(DEFAULT_HTTP_TIMEOUT)
          .build();
    } else {
      return HttpRequest.newBuilder(uri)
          .headers(
              "Authorization",
              "token " + authenticationToken,
              "Accept",
              "application/vnd.github.v3+json")
          .timeout(DEFAULT_HTTP_TIMEOUT)
          .build();
    }
  }

  private <T> T executeRequest(
      HttpClient httpClient,
      HttpRequest request,
      Function<HttpResponse<InputStream>, T> responseConverter)
      throws ScmBadRequestException, ScmItemNotFoundException, ScmCommunicationException,
          ScmUnauthorizedException {
    String provider = GITHUB_SAAS_ENDPOINT.equals(getServerUrl()) ? "github" : "github-server";
    try {
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      int statusCode = response.statusCode();
      LOG.trace("executeRequest={} response {}", request, statusCode);
      if (statusCode == HTTP_OK) {
        return responseConverter.apply(response);
      } else if (statusCode == HTTP_NO_CONTENT) {
        return null;
      } else {
        String body =
            response.body() == null
                ? "Unrecognised error"
                : CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
        switch (statusCode) {
          case HTTP_BAD_REQUEST:
            throw new ScmBadRequestException(body);
          case HTTP_NOT_FOUND:
            throw new ScmItemNotFoundException(body);
          case HTTP_UNAUTHORIZED:
            throw new ScmUnauthorizedException(body, "github", "v2", "");
          default:
            throw new ScmCommunicationException(
                "Unexpected status code " + statusCode + " " + body, statusCode, provider);
        }
      }
    } catch (IOException | InterruptedException | UncheckedIOException e) {
      throw new ScmCommunicationException(e.getMessage(), e, provider);
    }
  }

  /**
   * Checks if the provided url belongs to this client (GitHub)
   *
   * @param scmServerUrl the SCM url to verify
   * @return If the provided url is recognized by the current client
   */
  public boolean isConnected(String scmServerUrl) {
    return this.scmServerUrl.equals(URI.create(trimEnd(scmServerUrl, '/')));
  }
}
