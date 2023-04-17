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
package org.eclipse.che.api.factory.server.scm;

import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.*;

/**
 * OAuth 2.0 token fetcher is designed to use a dependency in che-core-api-factory module. It is
 * needed to avoid circular dependency between che-core-api-factory and che-core-api-auth modules.
 *
 * @author Anatolii Bazko
 */
public interface OAuthTokenFetcher {

  /**
   * Fetches OAuth token for the given OAuth provider name.
   *
   * @param oAuthProviderName OAuth provider name (e.g. github, bitbucket)
   * @return OAuth token
   */
  OAuthToken getToken(String oAuthProviderName)
      throws NotFoundException, UnauthorizedException, ServerException, ForbiddenException,
          BadRequestException, ConflictException;
}
