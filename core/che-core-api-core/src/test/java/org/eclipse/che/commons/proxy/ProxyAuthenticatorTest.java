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
package org.eclipse.che.commons.proxy;

import static org.testng.Assert.assertEquals;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author Dmytro Nochevnov */
public class ProxyAuthenticatorTest {

  public static final String HTTP_URL = "http://some.address";
  public static final String HTTPS_URL = "https://some.address";

  @BeforeClass
  public void setup() {
    System.setProperty("http.proxyUser", "user1");
    System.setProperty("http.proxyPassword", "paswd1");
    System.setProperty("https.proxyUser", "user2");
    System.setProperty("https.proxyPassword", "paswd2");
  }

  @Test
  public void shouldInitHttpsProxyAuthenticator() throws Exception {
    // when
    ProxyAuthenticator.initAuthenticator(HTTPS_URL);
    PasswordAuthentication testAuthentication =
        Authenticator.requestPasswordAuthentication(null, 0, null, null, null);

    // then
    assertEquals(testAuthentication.getUserName(), "user2");
    assertEquals(String.valueOf(testAuthentication.getPassword()), "paswd2");

    // when
    ProxyAuthenticator.resetAuthenticator();

    // then
    testAuthentication = Authenticator.requestPasswordAuthentication(null, 0, null, null, null);
    assertEquals(testAuthentication, null);
  }

  @Test
  public void shouldInitHttpProxyAuthenticator() throws Exception {
    // when
    ProxyAuthenticator.initAuthenticator(HTTP_URL);

    // then
    PasswordAuthentication testAuthentication =
        Authenticator.requestPasswordAuthentication(null, 0, null, null, null);
    assertEquals(testAuthentication.getUserName(), "user1");
    assertEquals(String.valueOf(testAuthentication.getPassword()), "paswd1");

    // when
    ProxyAuthenticator.resetAuthenticator();

    // then
    testAuthentication = Authenticator.requestPasswordAuthentication(null, 0, null, null, null);
    assertEquals(testAuthentication, null);
  }

  @AfterClass
  public void tearDown() {
    System.clearProperty("http.proxyUser");
    System.clearProperty("http.proxyPassword");
    System.clearProperty("https.proxyUser");
    System.clearProperty("https.proxyPassword");
  }
}
