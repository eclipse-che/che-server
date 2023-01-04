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
package org.eclipse.che.api.factory.server.bitbucket;

import org.eclipse.che.api.factory.server.scm.AuthorizingFileContentProvider;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/**
 * Bitbucket Server specific file content provider. Files are retrieved using bitbucket Server REST
 * API and personal access token based authentication is performed during requests.
 */
public class BitbucketServerAuthorizingFileContentProvider
    extends AuthorizingFileContentProvider<BitbucketServerUrl> {

  public BitbucketServerAuthorizingFileContentProvider(
      BitbucketServerUrl bitbucketServerUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(bitbucketServerUrl, urlFetcher, personalAccessTokenManager);
  }
}
