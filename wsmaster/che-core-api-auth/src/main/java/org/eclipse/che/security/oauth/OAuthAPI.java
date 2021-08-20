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
package org.eclipse.che.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Set;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.*;
import org.eclipse.che.security.oauth.shared.dto.OAuthAuthenticatorDescriptor;

/**
 * Interface of OAuth authentication service API component, that is used for.
 *
 * @author Mykhailo Kuznietsov
 */
public interface OAuthAPI {

  /**
   * Implementation of method {@link OAuthAuthenticationService#authenticate(String, String, List,
   * HttpServletRequest)}
   */
  Response authenticate(
      UriInfo uriInfo,
      String oauthProvider,
      List<String> scopes,
      String redirectAfterLogin,
      HttpServletRequest request)
      throws NotFoundException, OAuthAuthenticationException, ForbiddenException,
          BadRequestException;

  /** Implementation of method {@link OAuthAuthenticationService#callback(List)} */
  Response callback(UriInfo uriInfo, List<String> errorValues)
      throws NotFoundException, OAuthAuthenticationException, ForbiddenException;

  /** Implementation of method {@link OAuthAuthenticationService#getRegisteredAuthenticators()} */
  Set<OAuthAuthenticatorDescriptor> getRegisteredAuthenticators(UriInfo uriInfo)
      throws ForbiddenException;

  /** Implementation of method {@link OAuthAuthenticationService#token(String)} */
  OAuthToken getToken(String oauthProvider)
      throws NotFoundException, UnauthorizedException, ServerException, ForbiddenException,
          BadRequestException, ConflictException;

  /** Implementation of method {@link OAuthAuthenticationService#invalidate(String)}} */
  void invalidateToken(String oauthProvider)
      throws NotFoundException, UnauthorizedException, ServerException, ForbiddenException;
}
