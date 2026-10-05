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
package org.eclipse.che.commons.lang;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks that a URL derived from user input points at a target the server is allowed to reach.
 *
 * <p>Several endpoints take a URL from the user and make the server fetch it. Without a check on
 * the destination, such a URL can be aimed at a service that is only reachable from inside the
 * cluster - the cloud metadata endpoint, the Kubernetes API, a neighbouring pod - turning the
 * server into a proxy for the caller (SSRF).
 *
 * <p>The check can be turned off with the {@value #URL_DESTINATION_CHECK} environment variable, for
 * deployments whose SCM server is only reachable on an internal address and would therefore be
 * rejected. The same variable is read by the callers that restrict which URLs a devfile may refer
 * to and which hosts the user's credentials are sent to, so that one switch restores the behaviour
 * these deployments had before the checks existed. See {@link #isCheckEnabled()}.
 */
public final class UrlTargetValidator {

  private static final Logger LOG = LoggerFactory.getLogger(UrlTargetValidator.class);

  /**
   * Name of the environment variable that switches the destination check on and off. Set it to
   * {@code true} (also accepted: {@code 1}, {@code yes} and {@code on}) to check every destination,
   * or to {@code false} ({@code 0}, {@code no}, {@code off}) to let the server request any host.
   * The check is on when the variable is unset.
   */
  public static final String URL_DESTINATION_CHECK = "URL_DESTINATION_CHECK";

  /** Resolved value of {@link #URL_DESTINATION_CHECK}, {@code null} until it is first read. */
  private static volatile Boolean checkEnabled;

  private UrlTargetValidator() {}

  /**
   * Tells whether the destination check is on, that is not turned off by the {@value
   * #URL_DESTINATION_CHECK} environment variable. When it is off, every URL is accepted.
   *
   * <p>The variable is read once and remembered, as the environment cannot change while the server
   * runs.
   *
   * @return true if the check is enabled
   */
  public static boolean isCheckEnabled() {
    Boolean enabled = checkEnabled;
    if (enabled == null) {
      enabled = isCheckEnabledBy(System.getenv(URL_DESTINATION_CHECK));
      checkEnabled = enabled;
      if (!enabled) {
        LOG.warn(
            "{} is off: URLs supplied by users are fetched without checking their destination,"
                + " so the server can be made to request hosts only reachable from inside the"
                + " cluster (SSRF).",
            URL_DESTINATION_CHECK);
      }
    }
    return enabled;
  }

  /**
   * Tells whether the given value of {@value #URL_DESTINATION_CHECK} turns the destination check
   * on. An unset or empty variable leaves it on, as does a value that is not recognised: a
   * deployment has to explicitly ask for the check to be turned off, and a typo in that request
   * must not be what turns it off.
   */
  @VisibleForTesting
  static boolean isCheckEnabledBy(String value) {
    if (isNullOrEmpty(value)) {
      return true;
    }
    switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "true":
      case "1":
      case "yes":
      case "on":
        return true;
      case "false":
      case "0":
      case "no":
      case "off":
        return false;
      default:
        return true;
    }
  }

  /**
   * Forces the resolved value of {@value #URL_DESTINATION_CHECK}, or restores it with {@code null}
   * so that the environment is read again. Only meant for tests, which cannot set an environment
   * variable, of the checks in other modules that this switch also turns off.
   */
  @VisibleForTesting
  public static void setCheckEnabled(Boolean enabled) {
    checkEnabled = enabled;
  }

  /**
   * Throws if the given URL may not be requested by the server, either because of its scheme or
   * because its host resolves to an address that is not publicly routable. Does nothing when the
   * check is {@link #isCheckEnabled() turned off}.
   *
   * @param url the URL about to be requested
   * @throws IOException if the URL is malformed or its target is not allowed
   */
  public static void validate(String url) throws IOException {
    if (!isCheckEnabled()) {
      return;
    }

    // the scheme is read off the raw string: an opaque URL such as jar:file:/x!/y is rejected here
    // rather than reported as a parsing failure
    int schemeEnd = url == null ? -1 : url.indexOf(':');
    String scheme = schemeEnd > 0 ? url.substring(0, schemeEnd) : null;
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new IOException(
          "Only http and https URLs are allowed, got: " + scheme + " in URL " + url);
    }

    final URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new IOException("Invalid URL " + url, e);
    }

    String host = uri.getHost();
    if (isNullOrEmpty(host)) {
      throw new IOException("URL host is missing in " + url);
    }

    final InetAddress[] addresses;
    try {
      // all records are checked, so that a host publishing both a public and an internal address
      // cannot pass the check and then be connected to on the internal one
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      throw new IOException("Unable to resolve URL host " + host + " in " + url, e);
    }

    for (InetAddress address : addresses) {
      if (!isPubliclyRoutable(address)) {
        throw new IOException("URL host is not allowed: " + host);
      }
    }
  }

  /**
   * Same check as {@link #validate(String)}, in a form usable where a URL is probed on a best
   * effort basis and a disallowed target simply means "not a match".
   *
   * @param url the URL about to be requested
   * @return true if the server may request the URL
   */
  public static boolean isAllowed(String url) {
    try {
      validate(url);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Tells whether an address belongs to the public internet, as opposed to the ranges reserved for
   * private networks, the host itself, or protocol machinery.
   */
  private static boolean isPubliclyRoutable(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        // IPv4 private ranges and the deprecated IPv6 site-local fec0::/10
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return false;
    }

    byte[] bytes = address.getAddress();
    if (bytes.length == 4) {
      int first = bytes[0] & 0xFF;
      int second = bytes[1] & 0xFF;
      int third = bytes[2] & 0xFF;
      // 0.0.0.0/8 "this network" (RFC 791), of which isAnyLocalAddress only covers 0.0.0.0 itself
      if (first == 0) {
        return false;
      }
      // 100.64.0.0/10 shared address space (RFC 6598), used by several CNI plugins
      if (first == 100 && second >= 64 && second <= 127) {
        return false;
      }
      // 192.0.0.0/24 IETF protocol assignments (RFC 6890)
      // and 192.0.2.0/24 TEST-NET-1 (RFC 5737)
      if (first == 192 && second == 0 && (third == 0 || third == 2)) {
        return false;
      }
      // 198.18.0.0/15 benchmarking (RFC 2544)
      if (first == 198 && (second == 18 || second == 19)) {
        return false;
      }
      // 198.51.100.0/24 TEST-NET-2 (RFC 5737)
      if (first == 198 && second == 51 && third == 100) {
        return false;
      }
      // 203.0.113.0/24 TEST-NET-3 (RFC 5737)
      if (first == 203 && second == 0 && third == 113) {
        return false;
      }
      // 240.0.0.0/4 reserved, including the 255.255.255.255 broadcast address
      if (first >= 240) {
        return false;
      }
    } else if (bytes.length == 16) {
      // fc00::/7 unique local addresses (RFC 4193), which isSiteLocalAddress does not cover
      if ((bytes[0] & 0xFE) == 0xFC) {
        return false;
      }
    }
    return true;
  }
}
