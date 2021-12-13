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

public class GitLabOAuthAuthenticatorProviderTest {
  private static final String TEST_URI = "https://gitlab.com";
  private File credentialFile;
  private File emptyFile;

  @BeforeClass
  public void setup() throws IOException {
    credentialFile = File.createTempFile("GitLabOAuthAuthenticatorProviderTest-", "-credentials");
    Files.asCharSink(credentialFile, Charset.defaultCharset()).write("id/secret");
    credentialFile.deleteOnExit();
    emptyFile = File.createTempFile("GitLabOAuthAuthenticatorProviderTest-", "-empty");
    emptyFile.deleteOnExit();
  }

  @Test(dataProvider = "noopConfig")
  public void shouldProvideNoopAuthenticatorWhenInvalidConfigurationSet(
      String gitHubClientIdPath,
      String gitHubClientSecretPath,
      String gitlabEndpoint,
      String[] redirectUris)
      throws IOException {
    // given
    GitLabOAuthAuthenticatorProvider provider =
        new GitLabOAuthAuthenticatorProvider(
            gitHubClientIdPath, gitHubClientSecretPath, gitlabEndpoint, redirectUris);
    // when
    OAuthAuthenticator authenticator = provider.get();
    // then
    assertNotNull(authenticator);
    assertTrue(
        GitLabOAuthAuthenticatorProvider.NoopOAuthAuthenticator.class.isAssignableFrom(
            authenticator.getClass()));
  }

  @Test
  public void shouldProvideNoopAuthenticatorWhenConfigFilesAreEmpty() throws IOException {
    // given
    GitLabOAuthAuthenticatorProvider provider =
        new GitLabOAuthAuthenticatorProvider(
            emptyFile.getPath(), emptyFile.getPath(), TEST_URI, new String[] {TEST_URI});
    // when
    OAuthAuthenticator authenticator = provider.get();
    // then
    assertNotNull(authenticator);
    assertTrue(
        GitLabOAuthAuthenticatorProvider.NoopOAuthAuthenticator.class.isAssignableFrom(
            authenticator.getClass()));
  }

  @Test
  public void shouldProvideValidGitLabOAuthAuthenticator() throws IOException {
    // given
    GitLabOAuthAuthenticatorProvider provider =
        new GitLabOAuthAuthenticatorProvider(
            credentialFile.getPath(), credentialFile.getPath(), TEST_URI, new String[] {TEST_URI});
    // when
    OAuthAuthenticator authenticator = provider.get();

    // then
    assertNotNull(authenticator);
    assertTrue(GitLabOAuthAuthenticator.class.isAssignableFrom(authenticator.getClass()));
  }

  @DataProvider(name = "noopConfig")
  public Object[][] noopConfig() {
    return new Object[][] {
      {null, null, null, null},
      {emptyFile.getPath(), emptyFile.getPath(), null, null},
      {credentialFile.getPath(), credentialFile.getPath(), null, null},
      {null, null, TEST_URI, new String[] {TEST_URI}},
      {"", "", TEST_URI, new String[] {TEST_URI}},
      {emptyFile.getPath(), "", TEST_URI, new String[] {TEST_URI}},
      {"", emptyFile.getPath(), TEST_URI, new String[] {TEST_URI}},
      {null, emptyFile.getPath(), TEST_URI, new String[] {TEST_URI}},
      {emptyFile.getPath(), null, TEST_URI, new String[] {TEST_URI}},
      {credentialFile.getPath(), null, TEST_URI, new String[] {TEST_URI}},
      {null, credentialFile.getPath(), TEST_URI, new String[] {TEST_URI}},
      {credentialFile.getPath(), "", TEST_URI, new String[] {TEST_URI}},
      {"", credentialFile.getPath(), TEST_URI, new String[] {TEST_URI}},
      {credentialFile.getPath(), null, null, new String[] {TEST_URI}},
      {credentialFile.getPath(), credentialFile.getPath(), "", new String[] {TEST_URI}},
      {credentialFile.getPath(), credentialFile.getPath(), TEST_URI, new String[] {}},
      {credentialFile.getPath(), credentialFile.getPath(), TEST_URI, null},
      {credentialFile.getPath(), credentialFile.getPath(), null, new String[] {TEST_URI}},
      {credentialFile.getPath(), emptyFile.getPath(), TEST_URI, new String[] {TEST_URI}},
      {emptyFile.getPath(), credentialFile.getPath(), TEST_URI, new String[] {TEST_URI}},
    };
  }
}
