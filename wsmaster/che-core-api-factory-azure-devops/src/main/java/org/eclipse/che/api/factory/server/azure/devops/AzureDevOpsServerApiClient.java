/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.azure.devops;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
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
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Azure DevOps Server API operations helper. */
public class AzureDevOpsServerApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(AzureDevOpsServerApiClient.class);

  private final HttpClient httpClient;
  private final String azureDevOpsServerApiEndpoint;
  private final String azureDevOpsServerCollection;
  private static final Duration DEFAULT_HTTP_TIMEOUT = ofSeconds(10);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public AzureDevOpsServerApiClient(
      String azureDevOpsServerApiEndpoint, String azureDevOpsServerCollection) {
    this.azureDevOpsServerApiEndpoint = trimEnd(azureDevOpsServerApiEndpoint, '/');
    this.azureDevOpsServerCollection = azureDevOpsServerCollection;
    this.httpClient =
        HttpClient.newBuilder()
            .executor(
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder()
                        .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                        .setNameFormat(AzureDevOpsServerApiClient.class.getName() + "-%d")
                        .setDaemon(true)
                        .build()))
            .connectTimeout(DEFAULT_HTTP_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  /**
   * Returns the user associated with the provided PAT.
   *
   * @param token personal access token.
   */
  public AzureDevOpsServerUserProfile getUser(String token)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException {
    final String url =
        String.format(
            "%s/%s/_api/_common/GetUserProfile",
            azureDevOpsServerApiEndpoint, azureDevOpsServerCollection);
    return getUser(url, encodeAuthorizationHeader(token));
  }

  private static String encodeAuthorizationHeader(String token) {
    return "Basic " + Base64.getEncoder().encodeToString((":" + token).getBytes(UTF_8));
  }

  private AzureDevOpsServerUserProfile getUser(String url, String authorizationHeader)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException {
    final HttpRequest userDataRequest =
        HttpRequest.newBuilder(URI.create(url))
            .headers("Authorization", authorizationHeader)
            .timeout(DEFAULT_HTTP_TIMEOUT)
            .build();

    LOG.trace("executeRequest={}", userDataRequest);
    return executeRequest(
        httpClient,
        userDataRequest,
        response -> {
          try {
            String result =
                CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
            return OBJECT_MAPPER.readValue(result, AzureDevOpsServerUserProfile.class);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
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
          default:
            throw new ScmCommunicationException(
                "Unexpected status code " + response.statusCode() + " " + response,
                response.statusCode(),
                "azure-devops");
        }
      }
    } catch (IOException | InterruptedException | UncheckedIOException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }
}
