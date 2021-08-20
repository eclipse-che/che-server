/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.keycloak.server;

import java.util.regex.Pattern;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class KeycloakServletModuleTest {
  private static final String KEYCLOAK_FILTER_PATHS =
      "^"
          // not equals to /keycloak/OIDCKeycloak.js
          + "(?!/keycloak/(OIDC|oidc)[^\\/]+$)"
          // not contains /docs/ (for swagger)
          + "(?!.*(/openapi\\.json))"
          // not ends with '/oauth/callback/' or 'c' or '/keycloak/settings/' or
          // '/system/state'
          + "(?!.*(/keycloak/settings/?|/oauth/callback/?|/oauth/1.0/callback/?|/system/state/?)$)"
          // all other
          + ".*";
  private static final Pattern KEYCLOAK_FILTER_PATHS_PATTERN =
      Pattern.compile(KEYCLOAK_FILTER_PATHS);

  @Test(dataProvider = "allowedRequests")
  public void shouldSkipOpenApi(String url) {
    Assert.assertFalse(KEYCLOAK_FILTER_PATHS_PATTERN.matcher(url).matches());
  }

  @DataProvider(name = "allowedRequests")
  public Object[][] allowedRequests() {
    return new Object[][] {
      {"/keycloak/OIDCKeycloak.js"},
      {"/openapi.json"},
      {"/oauth/callback/"},
      {"/oauth/callback/"},
      {"/system/state"}
    };
  }
}
