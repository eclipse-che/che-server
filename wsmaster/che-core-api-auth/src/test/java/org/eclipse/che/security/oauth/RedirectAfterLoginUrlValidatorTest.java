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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.net.URI;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.lang.UrlTargetValidator;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RedirectAfterLoginUrlValidatorTest {

  private static final String CHE_API = "https://che.example.com/api";

  private final RedirectAfterLoginUrlValidator validator =
      new RedirectAfterLoginUrlValidator(CHE_API);

  @AfterMethod
  public void restoreUrlChecks() {
    UrlTargetValidator.setCheckEnabled(null);
  }

  @Test(dataProvider = "allowedUrls")
  public void shouldAllowAUrlThatStaysOnTheCheHost(String url) throws Exception {
    assertEquals(validator.authorize(url), URI.create(url));
  }

  @DataProvider
  public Object[][] allowedUrls() {
    return new Object[][] {
      {"https://che.example.com/dashboard/"},
      {"https://che.example.com:443/dashboard/#/?params=%7B%7D"},
      // the host of a URL is case insensitive
      {"https://CHE.EXAMPLE.COM/dashboard/"},
      {"HTTPS://che.example.com/dashboard/"},
      // a relative URL is resolved against the Che host by the browser
      {"/dashboard"},
      {"/dashboard/#/load-factory?url=https://github.com/eclipse-che/che-server"},
      {"dashboard"},
    };
  }

  @Test(dataProvider = "refusedUrls")
  public void shouldRefuseAUrlThatLeavesTheCheHost(String url) {
    try {
      validator.authorize(url);
      fail("Expected the redirect to '" + url + "' to be refused");
    } catch (ForbiddenException e) {
      assertEquals(e.getMessage(), "The redirect after login URL is missing or not allowed");
    }
  }

  @DataProvider
  public Object[][] refusedUrls() {
    return new Object[][] {
      {"https://attacker.example.com/"},
      // a host that only looks like the Che host
      {"https://che.example.com.attacker.example.com/"},
      {"https://attacker.example.com/che.example.com"},
      // the origin is the scheme, the host and the port, all three of them
      {"http://che.example.com/dashboard/"},
      {"https://che.example.com:8443/dashboard/"},
      // the host a browser navigates to is the one after the '@'
      {"https://che.example.com@attacker.example.com/"},
      // protocol relative: not relative to the host but to the scheme
      {"//attacker.example.com/"},
      // a backslash is a path separator to a browser, but not to java.net.URI
      {"/\\attacker.example.com/"},
      {"\\\\attacker.example.com/"},
      {"/dashboard\\..\\..\\"},
      // not a hierarchical URL at all
      {"javascript:alert(1)"},
      {"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg=="},
      {"mailto:someone@attacker.example.com"},
      {null},
      {""},
      {" "},
      {"https://che.example.com/dash board"},
    };
  }

  @Test
  public void shouldTakeThePortOfTheApiEndpointIntoAccount() throws Exception {
    RedirectAfterLoginUrlValidator validator =
        new RedirectAfterLoginUrlValidator("http://localhost:8080/api");

    assertEquals(
        validator.authorize("http://localhost:8080/_app/loader.html?status=ready"),
        URI.create("http://localhost:8080/_app/loader.html?status=ready"));
    try {
      validator.authorize("http://localhost:9090/");
      fail("Expected the redirect to another port to be refused");
    } catch (ForbiddenException e) {
      // expected
    }
  }

  /** `che.api` names the host a redirect may address, so without it only a relative one can. */
  @Test
  public void shouldOnlyAllowARelativeUrlWhenTheApiEndpointIsUnusable() throws Exception {
    for (String apiEndpoint : new String[] {null, "", "/api", ":://not a url"}) {
      RedirectAfterLoginUrlValidator validator = new RedirectAfterLoginUrlValidator(apiEndpoint);

      assertEquals(validator.authorize("/dashboard"), URI.create("/dashboard"));
      try {
        validator.authorize("https://che.example.com/dashboard/");
        fail("Expected the redirect to be refused for `che.api` '" + apiEndpoint + "'");
      } catch (ForbiddenException e) {
        // expected
      }
    }
  }

  /** The OAuth 1.0 callback hands the URI it builds, rather than the parsed URL, to the check. */
  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRefuseAForeignUri() throws Exception {
    validator.authorize(URI.create("https://attacker.example.com/?error_code=access_denied"));
  }

  /** URL_DESTINATION_CHECK turns off every check on a user supplied URL, this one included. */
  @Test
  public void shouldAllowAnyUrlWhenTheCheckIsTurnedOff() throws Exception {
    UrlTargetValidator.setCheckEnabled(false);

    assertEquals(
        validator.authorize("https://attacker.example.com/"),
        URI.create("https://attacker.example.com/"));
    assertEquals(
        validator.authorize("//attacker.example.com/"), URI.create("//attacker.example.com/"));
    validator.authorize(URI.create("https://attacker.example.com/?error_code=access_denied"));
  }

  /** The redirect is built from the URL, so it has to be there and to parse whatever the switch. */
  @Test
  public void shouldStillRefuseAnUnusableUrlWhenTheCheckIsTurnedOff() {
    UrlTargetValidator.setCheckEnabled(false);

    for (String url : new String[] {null, "", "https://che.example.com/dash board"}) {
      try {
        validator.authorize(url);
        fail("Expected the redirect to '" + url + "' to be refused");
      } catch (ForbiddenException e) {
        assertEquals(e.getMessage(), "The redirect after login URL is missing or not allowed");
      }
    }
  }
}
