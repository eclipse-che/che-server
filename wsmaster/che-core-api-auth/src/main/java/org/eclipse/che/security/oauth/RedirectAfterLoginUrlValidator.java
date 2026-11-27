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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.UrlTargetValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authorizes the {@code redirect_after_login} URL of the OAuth 1.0 and OAuth 2.0 flows.
 *
 * <p>Both flows end their callback with a redirect to the URL carried by the {@code
 * redirect_after_login} parameter of the OAuth {@code state}. The {@code state} is chosen by
 * whoever builds the authorization URL, so that URL is untrusted input: without this check anyone
 * could hand a victim a Che authorization URL that sends the victim's browser on to a site of their
 * choosing, from the Che host and with whatever the callback appends to the query string (CWE-601).
 *
 * <p>A redirect is allowed when it stays on the Che host: either a relative URL, or an absolute URL
 * addressing the same origin as {@code che.api}. The dashboard and the {@code /_app} OAuth loader,
 * which are what asks for a redirect, are both served from that host.
 *
 * <p>The URL is taken as a browser would take it, not as {@link URI} would: characters a browser
 * sends on but {@link URI} rejects, such as the braces and quotes of {@code
 * /dashboard/w?params={"namespace":"ns"}}, are percent encoded before it is parsed rather than
 * refused. The encoding only fills in what the caller left out, so an already encoded URL is
 * unchanged, and it cannot make a URL pass the check that its raw form would not: it never
 * introduces a character that delimits the scheme, the authority or the path.
 *
 * <p>The check is turned off along with the other checks on user supplied URLs by the {@value
 * UrlTargetValidator#URL_DESTINATION_CHECK} environment variable, so that one switch restores the
 * behaviour a deployment had before these checks existed. The URL still has to be present and
 * parseable, as the redirect is built from it.
 */
@Singleton
public class RedirectAfterLoginUrlValidator {
  private static final Logger LOG = LoggerFactory.getLogger(RedirectAfterLoginUrlValidator.class);

  /**
   * A run of the characters {@link URI} rejects: anything that is neither a letter, a digit, an
   * unreserved mark nor one of the delimiters a URL is built of, plus a percent that starts no
   * escape. Encoding only these can never add a scheme, an authority or a path segment.
   */
  private static final Pattern ILLEGAL =
      Pattern.compile("(?:[^A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]|%(?![0-9A-Fa-f]{2}))+");

  /**
   * The origin of {@code che.api}, as {@code scheme://host:port}, or {@code null} if it names no
   * host, in which case only a relative redirect is allowed.
   */
  @Nullable private final String apiOrigin;

  @Inject
  public RedirectAfterLoginUrlValidator(@Named("che.api") String apiEndpoint) {
    this.apiOrigin = originOf(apiEndpoint);
    LOG.debug("A redirect after login may address {} or the Che host it is relative to", apiOrigin);
  }

  /**
   * Parses the given redirect after login URL, percent encoding the characters {@link URI} does not
   * accept, and fails unless it stays on the Che host.
   *
   * @param redirectAfterLogin the URL to redirect the browser to, as it will be redirected to
   * @return the parsed URL, to be handed to the redirect as is
   * @throws ForbiddenException if the URL is unparseable, or addresses another host while the
   *     {@value UrlTargetValidator#URL_DESTINATION_CHECK} check is on
   */
  public URI authorize(String redirectAfterLogin) throws ForbiddenException {
    if (redirectAfterLogin == null || redirectAfterLogin.trim().isEmpty()) {
      throw refuse("the URL is missing", redirectAfterLogin);
    }
    // A backslash is a path separator to a browser, so both '/\example.com' and '\\example.com'
    // leave the Che host. It is refused rather than encoded, as it was before: the '%5C' it would
    // encode to is a path character to a browser, so the redirect would go nowhere anyone asked
    // for, and the reason reads better than a parse failure.
    if (redirectAfterLogin.indexOf('\\') >= 0) {
      throw refuse("the URL contains a backslash", redirectAfterLogin);
    }
    URI uri;
    try {
      uri = new URI(encodeIllegalCharacters(redirectAfterLogin));
    } catch (URISyntaxException e) {
      throw refuse("the URL is not parseable: " + e.getMessage(), redirectAfterLogin);
    }
    authorize(uri);
    return uri;
  }

  /**
   * Fails unless the given redirect after login URL stays on the Che host. Does nothing when the
   * {@value UrlTargetValidator#URL_DESTINATION_CHECK} check is turned off.
   *
   * @param redirectAfterLogin the URI the browser will be redirected to
   * @throws ForbiddenException if the URI addresses another host
   */
  public void authorize(URI redirectAfterLogin) throws ForbiddenException {
    if (!UrlTargetValidator.isCheckEnabled()) {
      return;
    }
    if (!redirectAfterLogin.isAbsolute()) {
      // A relative URL is resolved against the Che host by the browser. A protocol relative URL
      // '//example.com/path' is not relative to the host but to the scheme, and java.net.URI reads
      // its host as the authority of a relative reference, so it must not pass as one.
      if (redirectAfterLogin.getAuthority() != null || redirectAfterLogin.getHost() != null) {
        throw refuse("the URL is protocol relative", redirectAfterLogin.toString());
      }
      return;
    }
    // An opaque URI is anything that is not hierarchical, e.g. 'javascript:alert(1)'.
    if (redirectAfterLogin.isOpaque() || redirectAfterLogin.getHost() == null) {
      throw refuse("the URL has no host", redirectAfterLogin.toString());
    }
    // The host a browser navigates to is the one after the '@'.
    if (redirectAfterLogin.getUserInfo() != null) {
      throw refuse("the URL carries user information", redirectAfterLogin.toString());
    }
    if (!originOf(redirectAfterLogin).equals(apiOrigin)) {
      throw refuse("the URL does not address the Che host", redirectAfterLogin.toString());
    }
  }

  /**
   * The URL is chosen by whoever built the authorization URL, so it is logged at debug level only,
   * and kept out of the message returned to the caller.
   */
  private ForbiddenException refuse(String reason, @Nullable String redirectAfterLogin) {
    LOG.warn("Blocked a redirect after login: {}. The Che host is {}", reason, apiOrigin);
    LOG.debug("Blocked a redirect after login to '{}'", redirectAfterLogin);
    return new ForbiddenException("The redirect after login URL is missing or not allowed");
  }

  /**
   * Returns the URL with every character {@link URI} rejects, such as a brace, a quote or a space,
   * replaced by the percent encoding of its UTF-8 bytes. A percent that already starts an encoded
   * octet is left alone, so encoding an encoded URL returns it unchanged.
   */
  private static String encodeIllegalCharacters(String url) {
    // URLEncoder is form encoding, which writes a space as '+'; in a URL it is '%20'.
    return ILLEGAL
        .matcher(url)
        .replaceAll(illegal -> URLEncoder.encode(illegal.group(), UTF_8).replace("+", "%20"));
  }

  /** Returns the origin of {@code che.api}, or {@code null} if it names no host. */
  @Nullable
  private static String originOf(String apiEndpoint) {
    if (isNullOrEmpty(apiEndpoint)) {
      LOG.error("`che.api` is not set, so only a relative redirect after login is allowed");
      return null;
    }
    try {
      URI uri = new URI(apiEndpoint.trim());
      if (!uri.isAbsolute() || uri.getHost() == null) {
        LOG.error(
            "`che.api` ('{}') is not an absolute URL, so only a relative redirect after login is"
                + " allowed",
            apiEndpoint);
        return null;
      }
      return originOf(uri);
    } catch (URISyntaxException e) {
      LOG.error(
          "`che.api` ('{}') is not parseable, so only a relative redirect after login is allowed:"
              + " {}",
          apiEndpoint,
          e.getMessage());
      return null;
    }
  }

  /** Returns the origin of the URI as {@code scheme://host:port}, with the port never implicit. */
  private static String originOf(URI uri) {
    return uri.getScheme().toLowerCase(Locale.ROOT)
        + "://"
        + uri.getHost().toLowerCase(Locale.ROOT)
        + ":"
        + effectivePort(uri);
  }

  /** Returns the port of the URI, substituting the default port of its scheme when unset. */
  private static int effectivePort(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }
}
