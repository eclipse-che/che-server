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
package org.eclipse.che.api.factory.server.git.ssh;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Anatalii Bazko */
@Listeners(MockitoTestNGListener.class)
public class GitSshURLParserTest {

  private GitSshURLParser gitSshURLParser;

  @BeforeMethod
  protected void start() {
    gitSshURLParser = new GitSshURLParser(mock(DevfileFilenamesProvider.class));
  }

  @Test(dataProvider = "parsing")
  public void testParse(String url, String hostName, String repository) {
    GitSshUrl gitSshUrl = gitSshURLParser.parse(url);

    assertEquals(gitSshUrl.getHostName(), hostName);
    assertEquals(gitSshUrl.getRepository(), repository);
  }

  @DataProvider(name = "parsing")
  public Object[][] expectedParsing() {
    return new Object[][] {
      {"git@ssh.dev.azure.com:v3/MyOrg/MyProject/MyRepo", "ssh.dev.azure.com", "MyRepo"},
      {"git@github.com:MyOrg/MyRepo.git", "github.com", "MyRepo"},
    };
  }
}
