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

import java.io.IOException;
import org.eclipse.che.api.factory.server.scm.AuthorizingFileContentProvider;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/** Bitbucket specific authorizing file content provider. */
class BitbucketAuthorizingFileContentProvider extends AuthorizingFileContentProvider<BitbucketUrl> {

  BitbucketAuthorizingFileContentProvider(
      BitbucketUrl bitbucketUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(bitbucketUrl, urlFetcher, personalAccessTokenManager);
  }

  /** Formats OAuth token as HTTP Authorization header. */
  @Override
  protected String formatAuthorization(String token) {
    return "Bearer " + token;
  }

  @Override
  protected boolean isPublicRepository(BitbucketUrl remoteFactoryUrl) {
    try {
      urlFetcher.fetch(
          BitbucketApiClient.BITBUCKET_API_SERVER
              + "repos"
              + '/'
              + remoteFactoryUrl.getUsername()
              + '/'
              + remoteFactoryUrl.getRepository());
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
