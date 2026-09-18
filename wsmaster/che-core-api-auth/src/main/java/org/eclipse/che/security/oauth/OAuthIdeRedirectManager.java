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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriBuilderException;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateless OAuth redirect proxy for browser-based IDE extensions.
 *
 * <p>IDE extensions (e.g. GitLab Workflow) running in a browser workspace cannot use protocol-based
 * redirect URIs ({@code vscode://...}). {@link OAuthAuthenticationService#ideRedirect()} acts as a
 * stable, pre-registrable OAuth {@code redirect_uri} that forwards the authorization code to the
 * dynamic workspace callback URL.
 *
 * <p>The workspace callback URL is encoded in the OAuth {@code state} parameter as a base64url JSON
 * object: {@code {"s":"<csrf_state>","c":"<workspace_callback_url>"}}.
 *
 * <p>The {@code state} parameter is chosen by whoever builds the authorization URL, so the callback
 * URL it carries is untrusted input. Before the authorization code is forwarded, the callback URL
 * is checked to be located under the main URL of a workspace that belongs to the authenticated user
 * of the current request (see {@link UserWorkspaceUrlProvider}). Without that check an attacker
 * could hand a victim an authorization URL pointing at the attacker's own workspace and collect the
 * victim's authorization code.
 */
@Singleton
public class OAuthIdeRedirectManager {
  private static final Logger LOG = LoggerFactory.getLogger(OAuthIdeRedirectManager.class);

  /** Name of the {@code state} field holding the CSRF token expected by the IDE extension. */
  private static final String STATE_CSRF_FIELD = "s";

  /** Name of the {@code state} field holding the workspace callback URL. */
  private static final String STATE_CALLBACK_URL_FIELD = "c";

  private final UserWorkspaceUrlProvider userWorkspaceUrlProvider;

  @Inject
  public OAuthIdeRedirectManager(UserWorkspaceUrlProvider userWorkspaceUrlProvider) {
    this.userWorkspaceUrlProvider = userWorkspaceUrlProvider;
  }

  /**
   * Receives an OAuth callback from the identity provider and redirects to the workspace IDE with
   * the authorization code and CSRF state.
   */
  public Response ideRedirect(UriInfo uriInfo)
      throws BadRequestException, ForbiddenException, ServerException {
    Subject subject = EnvironmentContext.getCurrent().getSubject();
    if (subject == null || subject.isAnonymous()) {
      throw new ForbiddenException("IDE OAuth redirect requires an authenticated user");
    }

    MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
    String code = params.getFirst("code");
    String error = params.getFirst("error");
    if (isNullOrBlank(code) && isNullOrBlank(error)) {
      throw new BadRequestException("Missing both 'code' and 'error' query parameters");
    }

    JsonObject state = decodeState(params.getFirst("state"));
    String csrfState = getStringField(state, STATE_CSRF_FIELD);
    URI callbackUri = parseCallbackUrl(getStringField(state, STATE_CALLBACK_URL_FIELD));

    authorizeCallbackUrl(subject, callbackUri);

    UriBuilder target = UriBuilder.fromUri(callbackUri).queryParam("state", csrfState);
    if (!isNullOrBlank(code)) {
      target.queryParam("code", code);
    }
    if (!isNullOrBlank(error)) {
      target.queryParam("error", error);
      String errorDescription = params.getFirst("error_description");
      if (!isNullOrBlank(errorDescription)) {
        target.queryParam("error_description", errorDescription);
      }
    }

    URI redirectTarget;
    try {
      redirectTarget = target.build();
    } catch (IllegalArgumentException | UriBuilderException e) {
      throw new BadRequestException("Unable to build the redirect URL: " + e.getMessage());
    }

    return Response.temporaryRedirect(redirectTarget)
        .header("Cache-Control", "no-store")
        .header("Referrer-Policy", "no-referrer")
        .build();
  }

  /**
   * Fails unless the callback URL is located under the main URL of one of the workspaces of the
   * given user.
   */
  private void authorizeCallbackUrl(Subject subject, URI callbackUri)
      throws ForbiddenException, ServerException {
    Set<String> workspaceUrls = userWorkspaceUrlProvider.getWorkspaceUrls();
    for (String workspaceUrl : workspaceUrls) {
      if (isLocatedUnder(callbackUri, workspaceUrl)) {
        return;
      }
    }
    // The callback URL is attacker controlled, so it is logged at debug level only.
    LOG.warn(
        "IDE OAuth redirect blocked: the callback URL does not belong to any of the {} workspace(s)"
            + " of user '{}'",
        workspaceUrls.size(),
        subject.getUserName());
    LOG.debug(
        "IDE OAuth redirect blocked: callback URL '{}' is not under any of {}",
        callbackUri,
        workspaceUrls);
    throw new ForbiddenException(
        "The callback URL does not belong to a workspace of the authenticated user");
  }

  /**
   * Returns {@code true} if {@code callbackUri} addresses the same origin as {@code workspaceUrl}
   * and its path is {@code workspaceUrl}'s path or a path segment underneath it.
   *
   * <p>Comparison is segment aware, so {@code /user/wksp/3100} does not match {@code
   * /user/wksp/31000}. Only the origin and the path of {@code workspaceUrl} are taken into account:
   * the main URL published by the DevWorkspace Operator may carry a query string of its own.
   */
  @VisibleForTesting
  static boolean isLocatedUnder(URI callbackUri, String workspaceUrl) {
    if (isNullOrBlank(workspaceUrl)) {
      return false;
    }
    URI workspaceUri;
    try {
      workspaceUri = new URI(workspaceUrl).normalize();
    } catch (URISyntaxException e) {
      LOG.warn("Ignoring unparseable workspace URL '{}': {}", workspaceUrl, e.getMessage());
      return false;
    }
    if (workspaceUri.getScheme() == null || workspaceUri.getHost() == null) {
      return false;
    }
    if (!workspaceUri.getScheme().equalsIgnoreCase(callbackUri.getScheme())
        || !workspaceUri.getHost().equalsIgnoreCase(callbackUri.getHost())
        || effectivePort(workspaceUri) != effectivePort(callbackUri)) {
      return false;
    }

    String workspacePath = trimTrailingSlashes(workspaceUri.getPath());
    // An empty workspace path would turn every path into a match.
    if (workspacePath.isEmpty()) {
      return false;
    }
    String callbackPath = trimTrailingSlashes(callbackUri.getPath());
    return callbackPath.equals(workspacePath) || callbackPath.startsWith(workspacePath + "/");
  }

  /** Decodes the base64url encoded JSON object carried by the OAuth {@code state} parameter. */
  private static JsonObject decodeState(String state) throws BadRequestException {
    if (isNullOrBlank(state)) {
      throw new BadRequestException("Missing 'state' query parameter");
    }
    try {
      byte[] jsonBytes = Base64.getUrlDecoder().decode(state);
      return JsonParser.parseString(new String(jsonBytes, StandardCharsets.UTF_8))
          .getAsJsonObject();
    } catch (IllegalArgumentException | IllegalStateException | JsonParseException e) {
      throw new BadRequestException("Invalid 'state' parameter: not a valid base64url JSON object");
    }
  }

  private static String getStringField(JsonObject state, String field) throws BadRequestException {
    try {
      if (state.has(field) && state.get(field).getAsJsonPrimitive().isString()) {
        String value = state.get(field).getAsString();
        if (!value.isBlank()) {
          return value;
        }
      }
    } catch (IllegalStateException | ClassCastException e) {
      // fall through to the exception below
    }
    throw new BadRequestException(
        "Invalid 'state' parameter: missing or malformed field '" + field + "'");
  }

  /** Parses and sanity checks the workspace callback URL taken from the {@code state} parameter. */
  private static URI parseCallbackUrl(String callbackUrl) throws BadRequestException {
    URI uri;
    try {
      uri = new URI(callbackUrl);
    } catch (URISyntaxException e) {
      throw new BadRequestException("Invalid callback URL in 'state' parameter: " + e.getMessage());
    }
    if (!uri.isAbsolute() || uri.isOpaque() || uri.getHost() == null) {
      throw new BadRequestException("Callback URL must be an absolute URL with a host");
    }
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !scheme.equals("http")) {
      throw new BadRequestException("Callback URL must use the http or https scheme");
    }
    if (uri.getUserInfo() != null) {
      throw new BadRequestException("Callback URL must not contain user information");
    }
    if (uri.getFragment() != null) {
      throw new BadRequestException("Callback URL must not contain a fragment");
    }
    String rawPath = uri.getRawPath();
    if (rawPath == null || rawPath.toLowerCase(Locale.ROOT).contains("%2f")) {
      throw new BadRequestException("Callback URL must not contain an encoded path separator");
    }
    URI normalized = uri.normalize();
    if (normalized.getPath().contains("..")) {
      throw new BadRequestException("Callback URL must not contain relative path segments");
    }
    return normalized;
  }

  /** Returns the port of the URI, substituting the default port of its scheme when unset. */
  private static int effectivePort(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private static String trimTrailingSlashes(String path) {
    if (path == null) {
      return "";
    }
    int end = path.length();
    while (end > 0 && path.charAt(end - 1) == '/') {
      end--;
    }
    return path.substring(0, end);
  }

  private static boolean isNullOrBlank(String value) {
    return value == null || value.isBlank();
  }
}
