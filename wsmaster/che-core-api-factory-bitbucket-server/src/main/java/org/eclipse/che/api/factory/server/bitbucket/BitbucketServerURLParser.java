/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
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
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;

import com.google.common.base.Splitter;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
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
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.StringUtils;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.eclipse.che.security.oauth1.BitbucketServerOAuthAuthenticator;

/**
 * Parser of String Bitbucket Server URLs and provide {@link BitbucketServerUrl} objects.
 *
 * @author Max Shaposhnyk
 */
@Singleton
public class BitbucketServerURLParser {

  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final OAuthAPI oAuthAPI;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private static final List<String> bitbucketUrlPatternTemplates =
      List.of(
          "^(?<scheme>%s)://(?<host>%s)/scm/~(?<user>[^/]+)/(?<repo>.*).git$",
          "^(?<scheme>%s)://(?<host>%s)/users/(?<user>[^/]+)/repos/(?<repo>[^/]+)/browse(\\?at=(?<branch>.*))?",
          "^(?<scheme>%s)://(?<host>%s)/users/(?<user>[^/]+)/repos/(?<repo>[^/]+)/?",
          "^(?<scheme>%s)://(?<host>%s)/scm/(?<project>[^/~]+)/(?<repo>[^/]+).git",
          "^(?<scheme>%s)://(?<host>%s)/projects/(?<project>[^/]+)/repos/(?<repo>[^/]+)/browse(\\?at=(?<branch>.*))?",
          "^(?<scheme>%s)://git@(?<host>%s):(?<port>\\d*)/~(?<user>[^/]+)/(?<repo>.*).git$",
          "^(?<scheme>%s)://git@(?<host>%s):(?<port>\\d*)/(?<project>[^/]+)/(?<repo>.*).git$");
  private final List<Pattern> bitbucketUrlPatterns = new ArrayList<>();
  private static final String OAUTH_PROVIDER_NAME = "bitbucket-server";

  @Inject
  public BitbucketServerURLParser(
      @Nullable @Named("che.integration.bitbucket.server_endpoints") String bitbucketEndpoints,
      DevfileFilenamesProvider devfileFilenamesProvider,
      OAuthAPI oAuthAPI,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.oAuthAPI = oAuthAPI;
    this.personalAccessTokenManager = personalAccessTokenManager;
    if (!isNullOrEmpty(bitbucketEndpoints)) {
      for (String bitbucketEndpoint : Splitter.on(",").split(bitbucketEndpoints)) {
        String trimmedEndpoint = StringUtils.trimEnd(bitbucketEndpoint, '/');
        URI uri = URI.create(trimmedEndpoint);
        bitbucketUrlPatternTemplates.forEach(
            t -> {
              String scheme = t.contains("git@") ? "ssh" : uri.getScheme();
              String host = uri.getHost() + uri.getPath();
              bitbucketUrlPatterns.add(Pattern.compile(format(t, scheme, host)));
            });
      }
    }
  }

  private boolean isUserTokenPresent(String repositoryUrl) {
    String serverUrl = getServerUrl(repositoryUrl);
    URI uri = URI.create(repositoryUrl);
    String schema = uri.getScheme();
    String host = uri.getHost();
    if (bitbucketUrlPatternTemplates.stream()
        .anyMatch(t -> Pattern.compile(format(t, schema, host)).matcher(repositoryUrl).matches())) {
      try {
        Optional<PersonalAccessToken> token =
            personalAccessTokenManager.get(EnvironmentContext.getCurrent().getSubject(), serverUrl);
        return token.isPresent() && token.get().getScmTokenName().equals(OAUTH_PROVIDER_NAME);
      } catch (ScmConfigurationPersistenceException | ScmCommunicationException exception) {
        return false;
      }
    }
    return false;
  }

  public boolean isValid(@NotNull String url) {
    if (!url.contains("://")) {
      return false;
    } else if (!bitbucketUrlPatterns.isEmpty()) {
      return bitbucketUrlPatterns.stream().anyMatch(pattern -> pattern.matcher(url).matches());
    } else {
      return
      // If Bitbucket server URL is not configured try to find it in a manually added user namespace
      // token.
      isUserTokenPresent(url)
          // Try to call an API request to see if the URL matches Bitbucket.
          || isApiRequestRelevant(url);
    }
  }

  private boolean isApiRequestRelevant(String repositoryUrl) {
    try {
      HttpBitbucketServerApiClient bitbucketServerApiClient =
          new HttpBitbucketServerApiClient(
              getServerUrl(repositoryUrl),
              new BitbucketServerOAuthAuthenticator("", "", "", ""),
              oAuthAPI,
              "");
      // If the user request catches the unauthorised error, it means that the provided url
      // belongs to Bitbucket.
      bitbucketServerApiClient.getUser();
    } catch (ScmItemNotFoundException | ScmCommunicationException e) {
      return false;
    } catch (ScmUnauthorizedException e) {
      return true;
    }
    return false;
  }

  private String getServerUrl(String repositoryUrl) {
    if (repositoryUrl.startsWith("ssh://git@")) {
      String substring = repositoryUrl.substring(10);
      return "https://" + substring.substring(0, substring.indexOf(":"));
    }
    return repositoryUrl.substring(
        0,
        repositoryUrl.indexOf("/scm") > 0
            ? repositoryUrl.indexOf("/scm")
            : repositoryUrl.indexOf("/users") > 0
                ? repositoryUrl.indexOf("/users")
                : repositoryUrl.indexOf("/projects") > 0
                    ? repositoryUrl.indexOf("/projects")
                    : repositoryUrl.length());
  }

  private Optional<Matcher> getPatternMatcherByUrl(String url) {
    URI uri = URI.create(url);
    String scheme = uri.getScheme();
    String host = uri.getHost();
    return bitbucketUrlPatternTemplates.stream()
        .map(t -> compile(format(t, scheme, host)).matcher(url))
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
    return parse(matcher).withUrl(url);
  }

  private BitbucketServerUrl parse(Matcher matcher) {
    String scheme = matcher.group("scheme");
    String host = matcher.group("host");
    String port = null;
    try {
      port = matcher.group("port");
    } catch (IllegalArgumentException e) {
      // keep port with null, as the pattern doesn't have the port group
    }
    String user = null;
    String project = null;
    try {
      user = matcher.group("user");
    } catch (IllegalArgumentException e) {
      project = matcher.group("project");
    }
    String repoName = matcher.group("repo");
    String branch = null;
    try {
      branch = matcher.group("branch");
    } catch (IllegalArgumentException e) {
      // keep branch with null, as the pattern doesn't have the branch group
    }

    return new BitbucketServerUrl()
        .withScheme(scheme)
        .withHostName(host)
        .withPort(port)
        .withProject(project)
        .withUser(user)
        .withRepository(repoName)
        .withBranch(branch)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames());
  }
}
