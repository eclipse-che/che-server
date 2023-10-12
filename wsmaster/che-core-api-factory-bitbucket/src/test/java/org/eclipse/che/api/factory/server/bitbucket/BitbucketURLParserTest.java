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
package org.eclipse.che.api.factory.server.bitbucket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** Validate operations performed by the Bitbucket parser */
@Listeners(MockitoTestNGListener.class)
public class BitbucketURLParserTest {

  @Mock private URLFetcher urlFetcher;
  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  /** Instance of component that will be tested. */
  @InjectMocks private BitbucketURLParser bitbucketURLParser;

  @BeforeMethod
  public void init() {
    lenient().when(urlFetcher.fetchSafely(any(String.class))).thenReturn("");
  }

  /** Check URLs are valid with regexp */
  @Test(dataProvider = "UrlsProvider")
  public void checkRegexp(String url) {
    assertTrue(bitbucketURLParser.isValid(url), "url " + url + " is invalid");
  }

  /** Compare parsing */
  @Test(dataProvider = "parsing")
  public void checkParsing(String url, String username, String repository, String branch) {
    BitbucketUrl bitbucketUrl = bitbucketURLParser.parse(url);

    assertEquals(bitbucketUrl.getWorkspaceId(), username);
    assertEquals(bitbucketUrl.getRepository(), repository);
    assertEquals(bitbucketUrl.getBranch(), branch);
  }

  /** Compare parsing */
  @Test(dataProvider = "parsingBadRepository")
  public void checkParsingBadRepositoryDoNotModifiesInitialInput(String url, String repository) {
    BitbucketUrl bitbucketUrl = bitbucketURLParser.parse(url);
    assertEquals(bitbucketUrl.getRepository(), repository);
  }

  @DataProvider(name = "UrlsProvider")
  public Object[][] urls() {
    return new Object[][] {
      {"https://bitbucket.org/eclipse/che"},
      {"https://bitbucket.org/eclipse/che123"},
      {"https://bitbucket.org/eclipse/che/"},
      {"https://bitbucket.org/eclipse/che/src/4.2.x"},
      {"https://bitbucket.org/eclipse/che/src/master/"},
      {"https://bitbucket.org/eclipse/che.git"},
      {"https://bitbucket.org/eclipse/che.with.dots.git"},
      {"https://bitbucket.org/eclipse/che-with-hyphen"},
      {"https://bitbucket.org/eclipse/che-with-hyphen.git"},
      {"git@bitbucket.org:eclipse/che"},
      {"git@bitbucket.org:eclipse/che123"},
      {"git@bitbucket.org:eclipse/che/"},
      {"git@bitbucket.org:eclipse/che/src/4.2.x"},
      {"git@bitbucket.org:eclipse/che/src/master/"},
      {"git@bitbucket.org:eclipse/che.git"},
      {"git@bitbucket.org:eclipse/che.with.dots.git"},
      {"git@bitbucket.org:eclipse/che-with-hyphen"},
      {"git@bitbucket.org:eclipse/che-with-hyphen.git"},
      {"https://username@bitbucket.org/eclipse/che"},
      {"https://username@bitbucket.org/eclipse/che123"},
      {"https://username@bitbucket.org/eclipse/che/"},
      {"https://username@bitbucket.org/eclipse/che/src/4.2.x"},
      {"https://username@bitbucket.org/eclipse/che/src/master/"},
      {"https://username@bitbucket.org/eclipse/che.git"},
      {"https://username@bitbucket.org/eclipse/che.with.dots.git"},
      {"https://username@bitbucket.org/eclipse/che-with-hyphen"},
      {"https://username@bitbucket.org/eclipse/che-with-hyphen.git"}
    };
  }

  @DataProvider(name = "parsing")
  public Object[][] expectedParsing() {
    return new Object[][] {
      {"https://bitbucket.org/eclipse/che", "eclipse", "che", null},
      {"https://bitbucket.org/eclipse/che123", "eclipse", "che123", null},
      {"https://bitbucket.org/eclipse/che.git", "eclipse", "che", null},
      {"https://bitbucket.org/eclipse/che.with.dot.git", "eclipse", "che.with.dot", null},
      {"https://bitbucket.org/eclipse/-.git", "eclipse", "-", null},
      {"https://bitbucket.org/eclipse/-j.git", "eclipse", "-j", null},
      {"https://bitbucket.org/eclipse/-", "eclipse", "-", null},
      {"https://bitbucket.org/eclipse/che-with-hyphen", "eclipse", "che-with-hyphen", null},
      {"https://bitbucket.org/eclipse/che-with-hyphen.git", "eclipse", "che-with-hyphen", null},
      {"https://bitbucket.org/eclipse/che/", "eclipse", "che", null},
      {"https://bitbucket.org/eclipse/repositorygit", "eclipse", "repositorygit", null},
      {"git@bitbucket.org:eclipse/che", "eclipse", "che", null},
      {"git@bitbucket.org:eclipse/che123", "eclipse", "che123", null},
      {"git@bitbucket.org:eclipse/che.git", "eclipse", "che", null},
      {"git@bitbucket.org:eclipse/che.with.dot.git", "eclipse", "che.with.dot", null},
      {"git@bitbucket.org:eclipse/-.git", "eclipse", "-", null},
      {"git@bitbucket.org:eclipse/-j.git", "eclipse", "-j", null},
      {"git@bitbucket.org:eclipse/-", "eclipse", "-", null},
      {"git@bitbucket.org:eclipse/che-with-hyphen", "eclipse", "che-with-hyphen", null},
      {"git@bitbucket.org:eclipse/che-with-hyphen.git", "eclipse", "che-with-hyphen", null},
      {"git@bitbucket.org:eclipse/repositorygit", "eclipse", "repositorygit", null},
      {"https://bitbucket.org/eclipse/che/src/4.2.x", "eclipse", "che", "4.2.x"},
      {"https://bitbucket.org/eclipse/che/src/master/", "eclipse", "che", "master"}
    };
  }

  @DataProvider(name = "parsingBadRepository")
  public Object[][] parsingBadRepository() {
    return new Object[][] {
      {"https://bitbucket.org/eclipse/che .git", "che .git"},
      {"https://bitbucket.org/eclipse/.git", ".git"},
      {"https://bitbucket.org/eclipse/myB@dR&pository.git", "myB@dR&pository.git"},
      {"https://bitbucket.org/eclipse/.", "."},
      {"https://bitbucket.org/eclipse/івапівап.git", "івапівап.git"},
      {"https://bitbucket.org/eclipse/ ", " "},
      {"https://bitbucket.org/eclipse/.", "."},
      {"https://bitbucket.org/eclipse/ .git", " .git"},
      {"git@bitbucket.org:eclipse/che .git", "che .git"},
      {"git@bitbucket.org:eclipse/.git", ".git"},
      {"git@bitbucket.org:eclipse/myB@dR&pository.git", "myB@dR&pository.git"},
      {"git@bitbucket.org:eclipse/.", "."},
      {"git@bitbucket.org:eclipse/івапівап.git", "івапівап.git"},
      {"git@bitbucket.org:eclipse/ ", " "},
      {"git@bitbucket.org:eclipse/.", "."},
      {"git@bitbucket.org:eclipse/ .git", " .git"}
    };
  }
}
