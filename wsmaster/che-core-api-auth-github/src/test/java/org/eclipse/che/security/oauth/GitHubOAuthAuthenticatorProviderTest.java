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
package org.eclipse.che.security.oauth;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class GitHubOAuthAuthenticatorProviderTest {
  private static final String TEST_URI = "https://api.github.com";
  private File credentialFile;
  private File emptyFile;

  @BeforeClass
  public void setup() throws IOException {
    credentialFile = File.createTempFile("GitHubOAuthAuthenticatorProviderTest-", "-credentials");
    Files.asCharSink(credentialFile, Charset.defaultCharset()).write("id/secret");
    credentialFile.deleteOnExit();
    emptyFile = File.createTempFile("GitHubOAuthAuthenticatorProviderTest-", "-empty");
    emptyFile.deleteOnExit();
  }

  @Test(dataProvider = "noopConfig")
  public void shouldProvideNoopAuthenticatorWhenInvalidConfigurationSet(
      String[] redirectUris, String authUri, String tokenUri) throws IOException {
    // given
    GitHubOAuthAuthenticatorProvider provider =
        new GitHubOAuthAuthenticatorProvider(redirectUris, authUri, tokenUri);
    // when
    OAuthAuthenticator authenticator = provider.get();
    // then
    assertNotNull(authenticator);
    assertTrue(
        GitHubOAuthAuthenticatorProvider.NoopOAuthAuthenticator.class.isAssignableFrom(
            authenticator.getClass()));
  }

  @Test
  public void shouldProvideNoopAuthenticatorWhenConfigFilesAreEmpty() throws IOException {
    // given
    GitHubOAuthAuthenticatorProvider provider =
        new GitHubOAuthAuthenticatorProvider(null, null, null);
    // when
    OAuthAuthenticator authenticator =
        provider.getOAuthAuthenticator(
            new String[] {TEST_URI}, TEST_URI, TEST_URI, emptyFile.getPath(), emptyFile.getPath());
    // then
    assertNotNull(authenticator);
    assertTrue(
        GitHubOAuthAuthenticatorProvider.NoopOAuthAuthenticator.class.isAssignableFrom(
            authenticator.getClass()));
  }

  @Test
  public void shouldProvideValidGitHubOAuthAuthenticator() throws IOException {
    // given
    GitHubOAuthAuthenticatorProvider provider =
        new GitHubOAuthAuthenticatorProvider(null, null, null);
    // when
    OAuthAuthenticator authenticator =
        provider.getOAuthAuthenticator(
            new String[] {TEST_URI},
            TEST_URI,
            TEST_URI,
            credentialFile.getPath(),
            credentialFile.getPath());
    // then
    assertNotNull(authenticator);
    assertTrue(GitHubOAuthAuthenticator.class.isAssignableFrom(authenticator.getClass()));
  }

  @DataProvider(name = "noopConfig")
  public Object[][] noopConfig() {
    return new Object[][] {
      {null, null, null},
      {null, TEST_URI, null},
      {null, null, TEST_URI},
      {null, TEST_URI, TEST_URI},
      {new String[] {}, null, null},
      {new String[] {}, "", ""},
      {new String[] {}, "", null},
      {new String[] {}, null, ""},
      {new String[] {}, TEST_URI, null},
      {new String[] {}, TEST_URI, TEST_URI},
      {new String[] {TEST_URI}, null, null},
      {new String[] {TEST_URI}, "", ""},
      {new String[] {TEST_URI}, null, TEST_URI},
      {new String[] {TEST_URI}, TEST_URI, null},
      {new String[] {TEST_URI}, TEST_URI, ""},
      {new String[] {TEST_URI}, "", TEST_URI}
    };
  }
}
