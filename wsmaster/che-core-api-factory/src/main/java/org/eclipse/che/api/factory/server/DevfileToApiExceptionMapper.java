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
 * Helps to convert {@link DevfileException}s with some specific causes into REST-friendly {@link
 * ApiException}
 */
public class DevfileToApiExceptionMapper {

  public static ApiException toApiException(DevfileException devfileException) {
    ApiException cause = getApiException(devfileException);
    return (cause != null)
        ? cause
        : new BadRequestException(
            "Error occurred during file content retrieval."
                + "Cause: "
                + devfileException.getMessage());
  }

  public static ApiException toApiException(
      DevfileException devfileException, DevfileLocation location) {
    ApiException cause = getApiException(devfileException);
    return (cause != null)
        ? cause
        : new BadRequestException(
            "Error occurred during creation a workspace from devfile located at `"
                + location.location()
                + "`. Cause: "
                + devfileException.getMessage());
  }

  private static ApiException getApiException(DevfileException devfileException) {
    Throwable cause = devfileException.getCause();
    if (cause instanceof ScmUnauthorizedException) {
      ScmUnauthorizedException scmCause = (ScmUnauthorizedException) cause;
      return new UnauthorizedException(
          "SCM Authentication required",
          401,
          Map.of(
              "oauth_version", scmCause.getOauthVersion(),
              "oauth_provider", scmCause.getOauthProvider(),
              "oauth_authentication_url", scmCause.getAuthenticateUrl()));
    } else if (cause instanceof UnknownScmProviderException) {
      return new ServerException(
          "Provided location is unknown or misconfigured on the server side. Error message: "
              + cause.getMessage());
    } else if (cause instanceof ScmCommunicationException) {
      return new ServerException(
          "There is an error happened when communicate with SCM server. Error message: "
              + cause.getMessage());
    }
    return null;
  }
}
