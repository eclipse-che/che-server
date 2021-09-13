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
package org.eclipse.che.multiuser.keycloak.server.deploy;

import java.util.regex.Pattern;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class KeycloakServletModuleTest {
  private static final Pattern KEYCLOAK_FILTER_PATHS_PATTERN =
      Pattern.compile(KeycloakServletModule.KEYCLOAK_FILTER_PATHS);

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
