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
package org.eclipse.che.api.factory.server.azure.devops;

import static org.eclipse.che.api.factory.server.azure.devops.AzureDevOps.formatAuthorizationHeader;

import java.io.IOException;
import org.eclipse.che.api.factory.server.scm.AuthorizingFileContentProvider;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/**
 * AzureDevops specific authorizing file content provider.
 *
 * @author Anatolii Bazko
 */
class AzureDevOpsAuthorizingFileContentProvider
    extends AuthorizingFileContentProvider<AzureDevOpsUrl> {

  AzureDevOpsAuthorizingFileContentProvider(
      AzureDevOpsUrl azureDevOpsUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(azureDevOpsUrl, urlFetcher, personalAccessTokenManager);
  }

  @Override
  protected boolean isPublicRepository(AzureDevOpsUrl remoteFactoryUrl) {
    try {
      urlFetcher.fetch(remoteFactoryUrl.getRepositoryLocation());
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  protected String formatAuthorization(String token, boolean isPAT) {
    return formatAuthorizationHeader(token, isPAT);
  }
}
