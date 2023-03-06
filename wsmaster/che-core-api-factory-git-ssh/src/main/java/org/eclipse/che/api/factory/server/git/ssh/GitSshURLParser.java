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

import static java.lang.String.format;
import static java.util.regex.Pattern.compile;

import jakarta.validation.constraints.NotNull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;

/**
 * Parser of String Git Ssh URLs and provide {@link GitSshUrl} objects.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class GitSshURLParser {

  private final Pattern gitSshPattern;

  private final DevfileFilenamesProvider devfileFilenamesProvider;

  @Inject
  public GitSshURLParser(DevfileFilenamesProvider devfileFilenamesProvider) {
    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.gitSshPattern = compile("^git@(?<hostName>[^:]++):(.*)/(?<repoName>[^/]++)$");
  }

  public boolean isValid(@NotNull String url) {
    return gitSshPattern.matcher(url).matches();
  }

  public GitSshUrl parse(String url) {
    Matcher matcher = gitSshPattern.matcher(url);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          format("The given url %s is not a valid. It should start with git@<hostName>", url));
    }

    String hostName = matcher.group("hostName");
    String repoName = matcher.group("repoName");
    if (repoName.endsWith(".git")) {
      repoName = repoName.substring(0, repoName.length() - 4);
    }

    return new GitSshUrl()
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .withHostName(hostName)
        .withRepository(repoName)
        .withRepositoryLocation(url)
        .withUrl(url);
  }
}
