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
package org.eclipse.che.api.factory.server;

import static org.testng.Assert.*;

import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.rest.shared.dto.ExtendedError;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;
import org.testng.annotations.Test;

public class DevfileToApiExceptionMapperTest {

  @Test
  public void shouldReturnUnauthorizedExceptionIfCauseIsScmUnauthorized() {

    ScmUnauthorizedException scmUnauthorizedException =
        new ScmUnauthorizedException(
            "msg", "gitlab", "2.0", "http://gitlab.com/oauth/authenticate");

    ApiException exception =
        DevfileToApiExceptionMapper.toApiException(
            new DevfileException("text", scmUnauthorizedException));
    assertTrue(exception instanceof UnauthorizedException);
    assertEquals(((ExtendedError) exception.getServiceError()).getErrorCode(), 401);
    assertEquals(((ExtendedError) exception.getServiceError()).getAttributes().size(), 3);
    assertEquals(
        ((ExtendedError) exception.getServiceError()).getAttributes().get("oauth_version"), "2.0");
    assertEquals(
        ((ExtendedError) exception.getServiceError())
            .getAttributes()
            .get("oauth_authentication_url"),
        "http://gitlab.com/oauth/authenticate");
  }

  @Test
  public void shouldReturnServerExceptionWhenCauseIsUnknownProvider() {

    UnknownScmProviderException scmProviderException =
        new UnknownScmProviderException("unknown", "http://gitlab.com/oauth/authenticate");
    ApiException exception =
        DevfileToApiExceptionMapper.toApiException(
            new DevfileException("text", scmProviderException));
    assertTrue(exception instanceof ServerException);
  }

  @Test
  public void shouldReturnServerExceptionWhenCauseIsCommunicationException() {

    ScmCommunicationException communicationException = new ScmCommunicationException("unknown");
    ApiException exception =
        DevfileToApiExceptionMapper.toApiException(
            new DevfileException("text", communicationException));
    assertTrue(exception instanceof ServerException);
  }

  @Test
  public void shouldReturnBadrequestExceptionWhenCauseIsOtherException() {

    ScmItemNotFoundException itemNotFoundException = new ScmItemNotFoundException("unknown");
    ApiException exception =
        DevfileToApiExceptionMapper.toApiException(
            new DevfileException("text", itemNotFoundException));
    assertTrue(exception instanceof BadRequestException);
  }
}
