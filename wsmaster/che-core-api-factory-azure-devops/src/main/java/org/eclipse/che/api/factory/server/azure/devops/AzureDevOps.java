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

import com.google.common.base.Joiner;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utils for Azure DevOps OAuth.
 *
 * @author Anatolii Bazko
 */
public class AzureDevOps {
  /** Name of this OAuth provider as found in OAuthAPI. */
  public static final String PROVIDER_NAME = "azure-devops";
  /** Azure DevOps Service API version calls. */
  public static final String API_VERSION = "7.0";

  public static String getAuthenticateUrlPath(String[] scopes) {
    return "/oauth/authenticate?oauth_provider="
        + PROVIDER_NAME
        + "&scope="
        + Joiner.on(" ").join(scopes);
  }

  /** The authorization request varies depending on the type of token. */
  public static String formatAuthorizationHeader(String token, boolean isPAT) {
    return isPAT
        ? "Basic "
            + Base64.getEncoder().encodeToString((":" + token).getBytes(StandardCharsets.UTF_8))
        : "Bearer " + token;
  }
}
