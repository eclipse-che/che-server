/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.security;

import static java.util.Collections.emptyList;
import static org.testng.Assert.assertTrue;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import org.eclipse.che.security.oauth.BitbucketOAuthAuthenticatorProvider;
import org.eclipse.che.security.oauth.OAuthAuthenticator;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class BitbucketOAuthAuthenticatorProviderTest {
  private BitbucketOAuthAuthenticatorProvider provider;
  private File cfgFile;

  @BeforeClass
  public void setup() throws IOException {
    cfgFile = File.createTempFile("BitbucketOAuthAuthenticatorProviderTest-", "-cfg");
    Files.asCharSink(cfgFile, Charset.defaultCharset()).write("tmp-data");
    cfgFile.deleteOnExit();
    provider =
        new BitbucketOAuthAuthenticatorProvider(
            "http://bitubucket-server.com",
            cfgFile.getPath(),
            cfgFile.getPath(),
            new String[] {"http://che.server.com"},
            "http://auth.uri",
            "http://token.uri");
  }

  @Test
  public void shouldReturnAuthenticationUrlEncodedOnce() throws Exception {
    // given
    BitbucketOAuthAuthenticatorProvider provider =
        new BitbucketOAuthAuthenticatorProvider(
            "https://bitbucket.org",
            cfgFile.getPath(),
            cfgFile.getPath(),
            new String[] {"http://che.server.com"},
            "http://auth.uri",
            "http://token.uri");
    OAuthAuthenticator authenticator = provider.get();
    URL url = new URL("http://che.server.com?query=param");
    // when
    String authenticateUrl = authenticator.getAuthenticateUrl(url, emptyList());
    // then
    assertTrue(authenticateUrl.endsWith("&state=query%253Dparam"));
  }

  @Test
  public void shouldReturnAuthenticationUrlEncodedTwice() throws Exception {
    // given
    OAuthAuthenticator authenticator = provider.get();
    URL url = new URL("http://che.server.com?query=param");
    // when
    String authenticateUrl = authenticator.getAuthenticateUrl(url, emptyList());
    // then
    assertTrue(authenticateUrl.endsWith("&state=query%25253Dparam"));
  }
}
