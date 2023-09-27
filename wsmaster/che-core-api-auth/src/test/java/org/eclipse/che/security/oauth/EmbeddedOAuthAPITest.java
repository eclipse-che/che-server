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
package org.eclipse.che.security.oauth;

import static java.util.Collections.singletonList;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.NotFoundException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Mykhailo Kuznietsov */
@Listeners(value = MockitoTestNGListener.class)
public class EmbeddedOAuthAPITest {

  @Mock OAuthAuthenticatorProvider providers;

  @InjectMocks EmbeddedOAuthAPI embeddedOAuthAPI;

  @Test(
      expectedExceptions = NotFoundException.class,
      expectedExceptionsMessageRegExp = "Unsupported OAuth provider unknown")
  public void shouldThrowExceptionIfNoSuchProviderFound() throws Exception {
    embeddedOAuthAPI.getToken("unknown");
  }

  @Test
  public void shouldBeAbleToGetUserToken() throws Exception {
    String provider = "myprovider";
    String token = "token123";
    OAuthAuthenticator authenticator = mock(OAuthAuthenticator.class);
    when(providers.getAuthenticator(eq(provider))).thenReturn(authenticator);

    when(authenticator.getToken(anyString())).thenReturn(newDto(OAuthToken.class).withToken(token));

    OAuthToken result = embeddedOAuthAPI.getToken(provider);

    assertEquals(result.getToken(), token);
  }

  @Test
  public void shouldEncodeRejectErrorForRedirectUrl() throws Exception {
    // given
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri()).thenReturn(new URI("http://eclipse.che"));
    Field redirectAfterLogin = EmbeddedOAuthAPI.class.getDeclaredField("redirectAfterLogin");
    redirectAfterLogin.setAccessible(true);
    redirectAfterLogin.set(embeddedOAuthAPI, "http://eclipse.che?quary=param");

    // when
    Response callback = embeddedOAuthAPI.callback(uriInfo, singletonList("access_denied"));

    // then
    assertEquals(
        callback.getLocation().toString(),
        "http://eclipse.che?quary%3Dparam%26error_code%3Daccess_denied");
  }
}
