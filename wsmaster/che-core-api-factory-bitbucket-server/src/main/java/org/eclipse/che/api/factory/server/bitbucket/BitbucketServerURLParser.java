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
package org.eclipse.che.api.factory.server.bitbucket;

import static java.lang.String.format;
import static java.util.regex.Pattern.compile;

import com.google.common.base.Splitter;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
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
 * Parser of String Bitbucket Server URLs and provide {@link BitbucketServerUrl} objects.
 *
 * @author Max Shaposhnyk
 */
@Singleton
public class BitbucketServerURLParser {

  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private static final List<String> bitbucketUrlPatternTemplates =
      List.of(
          "^(?<host>%s)/scm/~(?<user>[^/]+)/(?<repo>.*).git$",
          "^(?<host>%s)/users/(?<user>[^/]+)/repos/(?<repo>[^/]+)/browse(\\?at=(?<branch>.*))?",
          "^(?<host>%s)/scm/(?<project>[^/~]+)/(?<repo>[^/]+).git",
          "^(?<host>%s)/projects/(?<project>[^/]+)/repos/(?<repo>[^/]+)/browse(\\?at=(?<branch>.*))?");
  private final List<Pattern> bitbucketUrlPatterns = new ArrayList<>();
  private static final String OAUTH_PROVIDER_NAME = "bitbucket-server";

  @Inject
  public BitbucketServerURLParser(
      @Nullable @Named("che.integration.bitbucket.server_endpoints") String bitbucketEndpoints,
      DevfileFilenamesProvider devfileFilenamesProvider,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.personalAccessTokenManager = personalAccessTokenManager;
    if (bitbucketEndpoints != null) {
      for (String bitbucketEndpoint : Splitter.on(",").split(bitbucketEndpoints)) {
        String trimmedEndpoint = StringUtils.trimEnd(bitbucketEndpoint, '/');
        bitbucketUrlPatternTemplates.forEach(
            t -> bitbucketUrlPatterns.add(Pattern.compile(format(t, trimmedEndpoint))));
      }
    }
  }

  private boolean isUserTokenPresent(String repositoryUrl) {
    String serverUrl = getServerUrl(repositoryUrl);
    if (bitbucketUrlPatternTemplates.stream()
        .anyMatch(t -> Pattern.compile(format(t, serverUrl)).matcher(repositoryUrl).matches())) {
      try {
        Optional<PersonalAccessToken> token =
            personalAccessTokenManager.get(EnvironmentContext.getCurrent().getSubject(), serverUrl);
        return token.isPresent() && token.get().getScmTokenName().equals(OAUTH_PROVIDER_NAME);
      } catch (ScmConfigurationPersistenceException
          | ScmUnauthorizedException
          | ScmCommunicationException exception) {
        return false;
      }
    }
    return false;
  }

  public boolean isValid(@NotNull String url) {
    if (!bitbucketUrlPatterns.isEmpty()) {
      return bitbucketUrlPatterns.stream().anyMatch(pattern -> pattern.matcher(url).matches());
    } else {
      // If Bitbucket server URL is not configured try to find it in a manually added user namespace
      // token.
      return isUserTokenPresent(url);
    }
  }

  private String getServerUrl(String repositoryUrl) {
    return repositoryUrl.substring(
        0,
        repositoryUrl.indexOf("/scm") > 0 ? repositoryUrl.indexOf("/scm") : repositoryUrl.length());
  }

  private Optional<Matcher> getPatternMatcherByUrl(String url) {
    return bitbucketUrlPatternTemplates.stream()
        .map(t -> compile(format(t, getServerUrl(url))).matcher(url))
        .filter(Matcher::matches)
        .findAny();
  }

  /**
   * Parses url-s like
   * https://bitbucket.apps.cluster-cb82.cb82.example.opentlc.com/scm/test/test1.git into
   * BitbucketUrl objects.
   */
  public BitbucketServerUrl parse(String url) {

    if (bitbucketUrlPatterns.isEmpty()) {
      Optional<Matcher> matcherOptional = getPatternMatcherByUrl(url);
      if (matcherOptional.isPresent()) {
        return parse(matcherOptional.get());
      }
      throw new UnsupportedOperationException(
          "The Bitbucket integration is not configured properly and cannot be used at this moment."
              + "Please refer to docs to check the Bitbucket integration instructions");
    }

    Matcher matcher =
        bitbucketUrlPatterns.stream()
            .map(pattern -> pattern.matcher(url))
            .filter(Matcher::matches)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        format(
                            "The given url %s is not a valid Bitbucket server URL. Check either URL or server configuration.",
                            url)));
    return parse(matcher);
  }

  private BitbucketServerUrl parse(Matcher matcher) {
    String host = matcher.group("host");
    String user = matcher.toString().contains("user") ? matcher.group("user") : null;
    String project = matcher.toString().contains("project") ? matcher.group("project") : null;
    String repoName = matcher.group("repo");
    String branch = matcher.toString().contains("branch") ? matcher.group("branch") : null;

    return new BitbucketServerUrl()
        .withHostName(host)
        .withProject(project)
        .withUser(user)
        .withRepository(repoName)
        .withBranch(branch)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames());
  }
}
