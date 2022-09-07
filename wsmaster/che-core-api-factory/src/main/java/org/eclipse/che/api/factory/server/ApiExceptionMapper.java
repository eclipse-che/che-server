/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
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

import java.util.Map;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl.DevfileLocation;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;

/**
 * Helps to convert {@link Exception}s with some specific causes into REST-friendly {@link
 * ApiException}
 */
public class ApiExceptionMapper {

  public static ApiException toApiException(DevfileException devfileException) {
    ApiException apiException = getApiException(devfileException.getCause());
    return (apiException != null)
        ? apiException
        : new BadRequestException(
            "Error occurred during file content retrieval."
                + "Cause: "
                + devfileException.getMessage());
  }

  public static ApiException toApiException(ScmUnauthorizedException scmUnauthorizedException) {
    ApiException apiException = getApiException(scmUnauthorizedException);
    return (apiException != null)
        ? apiException
        : new BadRequestException(
            "Error occurred during SCM authorisation."
                + "Cause: "
                + scmUnauthorizedException.getMessage());
  }

  public static ApiException toApiException(
      DevfileException devfileException, DevfileLocation location) {
    ApiException cause = getApiException(devfileException.getCause());
    return (cause != null)
        ? cause
        : new BadRequestException(
            "Error occurred during creation a workspace from devfile located at `"
                + location.location()
                + "`. Cause: "
                + devfileException.getMessage());
  }

  private static ApiException getApiException(Throwable throwable) {
    if (throwable instanceof ScmUnauthorizedException) {
      ScmUnauthorizedException scmCause = (ScmUnauthorizedException) throwable;
      return new UnauthorizedException(
          "SCM Authentication required",
          401,
          Map.of(
              "oauth_version", scmCause.getOauthVersion(),
              "oauth_provider", scmCause.getOauthProvider(),
              "oauth_authentication_url", scmCause.getAuthenticateUrl()));
    } else if (throwable instanceof UnknownScmProviderException) {
      return new ServerException(
          "Provided location is unknown or misconfigured on the server side. Error message: "
              + throwable.getMessage());
    } else if (throwable instanceof ScmCommunicationException) {
      return new ServerException(
          "There is an error happened when communicate with SCM server. Error message: "
              + throwable.getMessage());
    }
    return null;
  }
}
