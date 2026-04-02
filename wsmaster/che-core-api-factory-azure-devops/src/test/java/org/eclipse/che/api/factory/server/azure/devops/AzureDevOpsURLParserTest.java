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
package org.eclipse.che.api.factory.server.azure.devops;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * @author Anatalii Bazko
 */
@Listeners(MockitoTestNGListener.class)
public class AzureDevOpsURLParserTest {

  private AzureDevOpsURLParser azureDevOpsURLParser;

  @BeforeMethod
  protected void start() {
    azureDevOpsURLParser =
        new AzureDevOpsURLParser(
            mock(DevfileFilenamesProvider.class),
            mock(PersonalAccessTokenManager.class),
            "https://dev.azure.com/");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testParseInvalidUrl() {
    azureDevOpsURLParser.parse("http://www.eclipse.org", null);
  }

  @Test
  public void shouldParseWithBranch() {
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse("https://dev.azure.com/MyOrg/MyProject/_git/MyRepo", "branch");
    assertEquals(azureDevOpsUrl.getBranch(), "branch");
  }

  @Test
  public void shouldParseWithUrlBranch() {
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse(
            "https://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo?version=GBmain", "branch");
    assertEquals(azureDevOpsUrl.getBranch(), "main");
  }

  @Test
  public void shouldParseServerUrlWithIpv6Host() {
    // given
    azureDevOpsURLParser =
        new AzureDevOpsURLParser(
            mock(DevfileFilenamesProvider.class),
            mock(PersonalAccessTokenManager.class),
            "https://[2001:db8::1]/");

    // when
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse("https://[2001:db8::1]/MyOrg/MyProject/_git/MyRepo", null);

    // then
    assertEquals(azureDevOpsUrl.getOrganization(), "MyOrg");
    assertEquals(azureDevOpsUrl.getProject(), "MyProject");
    assertEquals(azureDevOpsUrl.getRepository(), "MyRepo");
  }

  @Test
  public void shouldParseIpv6UrlWithBranch() {
    // given
    azureDevOpsURLParser =
        new AzureDevOpsURLParser(
            mock(DevfileFilenamesProvider.class),
            mock(PersonalAccessTokenManager.class),
            "https://[2001:db8::1]/");

    // when
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse(
            "https://[2001:db8::1]/MyOrg/MyProject/_git/MyRepo?version=GBfeature-branch", null);

    // then
    assertEquals(azureDevOpsUrl.getOrganization(), "MyOrg");
    assertEquals(azureDevOpsUrl.getProject(), "MyProject");
    assertEquals(azureDevOpsUrl.getRepository(), "MyRepo");
    assertEquals(azureDevOpsUrl.getBranch(), "feature-branch");
  }

  @Test
  public void shouldParseIpv6UrlWithTag() {
    // given
    azureDevOpsURLParser =
        new AzureDevOpsURLParser(
            mock(DevfileFilenamesProvider.class),
            mock(PersonalAccessTokenManager.class),
            "https://[2001:db8::1]/");

    // when
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse(
            "https://[2001:db8::1]/MyOrg/MyProject/_git/MyRepo?version=GTv1.0", null);

    // then
    assertEquals(azureDevOpsUrl.getOrganization(), "MyOrg");
    assertEquals(azureDevOpsUrl.getProject(), "MyProject");
    assertEquals(azureDevOpsUrl.getRepository(), "MyRepo");
    assertEquals(azureDevOpsUrl.getTag(), "v1.0");
  }

  @Test
  public void shouldParseIpv6UrlWithRevisionParam() {
    // given
    azureDevOpsURLParser =
        new AzureDevOpsURLParser(
            mock(DevfileFilenamesProvider.class),
            mock(PersonalAccessTokenManager.class),
            "https://[2001:db8::1]/");

    // when
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse(
            "https://[2001:db8::1]/MyOrg/MyProject/_git/MyRepo", "my-branch");

    // then
    assertEquals(azureDevOpsUrl.getOrganization(), "MyOrg");
    assertEquals(azureDevOpsUrl.getProject(), "MyProject");
    assertEquals(azureDevOpsUrl.getRepository(), "MyRepo");
    assertEquals(azureDevOpsUrl.getBranch(), "my-branch");
  }

  @Test
  public void shouldParseIpv6LoopbackAddress() {
    // given
    azureDevOpsURLParser =
        new AzureDevOpsURLParser(
            mock(DevfileFilenamesProvider.class),
            mock(PersonalAccessTokenManager.class),
            "https://[::1]/");

    // when
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse("https://[::1]/MyOrg/MyProject/_git/MyRepo", null);

    // then
    assertEquals(azureDevOpsUrl.getOrganization(), "MyOrg");
    assertEquals(azureDevOpsUrl.getProject(), "MyProject");
    assertEquals(azureDevOpsUrl.getRepository(), "MyRepo");
  }

  @Test
  public void shouldParseIpv6FullFormAddress() {
    // given
    azureDevOpsURLParser =
        new AzureDevOpsURLParser(
            mock(DevfileFilenamesProvider.class),
            mock(PersonalAccessTokenManager.class),
            "https://[2001:0db8:0000:0000:0000:0000:0000:0001]/");

    // when
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse(
            "https://[2001:0db8:0000:0000:0000:0000:0000:0001]/MyOrg/MyProject/_git/MyRepo", null);

    // then
    assertEquals(azureDevOpsUrl.getOrganization(), "MyOrg");
    assertEquals(azureDevOpsUrl.getProject(), "MyProject");
    assertEquals(azureDevOpsUrl.getRepository(), "MyRepo");
  }

  @Test
  public void shouldParseIpv6WithCredentials() {
    // given
    azureDevOpsURLParser =
        new AzureDevOpsURLParser(
            mock(DevfileFilenamesProvider.class),
            mock(PersonalAccessTokenManager.class),
            "https://[2001:db8::1]/");

    // when
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse(
            "https://user:pwd@[2001:db8::1]/MyOrg/MyProject/_git/MyRepo", null);

    // then
    assertEquals(azureDevOpsUrl.getOrganization(), "MyOrg");
    assertEquals(azureDevOpsUrl.getProject(), "MyProject");
    assertEquals(azureDevOpsUrl.getRepository(), "MyRepo");
    assertEquals(azureDevOpsUrl.getCredentials(), Optional.of("user:pwd"));
  }

  @Test
  public void shouldParseIpv6UrlViaDynamicPatternMatching() {
    // The parser is configured for dev.azure.com, but getPatternMatcherByUrl() dynamically
    // creates a pattern from the URL itself, so IPv6 URLs can be parsed even when the
    // constructor was configured with a different host.
    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse("https://[2001:db8::1]/MyOrg/MyProject/_git/MyRepo", null);

    assertEquals(azureDevOpsUrl.getOrganization(), "MyOrg");
    assertEquals(azureDevOpsUrl.getProject(), "MyProject");
    assertEquals(azureDevOpsUrl.getRepository(), "MyRepo");
  }

  @Test(dataProvider = "parsing")
  public void testParse(
      String url,
      String organization,
      String project,
      String repository,
      String branch,
      String tag) {
    AzureDevOpsUrl azureDevOpsUrl = azureDevOpsURLParser.parse(url, null);

    assertEquals(azureDevOpsUrl.getOrganization(), organization);
    assertEquals(azureDevOpsUrl.getProject(), project);
    assertEquals(azureDevOpsUrl.getRepository(), repository);
    assertEquals(azureDevOpsUrl.getBranch(), branch);
    assertEquals(azureDevOpsUrl.getTag(), tag);
  }

  @Test(dataProvider = "parsingServer")
  public void testParseServer(
      String url,
      String organization,
      String project,
      String repository,
      String branch,
      String tag) {
    AzureDevOpsUrl azureDevOpsUrl = azureDevOpsURLParser.parse(url, null);

    assertEquals(azureDevOpsUrl.getOrganization(), organization);
    assertEquals(azureDevOpsUrl.getProject(), project);
    assertEquals(azureDevOpsUrl.getRepository(), repository);
    assertEquals(azureDevOpsUrl.getBranch(), branch);
    assertEquals(azureDevOpsUrl.getTag(), tag);
  }

  @DataProvider(name = "parsing")
  public Object[][] expectedParsing() {
    return new Object[][] {
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        null
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo.git",
        "MyOrg",
        "MyProject",
        "MyRepo.git",
        null,
        null
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo.dot.git",
        "MyOrg",
        "MyProject",
        "MyRepo.dot.git",
        null,
        null
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        null
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo-with-hypen",
        "MyOrg",
        "MyProject",
        "MyRepo-with-hypen",
        null,
        null
      },
      {"https://dev.azure.com/MyOrg/MyProject/_git/-", "MyOrg", "MyProject", "-", null, null},
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/-j.git",
        "MyOrg",
        "MyProject",
        "-j.git",
        null,
        null
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GBmain&_a=contents",
        "MyOrg",
        "MyProject",
        "MyRepo",
        "main",
        null
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        null
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo.git",
        "MyOrg",
        "MyProject",
        "MyRepo.git",
        null,
        null
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo.dot.git",
        "MyOrg",
        "MyProject",
        "MyRepo.dot.git",
        null,
        null
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        null
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo-with-hypen",
        "MyOrg",
        "MyProject",
        "MyRepo-with-hypen",
        null,
        null
      },
      {"git@ssh.dev.azure.com:v3/MyOrg/MyProject/-", "MyOrg", "MyProject", "-", null, null},
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/-j.git",
        "MyOrg",
        "MyProject",
        "-j.git",
        null,
        null
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GBmain",
        "MyOrg",
        "MyProject",
        "MyRepo",
        "main",
        null
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GTMyTag&_a=contents",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        "MyTag"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GTMyTag",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        "MyTag"
      },
      {"https://MyOrg@dev.azure.com/MyOrg/_git/MyRepo", "MyOrg", "MyRepo", "MyRepo", null, null},
    };
  }

  @DataProvider(name = "parsingServer")
  public Object[][] expectedServerParsing() {
    return new Object[][] {
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        null
      },
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo.git",
        "MyOrg",
        "MyProject",
        "MyRepo.git",
        null,
        null
      },
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo.dot.git",
        "MyOrg",
        "MyProject",
        "MyRepo.dot.git",
        null,
        null
      },
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo-with-hypen",
        "MyOrg",
        "MyProject",
        "MyRepo-with-hypen",
        null,
        null
      },
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/-", "MyOrg", "MyProject", "-", null, null
      },
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/-j.git",
        "MyOrg",
        "MyProject",
        "-j.git",
        null,
        null
      },
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GBmain&_a=contents",
        "MyOrg",
        "MyProject",
        "MyRepo",
        "main",
        null
      },
      {
        "ssh://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        null
      },
      {
        "ssh://dev.azure-server.com:22/MyOrg/MyProject/_git/MyRepo",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        null
      },
      {
        "ssh://dev.azure-server.com:22/MyOrg/MyProject/_git/MyRepo.git",
        "MyOrg",
        "MyProject",
        "MyRepo.git",
        null,
        null
      },
      {
        "ssh://dev.azure-server.com:22/MyOrg/MyProject/_git/MyRepo.dot.git",
        "MyOrg",
        "MyProject",
        "MyRepo.dot.git",
        null,
        null
      },
      {
        "ssh://dev.azure-server.com:22/MyOrg/MyProject/_git/MyRepo",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        null
      },
      {
        "ssh://dev.azure-server.com:22/MyOrg/MyProject/_git/MyRepo-with-hypen",
        "MyOrg",
        "MyProject",
        "MyRepo-with-hypen",
        null,
        null
      },
      {
        "ssh://dev.azure-server.com:22/MyOrg/MyProject/_git/-",
        "MyOrg",
        "MyProject",
        "-",
        null,
        null
      },
      {
        "ssh://dev.azure-server.com:22/MyOrg/MyProject/_git/-j.git",
        "MyOrg",
        "MyProject",
        "-j.git",
        null,
        null
      },
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GBmain",
        "MyOrg",
        "MyProject",
        "MyRepo",
        "main",
        null
      },
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GTMyTag&_a=contents",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        "MyTag"
      },
      {
        "https://dev.azure-server.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GTMyTag",
        "MyOrg",
        "MyProject",
        "MyRepo",
        null,
        "MyTag"
      },
      {"https://dev.azure-server.com/MyOrg/_git/MyRepo", "MyOrg", "MyRepo", "MyRepo", null, null},
    };
  }

  @Test(dataProvider = "url")
  public void testCredentials(String url, String organization, Optional<String> credentials) {
    AzureDevOpsUrl azureDevOpsUrl = azureDevOpsURLParser.parse(url, null);

    assertEquals(azureDevOpsUrl.getOrganization(), organization);
    assertEquals(azureDevOpsUrl.getCredentials(), credentials);
  }

  @DataProvider(name = "url")
  public Object[][] url() {
    return new Object[][] {
      {"https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo", "MyOrg", Optional.empty()},
      {
        "https://user:pwd@dev.azure.com/MyOrg/MyProject/_git/MyRepo",
        "MyOrg",
        Optional.of("user:pwd")
      },
    };
  }
}
