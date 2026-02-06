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
package org.eclipse.che.api.factory.server.gitlab;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.eclipse.che.commons.annotation.Nullable;
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
          "^(?<scheme>%s)://(?<host>%s)(:(?<port>%s))?/(?<subgroups>([^/]++/?)+)/-/tree/(?<branch>.++)(/)?",
          "^(?<scheme>%s)://(?<host>%s)(:(?<port>%s))?/(?<subgroups>.*)"); // a wider one, should be
  // the last in
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
          t -> gitlabUrlPatterns.add(compile(format(t, "https", "gitlab.com", 443))));
      gitlabUrlPatterns.add(compile(format(gitlabSSHPatternTemplate, "gitlab.com")));
    } else {
      String trimmedEndpoint = trimEnd(serverUrl, '/');
      URI uri = URI.create(trimmedEndpoint);
      String schema = uri.getScheme();
      // We support GitLab endpoints that include an extra path segment, e.g.:
      //   https://gitlab-server.com/scm
      // and want to match repo URLs like:
      //   https://gitlab-server.com/scm/group/project.git
      //
      // In that case, we treat "/scm" as part of the fixed endpoint prefix, not as part of the
      // repository path.
      // Review feedback: use "hostname" naming instead of "endpointWithoutScheme".
      final String hostname = uri.getAuthority();
      final String endpointPath = uri.getPath() == null ? "" : uri.getPath();

      // What if URI#getAuthority() is null? Fallback to string parsing.
      final String authority;
      if (hostname != null) {
        authority = hostname;
      } else {
        final int schemeSeparatorIdx = trimmedEndpoint.indexOf("://");
        final String withoutScheme =
            schemeSeparatorIdx >= 0
                ? trimmedEndpoint.substring(schemeSeparatorIdx + 3)
                : trimmedEndpoint;
        final int firstSlashIdx = withoutScheme.indexOf('/');
        authority = firstSlashIdx >= 0 ? withoutScheme.substring(0, firstSlashIdx) : withoutScheme;
      }

      // authority may be:
      // - gitlab-server.com
      // - gitlab-server.com:8443
      // - [2001:db8::1]
      // - [2001:db8::1]:8443
      final String hostOnly;
      if (authority.startsWith("[")) {
        int closingBracket = authority.indexOf(']');
        hostOnly = closingBracket > 0 ? authority.substring(0, closingBracket + 1) : authority;
      } else {
        int colonIdx = authority.indexOf(':');
        hostOnly = colonIdx > 0 ? authority.substring(0, colonIdx) : authority;
      }

      // HTTP(S) patterns should match the configured endpoint prefix (including any extra path
      // segment). SSH pattern should match host only.
      // For HTTP(S) patterns we include any configured endpointPath and keep the port (if any) as a
      // part of the "host" group. This allows GitlabUrl#getProviderUrl() to return values like
      // "https://host:8443/scm" even though GitlabUrl models "host" and "port" separately.
      final String httpHostForRegex = Pattern.quote(authority + endpointPath);
      final String sshHostForRegex = Pattern.quote(hostOnly);
      for (String gitlabUrlPatternTemplate : gitlabUrlPatternTemplates) {
        gitlabUrlPatterns.add(
            compile(format(gitlabUrlPatternTemplate, schema, httpHostForRegex, uri.getPort())));
      }
      gitlabUrlPatterns.add(compile(format(gitlabSSHPatternTemplate, sshHostForRegex)));
    }
  }

  private boolean isUserTokenPresent(String repositoryUrl) {
    Optional<String> serverUrlOptional = getServerUrl(repositoryUrl);
    if (serverUrlOptional.isPresent()) {
      String serverUrl = serverUrlOptional.get();
      try {
        Optional<PersonalAccessToken> token =
            personalAccessTokenManager.get(
                EnvironmentContext.getCurrent().getSubject(), null, serverUrl, null);
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
        // Some Git providers e.g. Azure Devops Server, may return unauthorized exception on invalid
        // API request, but Gitlab API returns unauthorized error message in JSON format, so to be
        // sure that the URL belongs to Gitlab, we need to check if the error message is a valid
        // JSON.
        return isJsonValid(e.getMessage());
      } catch (ScmItemNotFoundException | IllegalArgumentException | ScmCommunicationException e) {
        return false;
      }
    }
    return false;
  }

  private boolean isJsonValid(String message) {
    try {
      JsonObject unused = new JsonParser().parse(message).getAsJsonObject();
      return true;
    } catch (Exception exception) {
      return false;
    }
  }

  private Optional<Matcher> getPatternMatcherByUrl(String url) {
    URI uri =
        URI.create(
            url.matches(format(gitlabSSHPatternTemplate, ".*"))
                ? "ssh://" + url.replace(":", "/")
                : url);
    String scheme = uri.getScheme();
    String host = uri.getHost();
    // Handle IPv6 addresses: escape brackets for regex
    final String hostForRegex;
    if (host != null && host.contains(":")) {
      // IPv6 address - URLs contain host in square brackets.
      hostForRegex = "\\[" + Pattern.quote(host) + "\\]";
    } else if (host != null) {
      // Regular hostname - escape special regex characters
      hostForRegex = Pattern.quote(host);
    } else {
      hostForRegex = "";
    }
    return gitlabUrlPatternTemplates.stream()
        .map(t -> compile(format(t, scheme, hostForRegex, uri.getPort())).matcher(url))
        .filter(Matcher::matches)
        .findAny()
        .or(
            () -> {
              Matcher matcher =
                  compile(format(gitlabSSHPatternTemplate, hostForRegex)).matcher(url);
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
    // Use URI parsing to properly handle IPv6 addresses
    try {
      URI uri = URI.create(repositoryUrl);
      if (uri.getScheme() != null && uri.getHost() != null) {
        String authority = uri.getRawAuthority();
        if (authority == null) {
          String host = uri.getHost();
          boolean ipv6 = host != null && host.contains(":");
          String hostForUrl = ipv6 ? "[" + host + "]" : host;
          int port = uri.getPort();
          authority = port == -1 ? hostForUrl : hostForUrl + ":" + port;
        }

        if (authority != null) {
          String serverUrl = uri.getScheme() + "://" + authority;
          // Remove path and query from the server URL
          int authorityIdx = repositoryUrl.indexOf(authority);
          if (authorityIdx >= 0) {
            int pathIndex = authorityIdx + authority.length();
            if (pathIndex < repositoryUrl.length() && repositoryUrl.charAt(pathIndex) == '/') {
              return Optional.of(serverUrl);
            }
          }
        }
      }
    } catch (IllegalArgumentException e) {
      // Fall through to old logic if URI parsing fails
    }
    // Fallback for non-standard URLs
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
  public GitlabUrl parse(String url, @Nullable String revision) {
    String trimmedUrl = trimEnd(url, '/');
    Optional<Matcher> matcherOptional =
        gitlabUrlPatterns.stream()
            .map(pattern -> pattern.matcher(trimmedUrl))
            .filter(Matcher::matches)
            .findFirst()
            .or(() -> getPatternMatcherByUrl(trimmedUrl));
    if (matcherOptional.isPresent()) {
      return parse(matcherOptional.get(), revision).withUrl(trimmedUrl);
    } else {
      throw new UnsupportedOperationException(
          "The gitlab integration is not configured properly and cannot be used at this moment."
              + "Please refer to docs to check the Gitlab integration instructions");
    }
  }

  private GitlabUrl parse(Matcher matcher, @Nullable String revision) {
    String scheme = null;
    String port = null;
    try {
      scheme = matcher.group("scheme");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }
    String host = matcher.group("host");
    try {
      port = matcher.group("port");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }
    String subGroups = trimEnd(matcher.group("subgroups"), '/');
    if (subGroups.endsWith(".git")) {
      subGroups = subGroups.substring(0, subGroups.length() - 4);
    }

    String branchFromUrl = null;
    try {
      branchFromUrl = matcher.group("branch");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }

    return new GitlabUrl()
        .withHostName(host)
        .withPort(port)
        .withScheme(scheme)
        .withSubGroups(subGroups)
        .withBranch(isNullOrEmpty(branchFromUrl) ? revision : branchFromUrl)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames());
  }
}
