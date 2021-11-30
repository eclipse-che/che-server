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

import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.REALM_SETTING;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.DefaultHttpJsonRequest;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.multiuser.oidc.OIDCInfo;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class KeycloakUserRemoverTest {

  @Mock private KeycloakSettings keycloakSettings;
  @Mock private OIDCInfo oidcInfo;
  @Mock private HttpJsonRequestFactory requestFactory;
  @Mock private KeycloakPasswordGrantTokenRequester tokenRequester;
  @Mock private DefaultHttpJsonRequest jsonRequest;

  private KeycloakUserRemover keycloakUserRemover;

  @BeforeMethod
  public void setUp() throws Exception {
    initMocks(this);

    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(REALM_SETTING, "realm");
    when(keycloakSettings.get()).thenReturn(settingsMap);
    when(oidcInfo.getAuthServerURL()).thenReturn("auth.server.url");
    when(requestFactory.fromUrl(anyString())).thenReturn(jsonRequest);
    when(tokenRequester.requestToken(anyString(), anyString(), anyString())).thenReturn("token");
    when(jsonRequest.setAuthorizationHeader(anyString())).thenCallRealMethod();
    when(jsonRequest.useDeleteMethod()).thenCallRealMethod();
    when(jsonRequest.setMethod(anyString())).thenCallRealMethod();
    keycloakUserRemover =
        new KeycloakUserRemover(
            true, "admin", "admin", keycloakSettings, tokenRequester, oidcInfo, requestFactory);
  }

  @Test
  public void shouldCatchFileNotFoundException() throws Exception {
    when(jsonRequest.request()).thenThrow(FileNotFoundException.class);
    keycloakUserRemover.removeUserFromKeycloak("123");
    verify(jsonRequest).request();
  }

  @Test
  public void shouldCatchNotFoundException() throws Exception {
    when(jsonRequest.request()).thenThrow(NotFoundException.class);
    keycloakUserRemover.removeUserFromKeycloak("123");
    verify(jsonRequest).request();
  }

  @Test(
      expectedExceptions = ApiException.class,
      expectedExceptionsMessageRegExp = "Exception during removing user from Keycloak")
  public void shouldThrowAPIException() throws Exception {
    when(jsonRequest.request()).thenThrow(ServerException.class);
    keycloakUserRemover.removeUserFromKeycloak("123");
    verify(jsonRequest).request();
  }
}
