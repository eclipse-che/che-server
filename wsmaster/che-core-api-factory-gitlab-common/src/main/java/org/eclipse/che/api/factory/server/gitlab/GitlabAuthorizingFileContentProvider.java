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
package org.eclipse.che.api.factory.server.gitlab;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.time.Duration.ofSeconds;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.eclipse.che.api.factory.server.scm.AuthorizingFileContentProvider;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.commons.lang.UrlTargetValidator;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Gitlab specific authorizing file content provider. */
class GitlabAuthorizingFileContentProvider extends AuthorizingFileContentProvider<GitlabUrl> {

  private static final Logger LOG =
      LoggerFactory.getLogger(GitlabAuthorizingFileContentProvider.class);

  private final HttpClient httpClient;

  private static final Duration DEFAULT_HTTP_TIMEOUT = ofSeconds(10);

  GitlabAuthorizingFileContentProvider(
      GitlabUrl gitlabUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(gitlabUrl, urlFetcher, personalAccessTokenManager);
    this.httpClient =
        HttpClient.newBuilder()
            .executor(
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder()
                        .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                        .setNameFormat(GitlabAuthorizingFileContentProvider.class.getName() + "-%d")
                        .setDaemon(true)
                        .build()))
            .connectTimeout(DEFAULT_HTTP_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  /**
   * Tells whether the server may send the request that decides if a repository is public. The URL
   * is derived from the one the caller supplied, and the request does not go through {@link
   * URLFetcher}, which is where the check on the destination normally sits, so it is made here.
   */
  @VisibleForTesting
  boolean canProbe(String repositoryUrl) {
    return UrlTargetValidator.isAllowed(repositoryUrl);
  }

  @Override
  protected boolean isPublicRepository(GitlabUrl remoteFactoryUrl) {
    String repositoryUrl =
        remoteFactoryUrl.getProviderUrl() + '/' + remoteFactoryUrl.getSubGroups();
    if (!canProbe(repositoryUrl)) {
      LOG.warn("Not probing {}: it does not point to a publicly routable host.", repositoryUrl);
      return false;
    }
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(repositoryUrl)).timeout(DEFAULT_HTTP_TIMEOUT).build();
    try {
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      return response.statusCode() == HTTP_OK;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }
}
