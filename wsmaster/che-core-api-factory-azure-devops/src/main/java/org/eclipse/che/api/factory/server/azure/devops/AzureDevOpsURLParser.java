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
package org.eclipse.che.api.factory.server.azure.devops;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
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
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Parser of String Azure DevOps URLs and provide {@link AzureDevOpsUrl} objects.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class AzureDevOpsURLParser {

  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final PersonalAccessTokenManager tokenManager;
  private final String azureDevOpsScmApiEndpointHost;
  /**
   * Regexp to find repository details (repository name, organization name and branch or tag)
   * Examples of valid URLs are in the test class.
   */
  private final Pattern azureDevOpsPattern;

  private final Pattern azureSSHDevOpsPattern;
  private final String azureSSHDevOpsPatternTemplate =
      "^git@ssh\\.%s:v3/(?<organization>.*)/(?<project>.*)/(?<repoName>.*)$";
  private final String azureSSHDevOpsServerPatternTemplate =
      "^ssh://%s(:\\d*)?/(?<organization>.*)/(?<project>.*)/_git/(?<repoName>.*)$";
  private final String azureDevOpsPatternTemplate =
      "^https?://(?<organizationCanIgnore>[^@]++)?@?%s/(?<organization>[^/]++)/((?<project>[^/]++)/)?_git/"
          + "(?<repoName>[^?]++)"
          + "([?&]path=(?<path>[^&]++))?"
          + "([?&]version=GT(?<tag>[^&]++))?"
          + "([?&]version=GB(?<branch>[^&]++))?"
          + "(.*)";
  private static final String PROVIDER_NAME = "azure-devops";

  @Inject
  public AzureDevOpsURLParser(
      DevfileFilenamesProvider devfileFilenamesProvider,
      PersonalAccessTokenManager tokenManager,
      @Named("che.integration.azure.devops.scm.api_endpoint") String azureDevOpsScmApiEndpoint) {

    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.tokenManager = tokenManager;
    this.azureDevOpsScmApiEndpointHost =
        trimEnd(azureDevOpsScmApiEndpoint, '/').replaceFirst("https?://", "");
    this.azureDevOpsPattern =
        compile(format(azureDevOpsPatternTemplate, azureDevOpsScmApiEndpointHost));
    this.azureSSHDevOpsPattern =
        compile(format(azureSSHDevOpsPatternTemplate, azureDevOpsScmApiEndpointHost));
  }

  public boolean isValid(@NotNull String url) {
    String trimmedUrl = trimEnd(url, '/');
    return azureDevOpsPattern.matcher(trimmedUrl).matches()
        || azureSSHDevOpsPattern.matcher(trimmedUrl).matches()
        // Check whether PAT is configured for the Azure Devops Server URL. It is sufficient to
        // confirm
        // that the URL is a valid Azure Devops Server URL.
        || isUserTokenPresent(trimmedUrl);
  }

  // Try to find the given url in a manually added user namespace token secret.
  private boolean isUserTokenPresent(String repositoryUrl) {
    Optional<String> serverUrlOptional = getServerUrl(repositoryUrl);
    if (serverUrlOptional.isPresent()) {
      String serverUrl = serverUrlOptional.get();
      try {
        PersonalAccessToken accessToken = tokenManager.get(serverUrl);
        return accessToken.getScmTokenName().equals(PROVIDER_NAME);
      } catch (ScmConfigurationPersistenceException
          | ScmCommunicationException
          | ScmUnauthorizedException
          | UnknownScmProviderException
          | UnsatisfiedScmPreconditionException exception) {
        return false;
      }
    }
    return false;
  }

  private Optional<String> getServerUrl(String repositoryUrl) {
    // If the given repository url is an SSH url, generate the base url from the pattern:
    // https://<hostname extracted from the SSH url>.
    String substring = null;
    if (repositoryUrl.startsWith("git@ssh.")) {
      substring = repositoryUrl.substring(8);
    } else if (repositoryUrl.startsWith("ssh://")) {
      substring = repositoryUrl.substring(6);
    }
    if (!isNullOrEmpty(substring)) {
      return Optional.of(
          "https://"
              + substring.substring(
                  0, substring.contains(":") ? substring.indexOf(":") : substring.indexOf("/")));
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

  private Optional<Matcher> getPatternMatcherByUrl(String url) {
    String host = URI.create(url).getHost();
    Matcher matcher = compile(format(azureDevOpsPatternTemplate, host)).matcher(url);
    if (matcher.matches()) {
      return Optional.of(matcher);
    } else {
      matcher = compile(format(azureSSHDevOpsPatternTemplate, host)).matcher(url);
      if (matcher.matches()) {
        return Optional.of(matcher);
      } else {
        matcher = compile(format(azureSSHDevOpsServerPatternTemplate, host)).matcher(url);
      }
      return matcher.matches() ? Optional.of(matcher) : Optional.empty();
    }
  }

  private IllegalArgumentException buildIllegalArgumentException(String url) {
    return new IllegalArgumentException(
        format("The given url %s is not a valid Azure DevOps URL. ", url));
  }

  public AzureDevOpsUrl parse(String url, @Nullable String revision) {
    Matcher matcher;
    boolean isHTTPSUrl = azureDevOpsPattern.matcher(url).matches();
    if (isHTTPSUrl) {
      matcher = azureDevOpsPattern.matcher(url);
    } else if (azureSSHDevOpsPattern.matcher(url).matches()) {
      matcher = azureSSHDevOpsPattern.matcher(url);
    } else {
      matcher = getPatternMatcherByUrl(url).orElseThrow(() -> buildIllegalArgumentException(url));
      isHTTPSUrl = url.startsWith("http");
    }
    if (!matcher.matches()) {
      throw buildIllegalArgumentException(url);
    }
    String serverUrl = getServerUrl(url).orElseThrow(() -> buildIllegalArgumentException(url));
    String repoName = matcher.group("repoName");
    String project = matcher.group("project");
    if (project == null) {
      // if project is not specified, repo name must be equal to project name
      // https://dev.azure.com/<MyOrg>/<MyRepo>/_git/<MyRepo> ==
      // https://dev.azure.com/<MyOrg>/_git/<MyRepo>
      project = repoName;
    }

    String branchFromUrl = null;
    String tag = null;

    String organization = matcher.group("organization");
    String urlToReturn = url;
    if (isHTTPSUrl) {
      branchFromUrl = matcher.group("branch");
      tag = matcher.group("tag");
      // The url might have the following formats:
      // - https://<organization>@<host>/<organization>/<project>/_git/<repoName>
      // - https://<credentials>@<host>/<organization>/<project>/_git/<repoName>
      // For the first case we need to remove the `organization` from the url to distinguish it from
      // `credentials`
      // TODO: return empty credentials like the BitBucketUrl
      String organizationCanIgnore = matcher.group("organizationCanIgnore");
      if (!isNullOrEmpty(organization) && organization.equals(organizationCanIgnore)) {
        urlToReturn = urlToReturn.replace(organizationCanIgnore + "@", "");
        serverUrl = serverUrl.replace(organizationCanIgnore + "@", "");
      }
    }

    return new AzureDevOpsUrl()
        .withHostName(
            url.startsWith("git@ssh.") ? azureDevOpsScmApiEndpointHost : URI.create(url).getHost())
        .setIsHTTPSUrl(isHTTPSUrl)
        .withProject(project)
        .withRepository(repoName)
        .withOrganization(organization)
        .withBranch(isNullOrEmpty(branchFromUrl) ? revision : branchFromUrl)
        .withTag(tag)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .withServerUrl(serverUrl)
        .withUrl(urlToReturn);
  }
}
