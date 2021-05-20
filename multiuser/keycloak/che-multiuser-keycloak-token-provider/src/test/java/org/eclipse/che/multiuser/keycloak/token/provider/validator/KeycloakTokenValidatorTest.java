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
package org.eclipse.che.multiuser.keycloak.token.provider.validator;

import org.eclipse.che.multiuser.keycloak.token.provider.exception.KeycloakException;
import org.junit.BeforeClass;
import org.junit.Test;

public class KeycloakTokenValidatorTest {
  private static final String VALID_TOKEN = "Bearer token";
  private static final String INVALID_TOKEN = "token";
  private static KeycloakTokenValidator keycloakTokenValidator;

  @BeforeClass
  public static void init() {
    keycloakTokenValidator = new KeycloakTokenValidator();
  }

  @Test
  public void processValidToken() throws KeycloakException {
    keycloakTokenValidator.validate(VALID_TOKEN);
  }

  @Test(expected = KeycloakException.class)
  public void processInvalidToken() throws KeycloakException {
    keycloakTokenValidator.validate(INVALID_TOKEN);
  }
}
