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
package org.eclipse.che.api.factory.server.bitbucket;

import static com.google.common.base.Strings.isNullOrEmpty;

import jakarta.validation.constraints.NotNull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;

/** Parser of String Bitbucket SAAS URLs and provides {@link BitbucketUrl} objects. */
@Singleton
public class BitbucketURLParser {
  private final DevfileFilenamesProvider devfileFilenamesProvider;

  @Inject
  public BitbucketURLParser(DevfileFilenamesProvider devfileFilenamesProvider) {
    this.devfileFilenamesProvider = devfileFilenamesProvider;
  }

  /**
   * Regexp to find repository details (repository name, workspace id, project name and branch)
   * Examples of valid URLs are in the test class.
   */
  protected static final Pattern BITBUCKET_PATTERN =
      Pattern.compile(
          "^https?://(?<username>[^/@]+)?@?bitbucket\\.org/(?<workspaceId>[^/]+)/(?<repoName>[^/]+)/?(\\.git)?(/(src|branch)/(?<branchName>[^/]+)/?)?$");

  protected static final Pattern BITBUCKET_SSH_PATTERN =
      Pattern.compile("^git@bitbucket.org:(?<workspaceId>.*)/(?<repoName>.*)$");

  public boolean isValid(@NotNull String url) {
    return BITBUCKET_PATTERN.matcher(url).matches() || BITBUCKET_SSH_PATTERN.matcher(url).matches();
  }

  public BitbucketUrl parse(String url, @Nullable String revision) {
    // Apply bitbucket url to the regexp
    boolean isHTTPSUrl = BITBUCKET_PATTERN.matcher(url).matches();
    Matcher matcher =
        isHTTPSUrl ? BITBUCKET_PATTERN.matcher(url) : BITBUCKET_SSH_PATTERN.matcher(url);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          String.format("The given bitbucket url %s is not a valid URL bitbucket url. ", url));
    }

    String workspaceId = matcher.group("workspaceId");
    String repoName = matcher.group("repoName");
    if (repoName.matches("^[\\w-][\\w.-]*?\\.git$")) {
      repoName = repoName.substring(0, repoName.length() - 4);
    }
    String username = null;
    String branchFromUrl = null;
    if (isHTTPSUrl) {
      username = matcher.group("username");
      branchFromUrl = matcher.group("branchName");
    }

    return new BitbucketUrl()
        .withUsername(username)
        .withRepository(repoName)
        .setIsHTTPSUrl(isHTTPSUrl)
        .withBranch(isNullOrEmpty(branchFromUrl) ? revision : branchFromUrl)
        .withWorkspaceId(workspaceId)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .withUrl(url);
  }
}
