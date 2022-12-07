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

import java.io.FileNotFoundException;
import java.io.IOException;
import org.eclipse.che.api.factory.server.scm.AuthorizingFileContentProvider;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;

/** Bitbucket specific authorizing file content provider. */
class BitbucketAuthorizingFileContentProvider extends AuthorizingFileContentProvider<BitbucketUrl> {

  private final BitbucketApiClient apiClient;

  BitbucketAuthorizingFileContentProvider(
      BitbucketUrl bitbucketUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager,
      BitbucketApiClient apiClient) {
    super(bitbucketUrl, urlFetcher, personalAccessTokenManager);
    this.apiClient = apiClient;
  }

  /** Formats OAuth token as HTTP Authorization header. */
  @Override
  protected String formatAuthorization(String token) {
    return "Bearer " + token;
  }

  @Override
  public String fetchContent(String fileURL) throws IOException, DevfileException {
    final String requestURL = formatUrl(fileURL);
    try {
      // try to authenticate for the given URL
      PersonalAccessToken token =
          personalAccessTokenManager.getAndStore(remoteFactoryUrl.getHostName());
      String[] split = requestURL.split("/");
      return apiClient.getFileContent(
          split[3],
          split[4],
          split[6],
          fileURL.substring(fileURL.indexOf(split[6]) + split[6].length() + 1),
          token.getToken());
    } catch (UnknownScmProviderException e) {
      return fetchContent(requestURL, e);
    } catch (ScmCommunicationException e) {
      return fetchContent(fileURL, e);
    } catch (ScmUnauthorizedException
        | ScmConfigurationPersistenceException
        | UnsatisfiedScmPreconditionException
        | ScmBadRequestException e) {
      throw new DevfileException(e.getMessage(), e);
    } catch (ScmItemNotFoundException e) {
      throw new FileNotFoundException(e.getMessage());
    }
  }

  @Override
  protected boolean isPublicRepository(BitbucketUrl remoteFactoryUrl) {
    try {
      urlFetcher.fetch(
          remoteFactoryUrl.getHostName()
              + '/'
              + remoteFactoryUrl.getWorkspaceId()
              + '/'
              + remoteFactoryUrl.getRepository());
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
