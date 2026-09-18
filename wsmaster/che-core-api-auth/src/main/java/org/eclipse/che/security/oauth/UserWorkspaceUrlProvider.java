/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.security.oauth;

import java.util.Set;
import org.eclipse.che.api.core.ServerException;

/**
 * Supplies the main URLs of the workspaces that belong to the user of the current request.
 *
 * <p>This is the authorization boundary of the OAuth IDE redirect proxy: {@link
 * OAuthIdeRedirectManager} only forwards an authorization code to a URL that is located under one
 * of the returned URLs. The implementation is infrastructure specific and resolves the user from
 * {@link org.eclipse.che.commons.env.EnvironmentContext}.
 */
public interface UserWorkspaceUrlProvider {

  /**
   * Returns the main URLs of the workspaces owned by the user of the current request. Never {@code
   * null}; an empty set means that no workspace URL may be used as a redirect target.
   *
   * @throws ServerException if the workspaces of the current user cannot be resolved
   */
  Set<String> getWorkspaceUrls() throws ServerException;
}
