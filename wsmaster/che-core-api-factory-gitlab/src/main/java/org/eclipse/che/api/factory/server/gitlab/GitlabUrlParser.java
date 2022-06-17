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
package org.eclipse.che.api.factory.server.gitlab;

import static java.lang.String.format;

import com.google.common.base.Splitter;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.StringUtils;

/**
 * Parser of String Gitlab URLs and provide {@link GitlabUrl} objects.
 *
 * @author Max Shaposhnyk
 */
public class GitlabUrlParser {

  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private static final List<String> gitlabUrlPatternTemplates =
      List.of(
          "^(?<host>%s)/(?<user>[^/]++)/(?<project>[^./]++).git",
          "^(?<host>%s)/(?<user>[^/]++)/(?<project>[^/]++)/(?<repository>[^.]++).git",
          "^(?<host>%s)/(?<user>[^/]++)/(?<project>[^/]++)(/)?(?<repository>[^/]++)?(/)?",
          "^(?<host>%s)/(?<user>[^/]++)/(?<project>[^/]++)(/)?(?<repository>[^/]++)?/-/tree/(?<branch>[^/]++)(/)?(?<subfolder>[^/]++)?");
  private final List<Pattern> gitlabUrlPatterns = new ArrayList<>();

  @Inject
  public GitlabUrlParser(
      @Nullable @Named("che.integration.gitlab.server_endpoints") String bitbucketEndpoints,
      DevfileFilenamesProvider devfileFilenamesProvider,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.personalAccessTokenManager = personalAccessTokenManager;
    if (bitbucketEndpoints != null) {
      for (String bitbucketEndpoint : Splitter.on(",").split(bitbucketEndpoints)) {
        String trimmedEndpoint = StringUtils.trimEnd(bitbucketEndpoint, '/');
        for (String gitlabUrlPatternTemplate : gitlabUrlPatternTemplates) {
          this.gitlabUrlPatterns.add(
              Pattern.compile(format(gitlabUrlPatternTemplate, trimmedEndpoint)));
        }
      }
    }
  }

  public boolean isValid(@NotNull String url) {
    if (!gitlabUrlPatterns.isEmpty()) {
      return gitlabUrlPatterns.stream().anyMatch(pattern -> pattern.matcher(url).matches());
    } else {
      // If Gitlab URL is not configured, try to find it in a manually added user namespace
      // token.
      Matcher matcher = Pattern.compile("[^/|:]/").matcher(url);
      if (matcher.find()) {
        String serverUrl = url.substring(0, url.indexOf(matcher.group()) + 1);
        try {
          Optional<PersonalAccessToken> token =
              personalAccessTokenManager.get(
                  EnvironmentContext.getCurrent().getSubject(), serverUrl, false);
          if (token.isPresent()) {
            PersonalAccessToken accessToken = token.get();
            return accessToken.getScmTokenName().equals("gitlab");
          }
        } catch (ScmConfigurationPersistenceException
            | ScmUnauthorizedException
            | ScmCommunicationException exception) {
          return false;
        }
      }
    }
    return false;
  }

  /**
   * Parses url-s like https://gitlab.apps.cluster-327a.327a.example.opentlc.com/root/proj1.git into
   * {@link GitlabUrl} objects.
   */
  public GitlabUrl parse(String url) {

    if (gitlabUrlPatterns.isEmpty()) {
      String trimmedEndpoint = StringUtils.trimEnd(url, '/');
      Matcher matcher = Pattern.compile("[^/|:]/").matcher(url);
      if (matcher.find()) {
        String serverUrl =
            trimmedEndpoint.substring(0, trimmedEndpoint.indexOf(matcher.group()) + 1);
        Optional<Matcher> optional =
            gitlabUrlPatternTemplates.stream()
                .map(t -> Pattern.compile(format(t, serverUrl)).matcher(url))
                .filter(Matcher::matches)
                .findAny();
        if (optional.isPresent()) {
          return parse(optional.get());
        }
      }
      throw new UnsupportedOperationException(
          "The gitlab integration is not configured properly and cannot be used at this moment."
              + "Please refer to docs to check the Gitlab integration instructions");
    }

    Matcher matcher =
        gitlabUrlPatterns.stream()
            .map(pattern -> pattern.matcher(url))
            .filter(Matcher::matches)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        format(
                            "The given url %s is not a valid Gitlab server URL. Check either URL or server configuration.",
                            url)));
    return parse(matcher);
  }

  private GitlabUrl parse(Matcher matcher) {
    String host = matcher.group("host");
    String userName = matcher.group("user");
    String project = matcher.group("project");
    String repository = null;
    String branch = null;
    String subfolder = null;
    try {
      repository = matcher.group("repository");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }
    try {
      branch = matcher.group("branch");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }
    try {
      subfolder = matcher.group("subfolder");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }

    return new GitlabUrl()
        .withHostName(host)
        .withUsername(userName)
        .withProject(project)
        .withRepository(repository)
        .withBranch(branch)
        .withSubfolder(subfolder)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames());
  }
}
