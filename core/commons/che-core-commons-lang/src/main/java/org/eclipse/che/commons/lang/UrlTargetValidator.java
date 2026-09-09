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

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Checks that a URL derived from user input points at a target the server is allowed to reach.
 *
 * <p>Several endpoints take a URL from the user and make the server fetch it. Without a check on
 * the destination, such a URL can be aimed at a service that is only reachable from inside the
 * cluster - the cloud metadata endpoint, the Kubernetes API, a neighbouring pod - turning the
 * server into a proxy for the caller (SSRF).
 */
public final class UrlTargetValidator {

  private UrlTargetValidator() {}

  /**
   * Throws if the given URL may not be requested by the server, either because of its scheme or
   * because its host resolves to an address that is not publicly routable.
   *
   * @param url the URL about to be requested
   * @throws IOException if the URL is malformed or its target is not allowed
   */
  public static void validate(String url) throws IOException {
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
      // 100.64.0.0/10 shared address space (RFC 6598), used by several CNI plugins
      if (first == 100 && second >= 64 && second <= 127) {
        return false;
      }
      // 192.0.0.0/24 IETF protocol assignments (RFC 6890)
      if (first == 192 && second == 0 && (bytes[2] & 0xFF) == 0) {
        return false;
      }
      // 198.18.0.0/15 benchmarking (RFC 2544)
      if (first == 198 && (second == 18 || second == 19)) {
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
