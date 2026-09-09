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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests of {@link UrlTargetValidator}. */
public class UrlTargetValidatorTest {

  @DataProvider
  public Object[][] disallowedUrls() {
    return new Object[][] {
      // schemes that are not a web request at all
      {"file:///etc/passwd"},
      {"ftp://example.com/file"},
      {"jar:file:///tmp/evil.jar!/payload"},
      {"gopher://example.com/"},
      {"no-scheme-at-all"},
      // the host itself
      {"http://127.0.0.1/"},
      {"http://[::1]/"},
      {"http://0/"},
      {"http://[::ffff:127.0.0.1]/"},
      // cloud metadata, reachable on the link-local range
      {"http://169.254.169.254/latest/meta-data/"},
      {"http://[fe80::1]/"},
      {"http://[0:0:0:0:0:ffff:a9fe:a9fe]/"},
      // IPv4 private ranges
      {"http://10.0.0.1/"},
      {"http://172.16.0.1/"},
      {"http://192.168.1.1/"},
      // IPv6 unique local addresses, which InetAddress#isSiteLocalAddress does not cover
      {"http://[fc00::1]/"},
      {"http://[fd12:3456:789a::1]/"},
      // ranges that are not the public internet either
      {"http://100.64.1.1/"},
      {"http://192.0.0.1/"},
      {"http://198.18.0.1/"},
      {"http://240.0.0.1/"},
      {"http://255.255.255.255/"},
      // no host to check
      {"http:///path"},
    };
  }

  @Test(dataProvider = "disallowedUrls")
  public void shouldRejectUrl(String url) {
    assertFalse(UrlTargetValidator.isAllowed(url), url + " should not be reachable");
  }

  @DataProvider
  public Object[][] allowedUrls() {
    return new Object[][] {
      {"https://93.184.216.34/devfile.yaml"},
      {"http://8.8.8.8/"},
      {"https://[2001:4860:4860::8888]/"},
    };
  }

  @Test(dataProvider = "allowedUrls")
  public void shouldAllowUrl(String url) {
    assertTrue(UrlTargetValidator.isAllowed(url), url + " should be reachable");
  }
}
