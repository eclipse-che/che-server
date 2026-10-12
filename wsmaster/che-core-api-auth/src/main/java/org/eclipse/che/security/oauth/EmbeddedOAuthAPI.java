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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher.OAUTH_2_PREFIX;
import static org.eclipse.che.commons.lang.UrlUtils.*;
import static org.eclipse.che.commons.lang.UrlUtils.getParameter;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.eclipse.che.security.oauth.OAuthAuthenticator.SSL_ERROR_CODE;
import static org.eclipse.che.security.oauth1.OAuthAuthenticationService.ERROR_QUERY_NAME;

import com.google.api.client.auth.oauth2.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.core.rest.shared.dto.LinkParameter;
import org.eclipse.che.api.core.util.LinksHelper;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.shared.dto.OAuthAuthenticatorDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of functional API component for {@link OAuthAuthenticationService}, that uses
 * {@link OAuthAuthenticator}.
 *
 * @author Mykhailo Kuznietsov
 */
@Singleton
public class EmbeddedOAuthAPI implements OAuthAPI {
  private static final Logger LOG = LoggerFactory.getLogger(EmbeddedOAuthAPI.class);

  @Inject
  @Named("che.auth.access_denied_error_page")
  protected String errorPage;

  @Inject protected OAuthAuthenticatorProvider oauth2Providers;
  @Inject protected org.eclipse.che.security.oauth1.OAuthAuthenticatorProvider oauth1Providers;
  @Inject private PersonalAccessTokenManager personalAccessTokenManager;
  @Inject private RedirectAfterLoginUrlValidator redirectUrlValidator;
  private String redirectAfterLogin;

  @Override
  public Response authenticate(
      UriInfo uriInfo,
      String oauthProvider,
      List<String> scopes,
      String redirectAfterLogin,
      HttpServletRequest request)
      throws NotFoundException, OAuthAuthenticationException, ForbiddenException {
    if (!isNullOrEmpty(redirectAfterLogin)) {
      // Refuse the URL before the browser leaves for the OAuth provider: the callback refuses to
      // redirect to it anyway, and the user would have authorized Che for nothing.
      redirectUrlValidator.authorize(redirectAfterLogin);
    }
    this.redirectAfterLogin = redirectAfterLogin;
    OAuthAuthenticator oauth = getAuthenticator(oauthProvider);
    final String authUrl =
        oauth.getAuthenticateUrl(getRequestUrl(uriInfo), scopes == null ? emptyList() : scopes);
    return Response.temporaryRedirect(URI.create(authUrl)).build();
  }

  @Override
  public Response callback(UriInfo uriInfo, @Nullable List<String> errorValues)
      throws NotFoundException, ForbiddenException {
    URL requestUrl = getRequestUrl(uriInfo);
    Map<String, List<String>> params = getQueryParametersFromState(getState(requestUrl));
    errorValues = errorValues == null ? uriInfo.getQueryParameters().get("error") : errorValues;
    if (!isNullOrEmpty(redirectAfterLogin)
        && errorValues != null
        && errorValues.contains("access_denied")) {
      return redirect(encodeRedirectUrl(redirectAfterLogin + "&error_code=access_denied"));
    }
    final String providerName = getParameter(params, "oauth_provider");
    OAuthAuthenticator oauth = getAuthenticator(providerName);
    final List<String> scopes = params.get("scope");
    try {
      TokenResponse tokenResponse =
          oauth.callback(requestUrl, scopes == null ? emptyList() : scopes);
      // Store the full token response (including refresh token and expiry) so that
      // tokens can be refreshed later without requiring re-authorization.
      // Providers that issue non-expiring tokens omit `expires_in`, so fall back to 0.
      Long expiresInSeconds = tokenResponse.getExpiresInSeconds();
      personalAccessTokenManager.store(
          new PersonalAccessToken(
              oauth.getEndpointUrl(),
              providerName,
              EnvironmentContext.getCurrent().getSubject().getUserId(),
              null,
              null,
              NameGenerator.generate(OAUTH_2_PREFIX, 5),
              NameGenerator.generate("id-", 5),
              tokenResponse.getAccessToken(),
              tokenResponse.getRefreshToken(),
              expiresInSeconds == null ? 0 : expiresInSeconds));
    } catch (OAuthAuthenticationException e) {
      return redirect(getRedirectAfterLoginUrl(params, "access_denied"));
    } catch (UnsatisfiedScmPreconditionException | ScmConfigurationPersistenceException e) {
      // Skip exception, the token will be stored in the next request.
      LOG.error(e.getMessage(), e);
    } catch (ScmCommunicationException e) {
      if (e.getStatusCode() == SSL_ERROR_CODE) {
        return redirect(getRedirectAfterLoginUrl(params, "ssl_exception"));
      } else {
        LOG.error(e.getMessage(), e);
      }
    }
    return redirect(getRedirectAfterLoginUrl(params, null));
  }

  /**
   * Redirects the browser to the given redirect after login URL, unless the URL is not allowed to
   * be redirected to. The URL comes from the OAuth {@code state}, which is chosen by whoever built
   * the authorization URL, so it is authorized here, at the point of use.
   */
  private Response redirect(String redirectAfterLogin) throws ForbiddenException {
    return Response.temporaryRedirect(redirectUrlValidator.authorize(redirectAfterLogin)).build();
  }

  /**
   * Returns the redirect after login URL from the query parameters. If the URL is encoded by the
   * CSM provider, it will be decoded, to avoid unsupported characters in the URL.
   *
   * <p>The returned URL is taken from the OAuth {@code state} and is not authorized here: it has to
   * be passed through {@link RedirectAfterLoginUrlValidator} before the browser is redirected to
   * it.
   *
   * @param parameters the query parameters
   * @param errorCode the error code or {@code null}
   * @return the redirect after login URL, or {@code null} if the parameters carry none
   */
  @Nullable
  public static String getRedirectAfterLoginUrl(
      Map<String, List<String>> parameters, @Nullable String errorCode) {
    String redirectAfterLogin = getParameter(parameters, "redirect_after_login");
    if (isNullOrEmpty(redirectAfterLogin)) {
      return null;
    }
    try {
      URI.create(redirectAfterLogin);
    } catch (IllegalArgumentException e) {
      // the redirectUrl was decoded by the CSM provider, so we need to encode it back.
      redirectAfterLogin = encodeRedirectUrl(redirectAfterLogin);
    }
    if (errorCode != null) {
      redirectAfterLogin += String.format("&%s=%s", ERROR_QUERY_NAME, errorCode);
    }
    return redirectAfterLogin;
  }

  /**
   * Encode the redirect URL query parameters to avoid the error when the redirect URL contains
   * JSON, as a query parameter. This prevents passing unsupported characters, like '{' and '}' to
   * the {@link URI#create(String)} method.
   */
  private static String encodeRedirectUrl(String url) {
    try {
      String query = new URL(url).getQuery();
      return url.substring(0, url.indexOf(query)) + URLEncoder.encode(query, UTF_8);
    } catch (MalformedURLException e) {
      LOG.error(e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public Set<OAuthAuthenticatorDescriptor> getRegisteredAuthenticators(UriInfo uriInfo) {
    Set<OAuthAuthenticatorDescriptor> result = new HashSet<>();
    final UriBuilder uriBuilder =
        uriInfo.getBaseUriBuilder().clone().path(OAuthAuthenticationService.class);
    Set<String> registeredProviderNames =
        new HashSet<>(oauth2Providers.getRegisteredProviderNames());
    registeredProviderNames.addAll(oauth1Providers.getRegisteredProviderNames());
    for (String name : registeredProviderNames) {
      final List<Link> links = new LinkedList<>();
      links.add(
          LinksHelper.createLink(
              HttpMethod.GET,
              uriBuilder
                  .clone()
                  .path(OAuthAuthenticationService.class, "authenticate")
                  .build()
                  .toString(),
              null,
              null,
              "Authenticate URL",
              newDto(LinkParameter.class)
                  .withName("oauth_provider")
                  .withRequired(true)
                  .withDefaultValue(name),
              newDto(LinkParameter.class)
                  .withName("mode")
                  .withRequired(true)
                  .withDefaultValue("federated_login")));
      OAuthAuthenticator authenticator = oauth2Providers.getAuthenticator(name);
      String endpointUrl =
          authenticator != null
              ? authenticator.getEndpointUrl()
              : oauth1Providers.getAuthenticator(name).getEndpointUrl();
      String clientId = authenticator != null ? authenticator.getClientId() : null;
      result.add(
          newDto(OAuthAuthenticatorDescriptor.class)
              .withName(name)
              .withEndpointUrl(endpointUrl)
              .withClientId(clientId)
              .withLinks(links));
    }
    return result;
  }

  @Override
  public OAuthToken getOrRefreshToken(String oauthProvider)
      throws NotFoundException, UnauthorizedException, ServerException {
    OAuthAuthenticator provider = getAuthenticator(oauthProvider);
    Subject subject = EnvironmentContext.getCurrent().getSubject();
    try {
      OAuthToken token = provider.getOrRefreshToken(subject.getUserId());
      if (token == null) {
        token = provider.getOrRefreshToken(subject.getUserName());
      }
      if (token != null) {
        return token;
      } else {
        Optional<PersonalAccessToken> tokenOptional;
        try {
          // The token is read as stored: refreshing it is this class' own job, so a read that
          // refreshes would come back here through the SCM token fetcher and never terminate.
          tokenOptional = personalAccessTokenManager.getStored(subject, oauthProvider, null, null);
          if (tokenOptional.isEmpty()) {
            tokenOptional =
                personalAccessTokenManager.getStored(
                    subject, null, provider.getEndpointUrl(), null);
          }
          if (tokenOptional.isPresent()) {
            PersonalAccessToken persistedToken = tokenOptional.get();
            if (isNullOrEmpty(persistedToken.getRefreshToken())) {
              // Nothing to refresh with, e.g. a user-supplied personal access token.
              return newDto(OAuthToken.class).withToken(persistedToken.getToken());
            }
            // The persisted access token is returned as stored, so it may already have expired.
            // Restore the credential and refresh it, otherwise the expired token would be handed
            // out on every call, as the in-memory store stays empty.
            restoreCredential(provider, persistedToken, subject.getUserId());
            OAuthToken refreshedToken = provider.refreshToken(subject.getUserId());
            if (refreshedToken == null) {
              throw getUnauthorizedException(subject.getUserId());
            }
            return refreshedToken;
          }
        } catch (ScmConfigurationPersistenceException | ScmCommunicationException e) {
          throw new RuntimeException(e);
        }
      }
      throw new UnauthorizedException(
          "OAuth token for user " + subject.getUserId() + " was not found");
    } catch (IOException e) {
      throw new ServerException(e.getLocalizedMessage(), e);
    }
  }

  @Override
  public OAuthToken refreshToken(String oauthProvider)
      throws NotFoundException, UnauthorizedException, ServerException {
    OAuthAuthenticator provider = getAuthenticator(oauthProvider);
    Subject subject = EnvironmentContext.getCurrent().getSubject();
    String userId = subject.getUserId();
    String userName = subject.getUserName();
    try {
      OAuthToken storedToken = provider.refreshToken(userId);
      if (storedToken == null) {
        storedToken = provider.refreshToken(userName);
      }

      if (storedToken != null) {
        return storedToken;
      } else {
        // Credential was not found in the in-memory store (e.g. after server restart).
        // Restore it from the persisted Kubernetes secret so the OAuth flow can refresh it. The
        // token is read as stored: it is this call that refreshes it, so letting the manager
        // refresh it on read would call back here endlessly.
        Optional<PersonalAccessToken> tokenOptional =
            personalAccessTokenManager.getStored(subject, oauthProvider, null, null);
        if (tokenOptional.isPresent()) {
          PersonalAccessToken token = tokenOptional.get();
          if (isNullOrEmpty(token.getRefreshToken())) {
            throw getUnauthorizedException(userId);
          }
          restoreCredential(provider, token, userId);
          OAuthToken refreshedToken = provider.refreshToken(userId);
          if (refreshedToken == null) {
            throw getUnauthorizedException(userId);
          }
          return refreshedToken;
        } else {
          throw getUnauthorizedException(userId);
        }
      }
    } catch (IOException | ScmConfigurationPersistenceException | ScmCommunicationException e) {
      throw new ServerException(e.getLocalizedMessage(), e);
    }
  }

  /**
   * Re-populate the in-memory credential store from a token persisted in a Kubernetes secret, so
   * that the OAuth flow can refresh it. The credential is gone from the in-memory store e.g. after
   * a server restart.
   */
  private void restoreCredential(
      OAuthAuthenticator provider, PersonalAccessToken token, String userId) throws IOException {
    TokenResponse tokenResponse =
        new TokenResponse()
            .setAccessToken(token.getToken())
            .setRefreshToken(token.getRefreshToken());
    // leave `expires_in` unset when the persisted token carries no expiry,
    // so that the credential is not treated as already expired
    if (token.getExpiresIn() > 0) {
      tokenResponse.setExpiresInSeconds(token.getExpiresIn());
    }
    provider.flow.createAndStoreCredential(tokenResponse, userId);
  }

  private UnauthorizedException getUnauthorizedException(String userId) {
    return new UnauthorizedException("OAuth token for user " + userId + " was not found");
  }

  @Override
  public void invalidateToken(String oauthProvider)
      throws NotFoundException, UnauthorizedException, ServerException {
    OAuthAuthenticator oauth = getAuthenticator(oauthProvider);
    OAuthToken oauthToken = getOrRefreshToken(oauthProvider);
    try {
      if (!oauth.invalidateToken(oauthToken.getToken())) {
        throw new UnauthorizedException(
            "OAuth token for provider " + oauthProvider + " was not found");
      }
    } catch (IOException e) {
      throw new ServerException(e.getMessage());
    }
  }

  @Override
  public String getProviderUrl(String oauthProvider) throws NotFoundException {
    return getAuthenticator(oauthProvider).getEndpointUrl();
  }

  protected OAuthAuthenticator getAuthenticator(String oauthProviderName) throws NotFoundException {
    OAuthAuthenticator oauth = oauth2Providers.getAuthenticator(oauthProviderName);
    if (oauth == null) {
      LOG.warn("Unsupported OAuth provider {} ", oauthProviderName);
      throw new NotFoundException("Unsupported OAuth provider " + oauthProviderName);
    }
    return oauth;
  }
}
