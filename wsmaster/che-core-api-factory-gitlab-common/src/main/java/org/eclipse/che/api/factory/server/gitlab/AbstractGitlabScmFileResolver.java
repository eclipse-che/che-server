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

import static org.eclipse.che.api.factory.server.ApiExceptionMapper.toApiException;

import java.io.IOException;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.factory.server.ScmFileResolver;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;

/** GitLab specific SCM file resolver. */
public class AbstractGitlabScmFileResolver implements ScmFileResolver {

  private final AbstractGitlabUrlParser gitlabUrlParser;
  private final URLFetcher urlFetcher;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  public AbstractGitlabScmFileResolver(
      AbstractGitlabUrlParser gitlabUrlParser,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.gitlabUrlParser = gitlabUrlParser;
    this.urlFetcher = urlFetcher;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public boolean accept(String repository) {
    return gitlabUrlParser.isValid(repository);
  }

  @Override
  public String fileContent(String repository, String filePath) throws ApiException {
    GitlabUrl gitlabUrl = gitlabUrlParser.parse(repository);

    try {
      return fetchContent(gitlabUrl, filePath, false);
    } catch (DevfileException exception) {
      // This catch might mean that the authentication was rejected by user, try to repeat the fetch
      // without authentication flow.
      try {
        return fetchContent(gitlabUrl, filePath, true);
      } catch (DevfileException devfileException) {
        throw toApiException(devfileException);
      }
    }
  }

  private String fetchContent(GitlabUrl gitlabUrl, String filePath, boolean skipAuthentication)
      throws DevfileException, NotFoundException {
    try {
      GitlabAuthorizingFileContentProvider contentProvider =
          new GitlabAuthorizingFileContentProvider(
              gitlabUrl, urlFetcher, personalAccessTokenManager);
      return skipAuthentication
          ? contentProvider.fetchContentWithoutAuthentication(filePath)
          : contentProvider.fetchContent(filePath);
    } catch (IOException e) {
      throw new NotFoundException(e.getMessage());
    }
  }
}
