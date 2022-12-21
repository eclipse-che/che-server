/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
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

import java.io.IOException;
import javax.inject.Inject;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.factory.server.ScmFileResolver;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;

/** Bitbucket Server specific SCM file resolver. */
public class BitbucketServerScmFileResolver implements ScmFileResolver {

  private final URLFetcher urlFetcher;
  private final BitbucketServerURLParser bitbucketURLParser;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  @Inject
  public BitbucketServerScmFileResolver(
      BitbucketServerURLParser bitbucketURLParser,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.urlFetcher = urlFetcher;
    this.bitbucketURLParser = bitbucketURLParser;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public boolean accept(String repository) {
    return bitbucketURLParser.isValid(repository);
  }

  @Override
  public String fileContent(String repository, String filePath) throws ApiException {

    BitbucketServerUrl bitbucketServerUrl = bitbucketURLParser.parse(repository);

    try {
      return fetchContent(bitbucketServerUrl, filePath, false);
    } catch (DevfileException exception) {
      // This catch might mean that the authentication was rejected by user, try to repeat the fetch
      // without authentication flow.
      try {
        return fetchContent(bitbucketServerUrl, filePath, true);
      } catch (DevfileException devfileException) {
        throw toApiException(devfileException);
      }
    }
  }

  private String fetchContent(
      BitbucketServerUrl bitbucketServerUrl, String filePath, boolean skipAuthentication)
      throws DevfileException, NotFoundException {
    try {
      BitbucketServerAuthorizingFileContentProvider contentProvider =
          new BitbucketServerAuthorizingFileContentProvider(
              bitbucketServerUrl, urlFetcher, personalAccessTokenManager);
      return skipAuthentication
          ? contentProvider.fetchContentWithoutAuthentication(filePath)
          : contentProvider.fetchContent(filePath);
    } catch (IOException e) {
      throw new NotFoundException(e.getMessage());
    }
  }
}
