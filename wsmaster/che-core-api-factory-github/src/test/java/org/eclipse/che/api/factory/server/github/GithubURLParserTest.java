/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.github;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
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

/**
 * Validate operations performed by the Github parser
 *
 * @author Florent Benoit
 */
@Listeners(MockitoTestNGListener.class)
public class GithubURLParserTest {

  @Mock private URLFetcher urlFetcher;
  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  /** Instance of component that will be tested. */
  @InjectMocks private GithubURLParser githubUrlParser;

  /** Check invalid url (not a github one) */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void invalidUrl() {
    githubUrlParser.parse("http://www.eclipse.org");
  }

  @BeforeMethod
  public void init() {
    lenient().when(urlFetcher.fetchSafely(any(String.class))).thenReturn("");
  }

  /** Check URLs are valid with regexp */
  @Test(dataProvider = "UrlsProvider")
  public void checkRegexp(String url) {
    assertTrue(githubUrlParser.isValid(url), "url " + url + " is invalid");
  }

  /** Compare parsing */
  @Test(dataProvider = "parsing")
  public void checkParsing(
      String url, String username, String repository, String branch, String subfolder) {
    GithubUrl githubUrl = githubUrlParser.parse(url);

    assertEquals(githubUrl.getUsername(), username);
    assertEquals(githubUrl.getRepository(), repository);
    assertEquals(githubUrl.getBranch(), branch);
    assertEquals(githubUrl.getSubfolder(), subfolder);
  }

  /** Compare parsing */
  @Test(dataProvider = "parsingBadRepository")
  public void checkParsingBadRepositoryDoNotModifiesInitialInput(String url, String repository) {
    GithubUrl githubUrl = githubUrlParser.parse(url);
    assertEquals(githubUrl.getRepository(), repository);
  }

  @DataProvider(name = "UrlsProvider")
  public Object[][] urls() {
    return new Object[][] {
      {"https://github.com/eclipse/che"},
      {"https://github.com/eclipse/che123"},
      {"https://github.com/eclipse/che/"},
      {"https://github.com/eclipse/che/tree/4.2.x"},
      {"https://github.com/eclipse/che/tree/master/"},
      {"https://github.com/eclipse/che/tree/master/dashboard/"},
      {"https://github.com/eclipse/che/tree/master/plugins/plugin-git/che-plugin-git-ext-git"},
      {"https://github.com/eclipse/che/tree/master/plugins/plugin-git/che-plugin-git-ext-git/"},
      {"https://github.com/eclipse/che/pull/11103"},
      {"https://github.com/eclipse/che.git"},
      {"https://github.com/eclipse/che.with.dots.git"},
      {"https://github.com/eclipse/che-with-hyphen"},
      {"https://github.com/eclipse/che-with-hyphen.git"}
    };
  }

  @DataProvider(name = "parsing")
  public Object[][] expectedParsing() {
    return new Object[][] {
      {"https://github.com/eclipse/che", "eclipse", "che", null, null},
      {"https://github.com/eclipse/che123", "eclipse", "che123", null, null},
      {"https://github.com/eclipse/che.git", "eclipse", "che", null, null},
      {"https://github.com/eclipse/che.with.dot.git", "eclipse", "che.with.dot", null, null},
      {"https://github.com/eclipse/-.git", "eclipse", "-", null, null},
      {"https://github.com/eclipse/-j.git", "eclipse", "-j", null, null},
      {"https://github.com/eclipse/-", "eclipse", "-", null, null},
      {"https://github.com/eclipse/che-with-hyphen", "eclipse", "che-with-hyphen", null, null},
      {"https://github.com/eclipse/che-with-hyphen.git", "eclipse", "che-with-hyphen", null, null},
      {"https://github.com/eclipse/che/", "eclipse", "che", null, null},
      {"https://github.com/eclipse/repositorygit", "eclipse", "repositorygit", null, null},
      {"https://github.com/eclipse/che/tree/4.2.x", "eclipse", "che", "4.2.x", null},
      {
        "https://github.com/eclipse/che/tree/master/dashboard/",
        "eclipse",
        "che",
        "master",
        "dashboard/"
      },
      {
        "https://github.com/eclipse/che/tree/master/plugins/plugin-git/che-plugin-git-ext-git",
        "eclipse",
        "che",
        "master",
        "plugins/plugin-git/che-plugin-git-ext-git"
      }
    };
  }

  @DataProvider(name = "parsingBadRepository")
  public Object[][] parsingBadRepository() {
    return new Object[][] {
      {"https://github.com/eclipse/che .git", "che .git"},
      {"https://github.com/eclipse/.git", ".git"},
      {"https://github.com/eclipse/myB@dR&pository.git", "myB@dR&pository.git"},
      {"https://github.com/eclipse/.", "."},
      {"https://github.com/eclipse/івапівап.git", "івапівап.git"},
      {"https://github.com/eclipse/ ", " "},
      {"https://github.com/eclipse/.", "."},
      {"https://github.com/eclipse/ .git", " .git"}
    };
  }

  /** Check Pull Request with data inside the repository */
  @Test
  public void checkPullRequestFromRepository() {

    String url = "https://github.com/eclipse/che/pull/21276";
    when(urlFetcher.fetchSafely(url))
        .thenReturn(
            "    </div>\n"
                + "  <div class=\"d-flex flex-items-center flex-wrap mt-0 gh-header-meta\">\n"
                + "    <div class=\"flex-shrink-0 mb-2 flex-self-start flex-md-self-center\">\n"
                + "        <span reviewable_state=\"ready\" title=\"Status: Open\" data-view-component=\"true\" class=\"State State--open\">\n"
                + "  <svg height=\"16\" class=\"octicon octicon-git-pull-request\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" aria-hidden=\"true\"><path fill-rule=\"evenodd\" d=\"M7.177 3.073L9.573.677A.25.25 0 0110 .854v4.792a.25.25 0 01-.427.177L7.177 3.427a.25.25 0 010-.354zM3.75 2.5a.75.75 0 100 1.5.75.75 0 000-1.5zm-2.25.75a2.25 2.25 0 113 2.122v5.256a2.251 2.251 0 11-1.5 0V5.372A2.25 2.25 0 011.5 3.25zM11 2.5h-1V4h1a1 1 0 011 1v5.628a2.251 2.251 0 101.5 0V5A2.5 2.5 0 0011 2.5zm1 10.25a.75.75 0 111.5 0 .75.75 0 01-1.5 0zM3.75 12a.75.75 0 100 1.5.75.75 0 000-1.5z\"></path></svg> Open\n"
                + "</span>\n"
                + "    </div>\n"
                + "\n"
                + "\n"
                + "\n"
                + "    <div class=\"flex-auto min-width-0 mb-2\">\n"
                + "          <a class=\"author Link--secondary text-bold css-truncate css-truncate-target expandable\" data-hovercard-type=\"user\" data-hovercard-url=\"/users/che-bot/hovercard\" data-octo-click=\"hovercard-link-click\" data-octo-dimensions=\"link_type:self\" href=\"/che-bot\">che-bot</a>\n"
                + "\n"
                + "  wants to merge\n"
                + "  <span class=\"js-updating-pull-request-commits-count\">1</span>\n"
                + "  commit into\n"
                + "\n"
                + "\n"
                + "\n"
                + "  <span title=\"eclipse/che:main\" class=\"commit-ref css-truncate user-select-contain expandable base-ref\"><a title=\"eclipse/che:main\" class=\"no-underline \" href=\"/eclipse/che\"><span class=\"css-truncate-target\">main</span></a></span><span></span>\n"
                + "\n"
                + "  <div class=\"commit-ref-dropdown\">\n"
                + "    <details class=\"details-reset details-overlay select-menu commitish-suggester\" id=\"branch-select-menu\">\n"
                + "      <summary class=\"btn btn-sm select-menu-button branch\" title=\"Choose a base branch\">\n"
                + "        <i>base:</i>\n"
                + "        <span class=\"css-truncate css-truncate-target\" title=\"main\">main</span>\n"
                + "      </summary>\n"
                + "      <input-demux-context-wrapper data-context-type=\"baseChange\">");
    GithubUrl githubUrl = githubUrlParser.parse(url);

    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "main");
  }

  /** Check Pull Request with data outside the repository (fork) */
  @Test
  public void checkPullRequestFromForkedRepository() {

    String url = "https://github.com/eclipse/che/pull/20189";
    when(urlFetcher.fetchSafely(url))
        .thenReturn(
            " <div class=\"d-flex flex-items-center flex-wrap mt-0 gh-header-meta\">\n"
                + "    <div class=\"flex-shrink-0 mb-2 flex-self-start flex-md-self-center\">\n"
                + "        <span reviewable_state=\"ready\" title=\"Status: Open\" data-view-component=\"true\" class=\"State State--open\">\n"
                + "  <svg height=\"16\" class=\"octicon octicon-git-pull-request\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" aria-hidden=\"true\"><path fill-rule=\"evenodd\" d=\"M7.177 3.073L9.573.677A.25.25 0 0110 .854v4.792a.25.25 0 01-.427.177L7.177 3.427a.25.25 0 010-.354zM3.75 2.5a.75.75 0 100 1.5.75.75 0 000-1.5zm-2.25.75a2.25 2.25 0 113 2.122v5.256a2.251 2.251 0 11-1.5 0V5.372A2.25 2.25 0 011.5 3.25zM11 2.5h-1V4h1a1 1 0 011 1v5.628a2.251 2.251 0 101.5 0V5A2.5 2.5 0 0011 2.5zm1 10.25a.75.75 0 111.5 0 .75.75 0 01-1.5 0zM3.75 12a.75.75 0 100 1.5.75.75 0 000-1.5z\"></path></svg> Open\n"
                + "</span>\n"
                + "    </div>\n"
                + "\n"
                + "\n"
                + "\n"
                + "    <div class=\"flex-auto min-width-0 mb-2\">\n"
                + "          <a class=\"author Link--secondary text-bold css-truncate css-truncate-target expandable\" data-hovercard-type=\"user\" data-hovercard-url=\"/users/apupier/hovercard\" data-octo-click=\"hovercard-link-click\" data-octo-dimensions=\"link_type:self\" href=\"/apupier\">apupier</a>\n"
                + "\n"
                + "  wants to merge\n"
                + "  <span class=\"js-updating-pull-request-commits-count\">1</span>\n"
                + "  commit into\n"
                + "\n"
                + "\n"
                + "\n"
                + "  <span title=\"eclipse/che:main\" class=\"commit-ref css-truncate user-select-contain expandable base-ref\"><a title=\"eclipse/che:main\" class=\"no-underline \" href=\"/eclipse/che\"><span class=\"css-truncate-target\">eclipse</span>:<span class=\"css-truncate-target\">main</span></a></span><span></span>\n"
                + "\n"
                + "  <div class=\"commit-ref-dropdown\">\n"
                + "    <details class=\"details-reset details-overlay select-menu commitish-suggester\" id=\"branch-select-menu\">\n"
                + "      <summary class=\"btn btn-sm select-menu-button branch\" title=\"Choose a base branch\">\n"
                + "        <i>base:</i>\n"
                + "        <span class=\"css-truncate css-truncate-target\" title=\"main\">main</span>\n"
                + "      </summary>\n"
                + "      <input-demux-context-wrapper data-context-type=\"baseChange\">");
    GithubUrl githubUrl = githubUrlParser.parse(url);

    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "main");
  }

  /** Check Pull Request is failing with Merged state */
  @Test(
      expectedExceptions = IllegalArgumentException.class,
      expectedExceptionsMessageRegExp = ".*found merged.*")
  public void checkPullRequestMergedState() {

    String url = "https://github.com/eclipse/che/pull/11103";
    when(urlFetcher.fetchSafely(url))
        .thenReturn(
            "  <div class=\"d-flex flex-items-center flex-wrap mt-0 gh-header-meta\">\n"
                + "    <div class=\"flex-shrink-0 mb-2 flex-self-start flex-md-self-center\">\n"
                + "        <span reviewable_state=\"ready\" title=\"Status: Merged\" data-view-component=\"true\" class=\"State State--merged\">\n"
                + "  <svg height=\"16\" class=\"octicon octicon-git-merge\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" aria-hidden=\"true\"><path fill-rule=\"evenodd\" d=\"M5 3.254V3.25v.005a.75.75 0 110-.005v.004zm.45 1.9a2.25 2.25 0 10-1.95.218v5.256a2.25 2.25 0 101.5 0V7.123A5.735 5.735 0 009.25 9h1.378a2.251 2.251 0 100-1.5H9.25a4.25 4.25 0 01-3.8-2.346zM12.75 9a.75.75 0 100-1.5.75.75 0 000 1.5zm-8.5 4.5a.75.75 0 100-1.5.75.75 0 000 1.5z\"></path></svg> Merged\n"
                + "</span>\n"
                + "    </div>\n"
                + "\n"
                + "\n"
                + "\n"
                + "    <div class=\"flex-auto min-width-0 mb-2\">\n"
                + "          <a class=\"author Link--secondary text-bold css-truncate css-truncate-target expandable\" data-hovercard-type=\"user\" data-hovercard-url=\"/users/benoitf/hovercard\" data-octo-click=\"hovercard-link-click\" data-octo-dimensions=\"link_type:self\" href=\"/benoitf\">benoitf</a>\n"
                + "  merged 1 commit into\n"
                + "\n"
                + "\n"
                + "\n"
                + "  <span title=\"eclipse/che:master\" class=\"commit-ref css-truncate user-select-contain expandable \"><a title=\"eclipse/che:master\" class=\"no-underline \" href=\"/eclipse/che/tree/master\"><span class=\"css-truncate-target\">master</span></a></span><span></span>\n"
                + "\n"
                + "from\n"
                + "\n"
                + "<span title=\"eclipse/che:cleanup-e2e-theia\" class=\"commit-ref css-truncate user-select-contain expandable head-ref\"><a title=\"eclipse/che:cleanup-e2e-theia\" class=\"no-underline \" href=\"/eclipse/che/tree/cleanup-e2e-theia\"><span class=\"css-truncate-target\">cleanup-e2e-theia</span></a></span><span><clipboard-copy aria-label=\"Copy\" data-copy-feedback=\"Copied!\" value=\"cleanup-e2e-theia\" data-view-component=\"true\" class=\"Link--onHover js-copy-branch color-fg-muted d-inline-block ml-1\">\n"
                + "    <svg aria-hidden=\"true\" height=\"16\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" data-view-component=\"true\" class=\"octicon octicon-copy\">\n"
                + "    <path fill-rule=\"evenodd\" d=\"M0 6.75C0 5.784.784 5 1.75 5h1.5a.75.75 0 010 1.5h-1.5a.25.25 0 00-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 00.25-.25v-1.5a.75.75 0 011.5 0v1.5A1.75 1.75 0 019.25 16h-7.5A1.75 1.75 0 010 14.25v-7.5z\"></path><path fill-rule=\"evenodd\" d=\"M5 1.75C5 .784 5.784 0 6.75 0h7.5C15.216 0 16 .784 16 1.75v7.5A1.75 1.75 0 0114.25 11h-7.5A1.75 1.75 0 015 9.25v-7.5zm1.75-.25a.25.25 0 00-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 00.25-.25v-7.5a.25.25 0 00-.25-.25h-7.5z\"></path>\n"
                + "</svg>\n"
                + "    <svg style=\"display: none;\" aria-hidden=\"true\" height=\"16\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" data-view-component=\"true\" class=\"octicon octicon-check color-fg-success\">\n"
                + "    <path fill-rule=\"evenodd\" d=\"M13.78 4.22a.75.75 0 010 1.06l-7.25 7.25a.75.75 0 01-1.06 0L2.22 9.28a.75.75 0 011.06-1.06L6 10.94l6.72-6.72a.75.75 0 011.06 0z\"></path>\n"
                + "</svg>\n"
                + "</clipboard-copy></span>\n"
                + "\n"
                + "\n"
                + "  <relative-time datetime=\"2018-09-07T08:00:49Z\" class=\"no-wrap\">Sep 7, 2018</relative-time>\n"
                + "\n"
                + "    </div>\n"
                + "  </div>\n"
                + "");
    githubUrlParser.parse(url);
  }

  /** Check Pull Request is failing with Closed state */
  @Test(
      expectedExceptions = IllegalArgumentException.class,
      expectedExceptionsMessageRegExp = ".*found closed.*")
  public void checkPullRequestClosedState() {

    String url = "https://github.com/eclipse/che/pull/20754";
    when(urlFetcher.fetchSafely(url))
        .thenReturn(
            "   </div>\n"
                + "  <div class=\"d-flex flex-items-center flex-wrap mt-0 gh-header-meta\">\n"
                + "    <div class=\"flex-shrink-0 mb-2 flex-self-start flex-md-self-center\">\n"
                + "        <span reviewable_state=\"ready\" title=\"Status: Closed\" data-view-component=\"true\" class=\"State State--closed\">\n"
                + "  <svg height=\"16\" class=\"octicon octicon-git-pull-request-closed\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" aria-hidden=\"true\"><path fill-rule=\"evenodd\" d=\"M10.72 1.227a.75.75 0 011.06 0l.97.97.97-.97a.75.75 0 111.06 1.061l-.97.97.97.97a.75.75 0 01-1.06 1.06l-.97-.97-.97.97a.75.75 0 11-1.06-1.06l.97-.97-.97-.97a.75.75 0 010-1.06zM12.75 6.5a.75.75 0 00-.75.75v3.378a2.251 2.251 0 101.5 0V7.25a.75.75 0 00-.75-.75zm0 5.5a.75.75 0 100 1.5.75.75 0 000-1.5zM2.5 3.25a.75.75 0 111.5 0 .75.75 0 01-1.5 0zM3.25 1a2.25 2.25 0 00-.75 4.372v5.256a2.251 2.251 0 101.5 0V5.372A2.25 2.25 0 003.25 1zm0 11a.75.75 0 100 1.5.75.75 0 000-1.5z\"></path></svg> Closed\n"
                + "</span>\n"
                + "    </div>\n"
                + "\n"
                + "\n"
                + "\n"
                + "    <div class=\"flex-auto min-width-0 mb-2\">\n"
                + "          <a class=\"author Link--secondary text-bold css-truncate css-truncate-target expandable\" data-hovercard-type=\"user\" data-hovercard-url=\"/users/Ohrimenko1988/hovercard\" data-octo-click=\"hovercard-link-click\" data-octo-dimensions=\"link_type:self\" href=\"/Ohrimenko1988\">Ohrimenko1988</a>\n"
                + "\n"
                + "  wants to merge\n"
                + "  <span class=\"js-updating-pull-request-commits-count\">10</span>\n"
                + "  commits into\n"
                + "\n"
                + "\n"
                + "\n"
                + "  <span title=\"eclipse/che:7.38.x\" class=\"commit-ref css-truncate user-select-contain expandable \"><a title=\"eclipse/che:7.38.x\" class=\"no-underline \" href=\"/eclipse/che/tree/7.38.x\"><span class=\"css-truncate-target\">eclipse</span>:<span class=\"css-truncate-target\">7.38.x</span></a></span><span></span>\n"
                + "\n"
                + "from\n"
                + "\n"
                + "<span title=\"Ohrimenko1988/che:iokhrime-chromedriver-7.38.x\" class=\"commit-ref css-truncate user-select-contain expandable head-ref\"><a title=\"Ohrimenko1988/che:iokhrime-chromedriver-7.38.x\" class=\"no-underline \" href=\"/Ohrimenko1988/che/tree/iokhrime-chromedriver-7.38.x\"><span class=\"css-truncate-target\">Ohrimenko1988</span>:<span class=\"css-truncate-target\">iokhrime-chromedriver-7.38.x</span></a></span><span><clipboard-copy aria-label=\"Copy\" data-copy-feedback=\"Copied!\" value=\"Ohrimenko1988:iokhrime-chromedriver-7.38.x\" data-view-component=\"true\" class=\"Link--onHover js-copy-branch color-fg-muted d-inline-block ml-1\">\n"
                + "    <svg aria-hidden=\"true\" height=\"16\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" data-view-component=\"true\" class=\"octicon octicon-copy\">\n"
                + "    <path fill-rule=\"evenodd\" d=\"M0 6.75C0 5.784.784 5 1.75 5h1.5a.75.75 0 010 1.5h-1.5a.25.25 0 00-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 00.25-.25v-1.5a.75.75 0 011.5 0v1.5A1.75 1.75 0 019.25 16h-7.5A1.75 1.75 0 010 14.25v-7.5z\"></path><path fill-rule=\"evenodd\" d=\"M5 1.75C5 .784 5.784 0 6.75 0h7.5C15.216 0 16 .784 16 1.75v7.5A1.75 1.75 0 0114.25 11h-7.5A1.75 1.75 0 015 9.25v-7.5zm1.75-.25a.25.25 0 00-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 00.25-.25v-7.5a.25.25 0 00-.25-.25h-7.5z\"></path>\n"
                + "</svg>\n"
                + "    <svg style=\"display: none;\" aria-hidden=\"true\" height=\"16\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" data-view-component=\"true\" class=\"octicon octicon-check color-fg-success\">\n"
                + "    <path fill-rule=\"evenodd\" d=\"M13.78 4.22a.75.75 0 010 1.06l-7.25 7.25a.75.75 0 01-1.06 0L2.22 9.28a.75.75 0 011.06-1.06L6 10.94l6.72-6.72a.75.75 0 011.06 0z\"></path>\n"
                + "</svg>\n"
                + "</clipboard-copy></span>\n"
                + "\n"
                + "\n"
                + "\n"
                + "    </div>\n"
                + "  </div>\n"
                + "");
    githubUrlParser.parse(url);
  }
}
