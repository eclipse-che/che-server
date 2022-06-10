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
 * Parser of String Bitbucket URLs and provide {@link BitbucketUrl} objects.
 *
 * @author Max Shaposhnyk
 */
@Singleton
public class BitbucketURLParser {

  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private static final String bitbucketUrlPatternTemplate =
      "^(?<host>%s)/scm/(?<project>[^/]++)/(?<repo>[^.]++).git(\\?at=)?(?<branch>[\\w\\d-_]*)";
  private final List<Pattern> bitbucketUrlPatterns = new ArrayList<>();

  @Inject
  public BitbucketURLParser(
      @Nullable @Named("che.integration.bitbucket.server_endpoints") String bitbucketEndpoints,
      DevfileFilenamesProvider devfileFilenamesProvider,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.personalAccessTokenManager = personalAccessTokenManager;
    if (bitbucketEndpoints != null) {
      for (String bitbucketEndpoint : Splitter.on(",").split(bitbucketEndpoints)) {
        String trimmedEndpoint = StringUtils.trimEnd(bitbucketEndpoint, '/');
        this.bitbucketUrlPatterns.add(
            Pattern.compile(format(bitbucketUrlPatternTemplate, trimmedEndpoint)));
      }
    }
  }

  public boolean isValid(@NotNull String url) {
    if (!bitbucketUrlPatterns.isEmpty()) {
      return bitbucketUrlPatterns.stream().anyMatch(pattern -> pattern.matcher(url).matches());
    } else {
      // If Bitbucket server URL is not configured try to find it in a manually added user namespace
      // token.
      String trimmedUrl = url.substring(0, url.indexOf("/scm"));
      try {
        Optional<PersonalAccessToken> token =
            personalAccessTokenManager.get(
                EnvironmentContext.getCurrent().getSubject(), trimmedUrl);
        return token.isPresent() && token.get().getScmProviderUrl().equals(trimmedUrl);
      } catch (ScmConfigurationPersistenceException
          | ScmUnauthorizedException
          | ScmCommunicationException exception) {
        return false;
      }
    }
  }

  /**
   * Parses url-s like
   * https://bitbucket.apps.cluster-cb82.cb82.example.opentlc.com/scm/test/test1.git into
   * BitbucketUrl objects.
   */
  public BitbucketUrl parse(String url) {
    if (bitbucketUrlPatterns.isEmpty()) {
      String trimmedUrl = url.substring(0, url.indexOf("/scm"));
      try {
        Optional<PersonalAccessToken> token =
            personalAccessTokenManager.get(
                EnvironmentContext.getCurrent().getSubject(), trimmedUrl);
        if (token.isPresent()) {
          Pattern pattern =
              Pattern.compile(format(bitbucketUrlPatternTemplate, token.get().getScmProviderUrl()));
          Matcher matcher = pattern.matcher(url);
          if (matcher.matches()) {
            return parse(matcher);
          }
        }
      } catch (ScmConfigurationPersistenceException
          | ScmUnauthorizedException
          | ScmCommunicationException exception) {
        throw new UnsupportedOperationException("Token is not valid");
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

  private BitbucketUrl parse(Matcher matcher) {
    String host = matcher.group("host");
    String project = matcher.group("project");
    String repoName = matcher.group("repo");
    String branch = matcher.group("branch");

    return new BitbucketUrl()
        .withHostName(host)
        .withProject(project)
        .withRepository(repoName)
        .withBranch(branch)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames());
  }
}
