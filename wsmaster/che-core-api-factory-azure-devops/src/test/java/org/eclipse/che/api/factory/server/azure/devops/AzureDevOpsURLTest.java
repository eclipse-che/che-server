/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
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

import static java.lang.String.format;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.Iterator;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class AzureDevOpsURLTest {
  private AzureDevOpsURLParser azureDevOpsURLParser;

  @BeforeMethod
  protected void init() {
    azureDevOpsURLParser =
        new AzureDevOpsURLParser(mock(DevfileFilenamesProvider.class), "https://dev.azure.com/");
  }

  @Test(dataProvider = "urlsProvider")
  public void checkDevfileLocation(String repoUrl, String fileUrl) {

    AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser
            .parse(repoUrl)
            .withDevfileFilenames(Arrays.asList("devfile.yaml", "foo.bar"));
    assertEquals(azureDevOpsUrl.devfileFileLocations().size(), 2);
    Iterator<RemoteFactoryUrl.DevfileLocation> iterator =
        azureDevOpsUrl.devfileFileLocations().iterator();
    String location = iterator.next().location();
    assertEquals(location, format(fileUrl, "devfile.yaml"));
    assertEquals(location, format(fileUrl, "foo.bar"));
  }

  @DataProvider
  public static Object[][] urlsProvider() {
    return new Object[][] {
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo.git",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo.git/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo.dot.git",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo.dot.git/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo-with-hypen",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo-with-hypen/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/-",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/-/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/-j.git",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/-j.git/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo.git",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo.git/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo.dot.git",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo.dot.git/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo-with-hypen",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo-with-hypen/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/-",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/-/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/-j.git",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/-j.git/items?path=/devfile.yaml&api-version=7.0"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GBmain&_a=contents",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo/items?path=/devfile.yaml&versionType=branch&version=main&api-version=7.0"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GBmain",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo/items?path=/devfile.yaml&versionType=branch&version=main&api-version=7.0"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GTMyTag&_a=contents",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo/items?path=/devfile.yaml&versionType=tag&version=MyTag&api-version=7.0"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GTMyTag",
        "https://dev.azure.com/MyOrg/MyProject/_apis/git/repositories/MyRepo/items?path=/devfile.yaml&versionType=tag&version=MyTag&api-version=7.0"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/_git/MyRepo",
        "https://dev.azure.com/MyOrg/MyRepo/_apis/git/repositories/MyRepo/items?path=/devfile.yaml&api-version=7.0"
      }
    };
  }

  @Test(dataProvider = "repoProvider")
  public void checkRepositoryLocation(String rawUrl, String repoUrl) {
    AzureDevOpsUrl azureDevOpsUrl = azureDevOpsURLParser.parse(rawUrl);
    assertEquals(azureDevOpsUrl.getRepositoryLocation(), repoUrl);
  }

  @DataProvider
  public static Object[][] repoProvider() {
    return new Object[][] {
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo",
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo.git",
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo.git"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo.dot.git",
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo.dot.git"
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo",
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo"
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo-with-hypen",
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo-with-hypen"
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/-",
        "https://dev.azure.com/MyOrg/MyProject/_git/-"
      },
      {
        "https://dev.azure.com/MyOrg/MyProject/_git/-j.git",
        "https://dev.azure.com/MyOrg/MyProject/_git/-j.git"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo",
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo.git",
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo.git"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo.dot.git",
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo.dot.git"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo",
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo"
      },
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo-with-hypen",
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo-with-hypen"
      },
      {"git@ssh.dev.azure.com:v3/MyOrg/MyProject/-", "git@ssh.dev.azure.com:v3/MyOrg/MyProject/-"},
      {
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/-j.git",
        "git@ssh.dev.azure.com:v3/MyOrg/MyProject/-j.git"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GBmain&_a=contents",
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GBmain",
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GTMyTag&_a=contents",
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/MyProject/_git/MyRepo?path=MyFile&version=GTMyTag",
        "https://dev.azure.com/MyOrg/MyProject/_git/MyRepo"
      },
      {
        "https://MyOrg@dev.azure.com/MyOrg/_git/MyRepo",
        "https://dev.azure.com/MyOrg/MyRepo/_git/MyRepo"
      }
    };
  }
}
