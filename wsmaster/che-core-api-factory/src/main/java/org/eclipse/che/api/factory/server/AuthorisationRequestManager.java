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
package org.eclipse.che.api.factory.server;

import jakarta.ws.rs.core.UriInfo;
import java.util.List;

/**
 * Manager for storing and retrieving rejected authorisation requests. This is used to prevent from
 * asking the user to grant access to the SCM provider after the user has already rejected the
 * request.
 */
public interface AuthorisationRequestManager {
  /**
   * Store the reject flag for the given SCM provider name.
   *
   * @param scmProviderName the SCM provider name
   */
  void store(String scmProviderName);

  /**
   * Remove the reject flag for the given SCM provider name.
   *
   * @param scmProviderName the SCM provider name
   */
  void remove(String scmProviderName);

  /**
   * Check if the reject flag is stored for the given SCM provider name.
   *
   * @param scmProviderName the SCM provider name to check
   * @return {@code true} if the reject flag is stored, {@code false} otherwise
   */
  boolean isStored(String scmProviderName);

  /** This method must be called on the Oauth callback from the SCM provider. */
  void callback(UriInfo uriInfo, List<String> errorValues);
}
