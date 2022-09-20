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
package org.eclipse.che.api.factory.server.github;

import java.io.IOException;
import org.eclipse.che.api.factory.server.scm.AuthorizingFileContentProvider;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/** Github specific authorizing file content provider. */
class GithubAuthorizingFileContentProvider extends AuthorizingFileContentProvider<GithubUrl> {

  GithubAuthorizingFileContentProvider(
      GithubUrl githubUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(githubUrl, urlFetcher, personalAccessTokenManager);
  }

  /**
   * Formatting OAuth token as HTTP Authorization header.
   *
   * <p>GitHub Authorization HTTP header format is described here:
   * https://docs.github.com/en/rest/overview/resources-in-the-rest-api#oauth2-token-sent-in-a-header
   */
  @Override
  protected String formatAuthorization(String token) {
    return "token " + token;
  }

  @Override
  protected boolean isPublicRepository(GithubUrl remoteFactoryUrl) {
    try {
      urlFetcher.fetch(
          remoteFactoryUrl.getHostName()
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
