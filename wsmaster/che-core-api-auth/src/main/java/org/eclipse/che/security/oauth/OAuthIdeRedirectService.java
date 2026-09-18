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
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.commons.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateless OAuth redirect proxy for browser-based IDE extensions.
 *
 * <p>IDE extensions (e.g. GitLab Workflow) running in a browser workspace cannot use protocol-based
 * redirect URIs ({@code vscode://...}). This endpoint acts as a stable, pre-registrable OAuth
 * {@code redirect_uri} that forwards the authorization code to the dynamic workspace callback URL.
 *
 * <p>The workspace callback URL is encoded in the OAuth {@code state} parameter as a base64url JSON
 * object: {@code {"s":"<csrf_state>","c":"<workspace_callback_url>"}}.
 */
@Path("oauth/ide-redirect")
public class OAuthIdeRedirectService extends Service {
  private static final Logger LOG = LoggerFactory.getLogger(OAuthIdeRedirectService.class);

  private final String cheApiEndpoint;
  private final String allowedCallbackHosts;

  @Inject
  public OAuthIdeRedirectService(
      @Named("che.api") String cheApiEndpoint,
      @Nullable @Named("che.oauth.ide_redirect.allowed_hosts") String allowedCallbackHosts) {
    this.cheApiEndpoint = cheApiEndpoint;
    this.allowedCallbackHosts = allowedCallbackHosts;
  }

  /**
   * Receives an OAuth callback from the identity provider and redirects to the workspace IDE with
   * the authorization code and CSRF state.
   */
  @GET
  public Response ideRedirect(@Context UriInfo uriInfo)
      throws BadRequestException, ForbiddenException {
    String state = uriInfo.getQueryParameters().getFirst("state");
    if (state == null || state.isBlank()) {
      throw new BadRequestException("Missing 'state' query parameter");
    }

    JsonObject decoded;
    try {
      byte[] jsonBytes = Base64.getUrlDecoder().decode(state);
      decoded = JsonParser.parseString(new String(jsonBytes)).getAsJsonObject();
    } catch (IllegalArgumentException | JsonSyntaxException e) {
      throw new BadRequestException("Invalid 'state' parameter: not a valid base64url JSON object");
    }

    if (!decoded.has("s") || !decoded.has("c")) {
      throw new BadRequestException(
          "Invalid 'state' parameter: missing required fields 's' and 'c'");
    }

    String csrfState = decoded.get("s").getAsString();
    String callbackUrl = decoded.get("c").getAsString();

    validateCallbackHost(callbackUrl);

    URI redirectTarget;
    try {
      URI callbackUri = new URI(callbackUrl);
      String existingQuery = callbackUri.getRawQuery();
      StringBuilder newQuery = new StringBuilder();
      if (existingQuery != null && !existingQuery.isEmpty()) {
        newQuery.append(existingQuery).append('&');
      }
      newQuery.append("code=").append(encodeQueryParam(getQueryParam(uriInfo, "code")));
      newQuery.append("&state=").append(encodeQueryParam(csrfState));

      String error = uriInfo.getQueryParameters().getFirst("error");
      if (error != null) {
        newQuery.append("&error=").append(encodeQueryParam(error));
        String errorDescription = uriInfo.getQueryParameters().getFirst("error_description");
        if (errorDescription != null) {
          newQuery.append("&error_description=").append(encodeQueryParam(errorDescription));
        }
      }

      redirectTarget =
          new URI(
              callbackUri.getScheme(),
              callbackUri.getAuthority(),
              callbackUri.getPath(),
              null,
              callbackUri.getFragment());
      redirectTarget = URI.create(redirectTarget.toString() + "?" + newQuery);
    } catch (URISyntaxException e) {
      throw new BadRequestException("Invalid callback URL in 'state' parameter: " + e.getMessage());
    }

    LOG.debug("IDE OAuth redirect: forwarding to workspace callback at {}", redirectTarget);
    return Response.temporaryRedirect(redirectTarget).build();
  }

  private String getQueryParam(UriInfo uriInfo, String name) {
    String value = uriInfo.getQueryParameters().getFirst(name);
    return value != null ? value : "";
  }

  private static String encodeQueryParam(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  @VisibleForTesting
  void validateCallbackHost(String callbackUrl) throws BadRequestException, ForbiddenException {
    URI uri;
    try {
      uri = new URI(callbackUrl);
    } catch (URISyntaxException e) {
      throw new BadRequestException("Invalid callback URL: " + e.getMessage());
    }

    String scheme = uri.getScheme();
    if (scheme == null || !scheme.equals("https")) {
      throw new BadRequestException("Callback URL must use HTTPS scheme");
    }

    String callbackHost = uri.getHost();
    if (callbackHost == null) {
      throw new BadRequestException("Callback URL must contain a valid host");
    }

    String pattern = getEffectiveAllowedHostsPattern();
    if (pattern == null) {
      LOG.warn(
          "IDE OAuth redirect blocked: unable to determine allowed callback hosts"
              + " (che.oauth.ide_redirect.allowed_hosts is not set and che.api is not parseable)");
      throw new ForbiddenException("Unable to determine allowed callback hosts; denying redirect");
    }
    if (!matchesHostPattern(callbackHost, pattern)) {
      LOG.warn(
          "IDE OAuth redirect blocked: callback host '{}' does not match allowed pattern '{}'",
          callbackHost,
          pattern);
      throw new ForbiddenException(
          "Callback URL host '" + callbackHost + "' is not in the allowed hosts list");
    }
  }

  private String getEffectiveAllowedHostsPattern() {
    if (allowedCallbackHosts != null && !allowedCallbackHosts.isBlank()) {
      return allowedCallbackHosts;
    }
    // Default to exact Che server host. Workspaces typically use path-based routing
    // (same host, different path), so a wildcard is unnecessarily permissive.
    // For clusters with subdomain-based routing, set che.oauth.ide_redirect.allowed_hosts
    // explicitly (e.g. "*.apps.cluster.example.com").
    try {
      URI cheUri = new URI(cheApiEndpoint);
      String cheHost = cheUri.getHost();
      if (cheHost != null) {
        return cheHost;
      }
    } catch (URISyntaxException e) {
      LOG.warn("Failed to parse che.api endpoint '{}': {}", cheApiEndpoint, e.getMessage());
    }
    return null;
  }

  @VisibleForTesting
  static boolean matchesHostPattern(String host, String pattern) {
    if (pattern.startsWith("*.")) {
      String suffix = pattern.substring(1); // e.g. ".apps.cluster.example.com"
      return host.endsWith(suffix) || host.equals(pattern.substring(2));
    }
    return host.equals(pattern);
  }
}
