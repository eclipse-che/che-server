/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
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

import static org.eclipse.che.api.factory.server.DevfileToApiExceptionMapper.toApiException;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import javax.inject.Inject;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.factory.server.ScmFileResolver;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;

/** Github specific SCM file resolver. */
public class GithubScmFileResolver implements ScmFileResolver {

  private final GithubURLParser githubUrlParser;
  private final URLFetcher urlFetcher;
  private final GitCredentialManager gitCredentialManager;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  @Inject
  public GithubScmFileResolver(
      GithubURLParser githubUrlParser,
      URLFetcher urlFetcher,
      GitCredentialManager gitCredentialManager,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.githubUrlParser = githubUrlParser;
    this.urlFetcher = urlFetcher;
    this.gitCredentialManager = gitCredentialManager;
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
      return new GithubAuthorizingFileContentProvider(
              githubUrl, urlFetcher, gitCredentialManager, personalAccessTokenManager)
          .fetchContent(githubUrl.rawFileLocation(filePath));
    } catch (IOException e) {
      throw new NotFoundException(e.getMessage());
    } catch (DevfileException devfileException) {
      throw toApiException(devfileException);
    }
  }
}
