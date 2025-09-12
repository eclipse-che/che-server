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
package org.eclipse.che.api.factory.server.bitbucket;

import static org.eclipse.che.api.factory.server.ApiExceptionMapper.toApiException;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import javax.inject.Inject;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.factory.server.ScmFileResolver;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;

/** Bitbucket specific SCM file resolver. */
public class BitbucketScmFileResolver implements ScmFileResolver {

  private final BitbucketURLParser bitbucketUrlParser;
  private final URLFetcher urlFetcher;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private final BitbucketApiClient bitbucketApiClient;

  @Inject
  public BitbucketScmFileResolver(
      BitbucketURLParser bitbucketUrlParser,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager,
      BitbucketApiClient bitbucketApiClient) {
    this.bitbucketUrlParser = bitbucketUrlParser;
    this.urlFetcher = urlFetcher;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.bitbucketApiClient = bitbucketApiClient;
  }

  @Override
  public boolean accept(@NotNull String repository) {
    // Check if repository parameter is a bitbucket URL
    return bitbucketUrlParser.isValid(repository);
  }

  @Override
  public String fileContent(@NotNull String repository, @NotNull String filePath)
      throws ApiException {
    final BitbucketUrl bitbucketUrl = bitbucketUrlParser.parse(repository, null);
    try {
      return fetchContent(bitbucketUrl, filePath, false);
    } catch (DevfileException exception) {
      // This catch might mean that the authentication was rejected by user, try to repeat the fetch
      // without authentication flow.
      try {
        return fetchContent(bitbucketUrl, filePath, true);
      } catch (DevfileException devfileException) {
        throw toApiException(devfileException);
      }
    }
  }

  private String fetchContent(
      BitbucketUrl bitbucketUrl, String filePath, boolean skipAuthentication)
      throws DevfileException, NotFoundException {
    try {
      BitbucketAuthorizingFileContentProvider contentProvider =
          new BitbucketAuthorizingFileContentProvider(
              bitbucketUrl, urlFetcher, personalAccessTokenManager, bitbucketApiClient);
      return skipAuthentication
          ? contentProvider.fetchContentWithoutAuthentication(filePath)
          : contentProvider.fetchContent(filePath);
    } catch (IOException e) {
      throw new NotFoundException(e.getMessage());
    }
  }
}
