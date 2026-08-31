/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.*;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.annotations.Required;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.shared.dto.OAuthAuthenticatorDescriptor;

/** RESTful wrapper for OAuthAuthenticator. */
@Path("oauth")
public class OAuthAuthenticationService extends Service {
  @Context protected UriInfo uriInfo;
  @Context protected SecurityContext security;

  @Inject private OAuthAPI oAuthAPI;
  @Inject private AuthorisationRequestManager authorisationRequestManager;
  @Inject private PersonalAccessTokenManager personalAccessTokenManager;
  @Inject private GitCredentialManager gitCredentialManager;

  /**
   * Redirect request to OAuth provider site for authentication|authorization. Client must provide
   * query parameters, that may or may not be required, depending on the active implementation of
   * {@link OAuthAPI}.
   *
   * @param oauthProvider -
   * @param redirectAfterLogin
   * @param scopes - list
   * @return typically Response that redirect user for OAuth provider site
   */
  @GET
  @Path("authenticate")
  public Response authenticate(
      @QueryParam("oauth_provider") String oauthProvider,
      @QueryParam("redirect_after_login") String redirectAfterLogin,
      @QueryParam("scope") List<String> scopes,
      @Context HttpServletRequest request)
      throws NotFoundException,
          OAuthAuthenticationException,
          BadRequestException,
          ForbiddenException {
    return oAuthAPI.authenticate(uriInfo, oauthProvider, scopes, redirectAfterLogin, request);
  }

  @GET
  @Path("callback")
  /** Process OAuth callback */
  public Response callback(@QueryParam("errorValues") List<String> errorValues)
      throws OAuthAuthenticationException, NotFoundException, ForbiddenException {
    authorisationRequestManager.callback(uriInfo, errorValues);
    return oAuthAPI.callback(uriInfo, errorValues);
  }

  /**
   * Gets list of installed OAuth authenticators.
   *
   * @return list of installed OAuth authenticators
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Set<OAuthAuthenticatorDescriptor> getRegisteredAuthenticators() throws ForbiddenException {
    return oAuthAPI.getRegisteredAuthenticators(uriInfo);
  }

  /**
   * Gets OAuth token for user.
   *
   * @param oauthProvider OAuth provider name
   * @return OAuthToken
   * @throws ServerException
   */
  @GET
  @Path("token")
  @Produces(MediaType.APPLICATION_JSON)
  public OAuthToken token(@Required @QueryParam("oauth_provider") String oauthProvider)
      throws ServerException,
          UnauthorizedException,
          NotFoundException,
          ForbiddenException,
          BadRequestException,
          ConflictException {
    return oAuthAPI.getOrRefreshToken(oauthProvider);
  }

  /**
   * Refreshes the OAuth token for the given provider and persists the updated token as a Kubernetes
   * secret and git credential, so that subsequent SCM operations use the new access token.
   *
   * @param oauthProvider OAuth provider name
   */
  @POST
  @Path("refresh")
  public void refresh(@Required @QueryParam("oauth_provider") String oauthProvider)
      throws ServerException,
          UnauthorizedException,
          NotFoundException,
          ForbiddenException,
          UnsatisfiedScmPreconditionException,
          ScmConfigurationPersistenceException {
    OAuthToken token = oAuthAPI.refreshToken(oauthProvider);
    Subject subject = EnvironmentContext.getCurrent().getSubject();
    PersonalAccessToken personalAccessToken =
        new PersonalAccessToken(
            oAuthAPI.getProviderUrl(oauthProvider),
            oauthProvider,
            subject.getUserId(),
            null,
            subject.getUserName(),
            NameGenerator.generate("oauth2-", 5),
            NameGenerator.generate("id-", 5),
            token.getToken(),
            token.getRefreshToken(),
            token.getExpiresIn());
    personalAccessTokenManager.store(personalAccessToken);
    gitCredentialManager.createOrReplace(personalAccessToken);
  }

  /**
   * Invalidate OAuth token for user.
   *
   * @param oauthProvider OAuth provider name
   */
  @DELETE
  @Path("token")
  public void invalidate(@Required @QueryParam("oauth_provider") String oauthProvider)
      throws UnauthorizedException, NotFoundException, ServerException, ForbiddenException {
    oAuthAPI.invalidateToken(oauthProvider);
  }
}
