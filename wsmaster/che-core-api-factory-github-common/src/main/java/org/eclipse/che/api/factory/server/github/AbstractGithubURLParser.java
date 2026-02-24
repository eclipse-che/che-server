/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;
import static org.eclipse.che.api.factory.server.ApiExceptionMapper.toApiException;
import static org.eclipse.che.api.factory.server.github.GithubApiClient.GITHUB_SAAS_ENDPOINT;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.*;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser of String Github URLs and provide {@link GithubUrl} objects.
 *
 * @author Florent Benoit
 */
public abstract class AbstractGithubURLParser {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractGithubURLParser.class);
  private final PersonalAccessTokenManager tokenManager;
  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final GithubApiClient apiClient;

  /**
   * Regexp to find repository details (repository name, project name and branch and subfolder)
   * Examples of valid URLs are in the test class.
   */
  private final Pattern githubPattern;

  private final String githubPatternTemplate =
      "%s/(?<repoUser>[^/]+)/(?<repoName>[^/]++)((/)|(?:/((tree)|(blob))/(?<path>.++))|(/pull/(?<pullRequestId>\\d++)))?$";

  private final Pattern githubSSHPattern;

  private final String githubSSHPatternTemplate = "^git@%s:(?<repoUser>.*)/(?<repoName>.*)$";

  private final boolean disableSubdomainIsolation;

  private final String providerName;
  private final String endpoint;
  private final boolean isGitHubServer;

  /** Constructor used for testing only. */
  AbstractGithubURLParser(
      PersonalAccessTokenManager tokenManager,
      DevfileFilenamesProvider devfileFilenamesProvider,
      GithubApiClient githubApiClient,
      String oauthEndpoint,
      boolean disableSubdomainIsolation,
      String providerName) {
    this.tokenManager = tokenManager;
    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.apiClient = githubApiClient;
    this.disableSubdomainIsolation = disableSubdomainIsolation;
    this.providerName = providerName;
    // Check if the given OAuth endpoint is a GitHub server URL. If the OAuth endpoint is not
    // defined, or it equals the GitHub SaaS endpoint, it means that the given URL is a GitHub SaaS
    // URL (https://github.com). Otherwise, the given URL is a GitHub server URL.
    this.isGitHubServer =
        !isNullOrEmpty(oauthEndpoint) && !GITHUB_SAAS_ENDPOINT.equals(oauthEndpoint);

    endpoint = isNullOrEmpty(oauthEndpoint) ? GITHUB_SAAS_ENDPOINT : trimEnd(oauthEndpoint, '/');

    this.githubPattern = compile(format(githubPatternTemplate, endpoint));
    this.githubSSHPattern =
        compile(format(githubSSHPatternTemplate, URI.create(endpoint).getHost()));
  }

  // Check if the given URL is a valid GitHub URL.
  public boolean isValid(@NotNull String url) {
    String trimmedUrl = trimEnd(url, '/');
    return
    // Check if the given URL matches the GitHub URL patterns. It works if OAuth is configured and
    // GitHub server URL is known or the repository URL points to GitHub SaaS (https://github.com).
    githubPattern.matcher(trimmedUrl).matches()
        || githubSSHPattern.matcher(trimmedUrl).matches()
        // Check whether PAT is configured for the GitHub server URL. It is sufficient to confirm
        // that the URL is a valid GitHub URL.
        || isUserTokenPresent(trimmedUrl)
        // Check if the given URL is a valid GitHub URL by reaching the endpoint of the GitHub
        // server and analysing the response. This query basically only needs to be performed if the
        // specified repository URL does not point to GitHub SaaS.
        || (!isGitHubServer && isApiRequestRelevant(trimmedUrl));
  }

  // Try to find the given url in a manually added user namespace token secret.
  private boolean isUserTokenPresent(String repositoryUrl) {
    Optional<String> serverUrlOptional = getServerUrl(repositoryUrl);
    if (serverUrlOptional.isPresent()) {
      String serverUrl = serverUrlOptional.get();
      try {
        Optional<PersonalAccessToken> token =
            tokenManager.get(EnvironmentContext.getCurrent().getSubject(), null, serverUrl, null);
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

  // Try to call an API request to see if the given url matches self-hosted GitHub Enterprise.
  private boolean isApiRequestRelevant(String repositoryUrl) {
    Optional<String> serverUrlOptional = getServerUrl(repositoryUrl);
    if (serverUrlOptional.isPresent()) {
      String serverUrl = serverUrlOptional.get();
      GithubApiClient githubApiClient = new GithubApiClient(serverUrl);
      try {
        // If the user request catches the unauthorised error, it means that the provided url
        // belongs to GitHub.
        githubApiClient.getUser("");
      } catch (ScmUnauthorizedException e) {
        // Check the error message as well, because other providers might also return 401
        // for such requests.
        return e.getMessage().contains("Requires authentication")
            || // for older GitHub Enterprise versions
            e.getMessage().contains("Must authenticate to access this API.");
      } catch (ScmItemNotFoundException e) {
        // GitHub API v3 (/api/v3/user) returned 404 — this server does not expose the GitHub
        // v3 API path. Fall back to checking the Gitea-compatible API (/api/v1/user): if that
        // endpoint returns 401 the server is a GitHub-compatible instance (e.g. Gitea).
        return isGiteaCompatibleServer(serverUrl);
      } catch (ScmBadRequestException | IllegalArgumentException | ScmCommunicationException e) {
        return false;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} when the server at {@code serverUrl} exposes a Gitea-style GitHub
   * compatible API ({@code /api/v1/user}) and requires authentication (HTTP 401).
   */
  private boolean isGiteaCompatibleServer(String serverUrl) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(serverUrl + "/api/v1/user"))
              .timeout(Duration.ofSeconds(10))
              .GET()
              .build();
      HttpResponse<Void> response =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 401;
    } catch (Exception e) {
      LOG.debug("Failed to reach Gitea API at {}: {}", serverUrl, e.getMessage());
      return false;
    }
  }

  private Optional<String> getServerUrl(String repositoryUrl) {
    // If the given repository url is an SSH url, generate the base url from the pattern:
    // https://<hostname extracted from the SSH url>.
    if (repositoryUrl.startsWith("git@")) {
      String substring = repositoryUrl.substring(4);
      // Handle IPv6 addresses in SSH URLs (e.g., git@[2001:db8::1]:repo)
      if (substring.startsWith("[")) {
        int closingBracket = substring.indexOf(']');
        if (closingBracket > 0) {
          return Optional.of("https://" + substring.substring(0, closingBracket + 1));
        }
      }
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
    // Otherwise, extract the base url from the given repository url by cutting the url after the
    // first slash.
    Matcher serverUrlMatcher = compile("[^/|:]/").matcher(repositoryUrl);
    if (serverUrlMatcher.find()) {
      return Optional.of(
          repositoryUrl.substring(0, repositoryUrl.indexOf(serverUrlMatcher.group()) + 1));
    }
    return Optional.empty();
  }

  public GithubUrl parseWithoutAuthentication(String url, @Nullable String revision)
      throws ApiException {
    return parse(trimEnd(url, '/'), false, revision);
  }

  public GithubUrl parse(String url, @Nullable String revision) throws ApiException {
    return parse(trimEnd(url, '/'), true, revision);
  }

  private IllegalArgumentException buildIllegalArgumentException(String url) {
    return new IllegalArgumentException(
        format("The given url %s is not a valid github URL. ", url));
  }

  private GithubUrl parse(String url, boolean authenticationRequired, @Nullable String revision)
      throws ApiException {
    Matcher matcher;
    boolean isHTTPSUrl = githubPattern.matcher(url).matches();
    if (isHTTPSUrl) {
      matcher = githubPattern.matcher(url);
    } else if (githubSSHPattern.matcher(url).matches()) {
      matcher = githubSSHPattern.matcher(url);
    } else {
      matcher = getPatternMatcherByUrl(url).orElseThrow(() -> buildIllegalArgumentException(url));
      isHTTPSUrl = url.startsWith("http");
    }
    if (!matcher.matches()) {
      throw buildIllegalArgumentException(url);
    }

    String serverUrl = getServerUrl(url).orElseThrow(() -> buildIllegalArgumentException(url));
    String repoUser = matcher.group("repoUser");
    String repoName = matcher.group("repoName");
    if (repoName.matches("^[\\w-][\\w.-]*?\\.git$")) {
      repoName = repoName.substring(0, repoName.length() - 4);
    }

    String branchFromUrl = null;
    String pullRequestId = null;
    if (isHTTPSUrl) {
      branchFromUrl = getBranch(matcher.group("path"));
      pullRequestId = matcher.group("pullRequestId");
    }

    if (pullRequestId != null) {
      GithubPullRequest pullRequest =
          this.getPullRequest(serverUrl, pullRequestId, repoUser, repoName, authenticationRequired);
      if (pullRequest != null) {
        String state = pullRequest.getState();
        if (!"open".equalsIgnoreCase(state)) {
          throw new IllegalArgumentException(
              format(
                  "The given Pull Request url %s is not Opened, (found %s), thus it can't be opened as branch may have been removed.",
                  url, state));
        }

        GithubHead pullRequestHead = pullRequest.getHead();
        repoUser = pullRequestHead.getUser().getLogin();
        repoName = pullRequestHead.getRepo().getName();
        branchFromUrl = pullRequestHead.getRef();
      }
    }

    String latestCommit = null;
    GithubCommit commit =
        getLatestCommit(
            serverUrl,
            repoUser,
            repoName,
            isNullOrEmpty(revision) ? firstNonNull(branchFromUrl, "HEAD") : revision,
            authenticationRequired);
    if (commit != null) {
      latestCommit = commit.getSha();
    }

    return new GithubUrl(providerName)
        .withUsername(repoUser)
        .withRepository(repoName)
        .setIsHTTPSUrl(isHTTPSUrl)
        .withServerUrl(serverUrl)
        .withDisableSubdomainIsolation(disableSubdomainIsolation)
        .withBranch(isNullOrEmpty(branchFromUrl) ? revision : branchFromUrl)
        .withLatestCommit(latestCommit)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .withUrl(url);
  }

  /*
   * Get branch from the file path by extracting the segment before the devfile name.
   * Example of a file path: "branch/devfile.yaml" => "branch"
   */
  private String getBranch(String path) {
    if (path != null && path.contains("/")) {
      Optional<String> devfileNameOptional =
          devfileFilenamesProvider.getConfiguredDevfileFilenames().stream()
              .filter(devfileName -> path.endsWith("/" + devfileName))
              .findAny();
      return devfileNameOptional.map(s -> path.substring(0, path.indexOf(s) - 1)).orElse(path);
    }
    return path;
  }

  private GithubPullRequest getPullRequest(
      String githubEndpoint,
      String pullRequestId,
      String repoUser,
      String repoName,
      boolean authenticationRequired)
      throws ApiException {
    try {
      // prepare token
      Subject subject = EnvironmentContext.getCurrent().getSubject();
      PersonalAccessToken personalAccessToken = null;
      Optional<PersonalAccessToken> token = tokenManager.get(subject, null, githubEndpoint, null);
      if (token.isPresent()) {
        personalAccessToken = token.get();
      } else if (authenticationRequired) {
        personalAccessToken = tokenManager.fetchAndSave(subject, githubEndpoint);
      }

      GithubApiClient apiClient =
          this.apiClient.isConnected(githubEndpoint)
              ? this.apiClient
              : new GithubApiClient(githubEndpoint);

      // get pull request
      return apiClient.getPullRequest(
          pullRequestId,
          repoUser,
          repoName,
          personalAccessToken != null ? personalAccessToken.getToken() : null);
    } catch (UnknownScmProviderException e) {

      // get pull request without authentication
      try {
        return apiClient.getPullRequest(pullRequestId, repoUser, repoName, null);
      } catch (ScmItemNotFoundException
          | ScmCommunicationException
          | ScmBadRequestException
          | ScmUnauthorizedException exception) {
        LOG.error("Failed to authenticate to GitHub", e);
      }

    } catch (ScmUnauthorizedException e) {
      throw toApiException(e);
    } catch (ScmCommunicationException
        | UnsatisfiedScmPreconditionException
        | ScmConfigurationPersistenceException e) {
      LOG.error("Failed to authenticate to GitHub", e);
    } catch (ScmItemNotFoundException | ScmBadRequestException e) {
      LOG.error("Failed retrieve GitHub Pull Request", e);
    }

    return null;
  }

  private GithubCommit getLatestCommit(
      String githubEndpoint,
      String repoUser,
      String repoName,
      String branchName,
      boolean authenticationRequired) {
    GithubApiClient apiClient =
        this.apiClient.isConnected(githubEndpoint)
            ? this.apiClient
            : new GithubApiClient(githubEndpoint);
    try {
      // prepare token
      Subject subject = EnvironmentContext.getCurrent().getSubject();
      PersonalAccessToken personalAccessToken = null;
      Optional<PersonalAccessToken> token = tokenManager.get(subject, null, githubEndpoint, null);
      if (token.isPresent()) {
        personalAccessToken = token.get();
      } else if (authenticationRequired) {
        personalAccessToken = tokenManager.fetchAndSave(subject, githubEndpoint);
      }

      // get latest commit
      return apiClient.getLatestCommit(
          repoUser,
          repoName,
          branchName,
          personalAccessToken != null ? personalAccessToken.getToken() : null);
    } catch (UnknownScmProviderException | ScmUnauthorizedException e) {
      // get latest commit without authentication
      try {
        return apiClient.getLatestCommit(repoUser, repoName, branchName, null);
      } catch (ScmItemNotFoundException
          | ScmCommunicationException
          | ScmBadRequestException
          | URISyntaxException
          | ScmUnauthorizedException exception) {
        LOG.error("Failed to authenticate to GitHub", e);
      }
    } catch (ScmCommunicationException
        | UnsatisfiedScmPreconditionException
        | ScmConfigurationPersistenceException e) {
      LOG.error("Failed to authenticate to GitHub", e);
    } catch (ScmItemNotFoundException | ScmBadRequestException | URISyntaxException e) {
      LOG.error("Failed to retrieve the latest commit", e);
      e.printStackTrace();
    }

    return null;
  }

  /**
   * Converts an SSH git URL (git@host:path) to a parseable URI (ssh://git@host/path), handling IPv6
   * addresses where colons in the address must not be replaced.
   */
  private static String sshToUri(String url) {
    // Strip "git@" prefix
    String afterAt = url.substring(4);
    if (afterAt.startsWith("[")) {
      // IPv6: git@[2001:db8::1]:path -> ssh://git@[2001:db8::1]/path
      int closingBracket = afterAt.indexOf(']');
      if (closingBracket >= 0 && closingBracket + 1 < afterAt.length()) {
        String host = afterAt.substring(0, closingBracket + 1);
        // Skip the colon separator after the closing bracket
        String path = afterAt.substring(closingBracket + 1);
        if (path.startsWith(":")) {
          path = path.substring(1);
        }
        return "ssh://git@" + host + "/" + path;
      }
    }
    // Regular hostname: git@host:path -> ssh://git@host/path
    return "ssh://" + url.replace(":", "/");
  }

  private Optional<Matcher> getPatternMatcherByUrl(String url) {
    URI uri = URI.create(url.matches(format(githubSSHPatternTemplate, ".*")) ? sshToUri(url) : url);
    String scheme = uri.getScheme();
    String host = uri.getHost(); // IPv6 addresses are returned WITH brackets, e.g. "[fd00::1]"

    // uri.getRawAuthority() already contains the correct authority string (including brackets
    // for IPv6 and the port number), so we can quote it directly to build a literal-match regex.
    String rawAuthority = uri.getRawAuthority();
    final String hostForRegex;
    if (rawAuthority != null) {
      hostForRegex = Pattern.quote(scheme + "://" + rawAuthority);
    } else {
      // Fallback: build from host + port
      String authority = host != null ? host : "";
      if (uri.getPort() > 0) {
        authority += ":" + uri.getPort();
      }
      hostForRegex = Pattern.quote(scheme + "://" + authority);
    }

    Matcher matcher = compile(format(githubPatternTemplate, hostForRegex)).matcher(url);
    if (matcher.matches()) {
      return Optional.of(matcher);
    } else {
      // For SSH git@ URLs the host is used without brackets even for IPv6.
      final String hostOnlyForRegex;
      if (host != null && host.startsWith("[") && host.endsWith("]")) {
        // Strip brackets that uri.getHost() includes for IPv6.
        hostOnlyForRegex = Pattern.quote(host.substring(1, host.length() - 1));
      } else {
        hostOnlyForRegex = host != null ? Pattern.quote(host) : "";
      }
      matcher = compile(format(githubSSHPatternTemplate, hostOnlyForRegex)).matcher(url);
      return matcher.matches() ? Optional.of(matcher) : Optional.empty();
    }
  }
}
