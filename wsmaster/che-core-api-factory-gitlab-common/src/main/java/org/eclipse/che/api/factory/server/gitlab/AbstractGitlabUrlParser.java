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
package org.eclipse.che.api.factory.server.gitlab;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Parser of String Gitlab URLs and provide {@link GitlabUrl} objects.
 *
 * @author Max Shaposhnyk
 */
public class AbstractGitlabUrlParser {

  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private final String providerName;
  private static final List<String> gitlabUrlPatternTemplates =
      List.of(
          "^(?<scheme>%s)://(?<host>%s)/(?<subgroups>([^/]++/?)+)/-/tree/(?<branch>.++)(/)?",
          "^(?<scheme>%s)://(?<host>%s)/(?<subgroups>.*)"); // a wider one, should be the last in
  // the list
  private final String gitlabSSHPatternTemplate = "^git@(?<host>%s):(?<subgroups>.*)$";
  // list
  private final List<Pattern> gitlabUrlPatterns = new ArrayList<>();

  public AbstractGitlabUrlParser(
      String serverUrl,
      DevfileFilenamesProvider devfileFilenamesProvider,
      PersonalAccessTokenManager personalAccessTokenManager,
      String providerName) {
    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.providerName = providerName;
    if (isNullOrEmpty(serverUrl)) {
      gitlabUrlPatternTemplates.forEach(
          t -> gitlabUrlPatterns.add(compile(format(t, "https", "gitlab.com"))));
      gitlabUrlPatterns.add(compile(format(gitlabSSHPatternTemplate, "gitlab.com")));
    } else {
      String trimmedEndpoint = trimEnd(serverUrl, '/');
      URI uri = URI.create(trimmedEndpoint);
      String schema = uri.getScheme();
      String host = uri.getHost();
      for (String gitlabUrlPatternTemplate : gitlabUrlPatternTemplates) {
        gitlabUrlPatterns.add(compile(format(gitlabUrlPatternTemplate, schema, host)));
      }
      gitlabUrlPatterns.add(compile(format(gitlabSSHPatternTemplate, host)));
    }
  }

  private boolean isUserTokenPresent(String repositoryUrl) {
    Optional<String> serverUrlOptional = getServerUrl(repositoryUrl);
    if (serverUrlOptional.isPresent()) {
      String serverUrl = serverUrlOptional.get();
      try {
        Optional<PersonalAccessToken> token =
            personalAccessTokenManager.get(EnvironmentContext.getCurrent().getSubject(), serverUrl);
        if (token.isPresent()) {
          PersonalAccessToken accessToken = token.get();
          return accessToken.getScmTokenName().equals(providerName);
        }
      } catch (ScmConfigurationPersistenceException | ScmCommunicationException exception) {
        return false;
      }
    }
    return false;
  }

  public boolean isValid(@NotNull String url) {
    return gitlabUrlPatterns.stream()
            .anyMatch(pattern -> pattern.matcher(trimEnd(url, '/')).matches())
        // If the Gitlab URL is not configured, try to find it in a manually added user namespace
        // token.
        || isUserTokenPresent(url)
        // Try to call an API request to see if the URL matches Gitlab.
        || isApiRequestRelevant(url);
  }

  private boolean isApiRequestRelevant(String repositoryUrl) {
    Optional<String> serverUrlOptional = getServerUrl(repositoryUrl);
    if (serverUrlOptional.isPresent()) {
      GitlabApiClient gitlabApiClient = new GitlabApiClient(serverUrlOptional.get());
      try {
        // If the token request catches the unauthorised error, it means that the provided url
        // belongs to Gitlab.
        gitlabApiClient.getOAuthTokenInfo("");
      } catch (ScmUnauthorizedException e) {
        return true;
      } catch (ScmItemNotFoundException | IllegalArgumentException | ScmCommunicationException e) {
        return false;
      }
    }
    return false;
  }

  private Optional<Matcher> getPatternMatcherByUrl(String url) {
    URI uri =
        URI.create(
            url.matches(format(gitlabSSHPatternTemplate, ".*"))
                ? "ssh://" + url.replace(":", "/")
                : url);
    String scheme = uri.getScheme();
    String host = uri.getHost();
    return gitlabUrlPatternTemplates.stream()
        .map(t -> compile(format(t, scheme, host)).matcher(url))
        .filter(Matcher::matches)
        .findAny()
        .or(
            () -> {
              Matcher matcher = compile(format(gitlabSSHPatternTemplate, host)).matcher(url);
              if (matcher.matches()) {
                return Optional.of(matcher);
              }
              return Optional.empty();
            });
  }

  private Optional<String> getServerUrl(String repositoryUrl) {
    if (repositoryUrl.startsWith("git@")) {
      String substring = repositoryUrl.substring(4);
      return Optional.of("https://" + substring.substring(0, substring.indexOf(":")));
    }
    Matcher serverUrlMatcher = compile("[^/|:]/").matcher(repositoryUrl);
    if (serverUrlMatcher.find()) {
      return Optional.of(
          repositoryUrl.substring(0, repositoryUrl.indexOf(serverUrlMatcher.group()) + 1));
    }
    return Optional.empty();
  }

  /**
   * Parses url-s like https://gitlab.apps.cluster-327a.327a.example.opentlc.com/root/proj1.git into
   * {@link GitlabUrl} objects.
   */
  public GitlabUrl parse(String url) {
    String trimmedUrl = trimEnd(url, '/');
    Optional<Matcher> matcherOptional =
        gitlabUrlPatterns.stream()
            .map(pattern -> pattern.matcher(trimmedUrl))
            .filter(Matcher::matches)
            .findFirst()
            .or(() -> getPatternMatcherByUrl(trimmedUrl));
    if (matcherOptional.isPresent()) {
      return parse(matcherOptional.get()).withUrl(trimmedUrl);
    } else {
      throw new UnsupportedOperationException(
          "The gitlab integration is not configured properly and cannot be used at this moment."
              + "Please refer to docs to check the Gitlab integration instructions");
    }
  }

  private GitlabUrl parse(Matcher matcher) {
    String scheme = null;
    try {
      scheme = matcher.group("scheme");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }
    String host = matcher.group("host");
    String subGroups = trimEnd(matcher.group("subgroups"), '/');
    if (subGroups.endsWith(".git")) {
      subGroups = subGroups.substring(0, subGroups.length() - 4);
    }

    String branch = null;
    try {
      branch = matcher.group("branch");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }

    return new GitlabUrl()
        .withHostName(host)
        .withScheme(scheme)
        .withSubGroups(subGroups)
        .withBranch(branch)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames());
  }
}
