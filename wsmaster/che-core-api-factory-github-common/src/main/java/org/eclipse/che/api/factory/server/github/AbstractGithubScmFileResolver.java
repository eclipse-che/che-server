/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
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

/** Github specific SCM file resolver. */
public abstract class AbstractGithubScmFileResolver implements ScmFileResolver {

  private final AbstractGithubURLParser githubUrlParser;
  private final URLFetcher urlFetcher;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  public AbstractGithubScmFileResolver(
      AbstractGithubURLParser githubUrlParser,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.githubUrlParser = githubUrlParser;
    this.urlFetcher = urlFetcher;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public boolean accept(@NotNull String repository) {
    // Check if repository parameter is a github URL
    return githubUrlParser.isValid(repository);
  }

  @Override
  public String fileContent(@NotNull String repository, @NotNull String filePath)
      throws ApiException {
    final GithubUrl githubUrl = githubUrlParser.parse(repository);
    try {
      return fetchContent(githubUrl, filePath, false);
    } catch (DevfileException exception) {
      // This catch might mean that the authentication was rejected by user, try to repeat the fetch
      // without authentication flow.
      try {
        return fetchContent(githubUrl, filePath, true);
      } catch (DevfileException devfileException) {
        throw toApiException(devfileException);
      }
    }
  }

  private String fetchContent(GithubUrl githubUrl, String filePath, boolean skipAuthentication)
      throws DevfileException, NotFoundException {
    try {
      GithubAuthorizingFileContentProvider contentProvider =
          new GithubAuthorizingFileContentProvider(
              githubUrl, urlFetcher, personalAccessTokenManager);
      return skipAuthentication
          ? contentProvider.fetchContentWithoutAuthentication(filePath)
          : contentProvider.fetchContent(filePath);
    } catch (IOException e) {
      throw new NotFoundException(e.getMessage());
    }
  }
}
