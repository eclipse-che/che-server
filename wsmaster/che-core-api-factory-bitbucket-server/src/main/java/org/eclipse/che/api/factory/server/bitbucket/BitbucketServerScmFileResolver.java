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
package org.eclipse.che.api.factory.server.bitbucket;

import static org.eclipse.che.api.factory.server.DevfileToApiExceptionMapper.toApiException;

import java.io.IOException;
import javax.inject.Inject;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.factory.server.ScmFileResolver;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;

/** Bitbucket specific SCM file resolver. */
public class BitbucketServerScmFileResolver implements ScmFileResolver {

  private final URLFetcher urlFetcher;
  private final BitbucketURLParser bitbucketURLParser;
  private final GitCredentialManager gitCredentialManager;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  @Inject
  public BitbucketServerScmFileResolver(
      BitbucketURLParser bitbucketURLParser,
      URLFetcher urlFetcher,
      GitCredentialManager gitCredentialManager,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.urlFetcher = urlFetcher;
    this.bitbucketURLParser = bitbucketURLParser;
    this.gitCredentialManager = gitCredentialManager;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public boolean accept(String repository) {
    return bitbucketURLParser.isValid(repository);
  }

  @Override
  public String fileContent(String repository, String filePath) throws ApiException {

    BitbucketUrl bitbucketUrl = bitbucketURLParser.parse(repository);

    try {
      return new BitbucketServerAuthorizingFileContentProvider(
              bitbucketUrl, urlFetcher, gitCredentialManager, personalAccessTokenManager)
          .fetchContent(filePath);
    } catch (IOException e) {
      throw new NotFoundException("Unable to retrieve file from given location.");
    } catch (DevfileException devfileException) {
      throw toApiException(devfileException);
    }
  }
}
