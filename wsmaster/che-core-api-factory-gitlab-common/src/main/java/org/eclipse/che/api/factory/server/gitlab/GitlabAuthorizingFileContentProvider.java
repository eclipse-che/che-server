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

import java.io.IOException;
import org.eclipse.che.api.factory.server.scm.AuthorizingFileContentProvider;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/** Gitlab specific authorizing file content provider. */
class GitlabAuthorizingFileContentProvider extends AuthorizingFileContentProvider<GitlabUrl> {

  GitlabAuthorizingFileContentProvider(
      GitlabUrl gitlabUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(gitlabUrl, urlFetcher, personalAccessTokenManager);
  }

  @Override
  protected boolean isPublicRepository(GitlabUrl remoteFactoryUrl) {
    try {
      urlFetcher.fetch(remoteFactoryUrl.getHostName() + '/' + remoteFactoryUrl.getSubGroups());
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
