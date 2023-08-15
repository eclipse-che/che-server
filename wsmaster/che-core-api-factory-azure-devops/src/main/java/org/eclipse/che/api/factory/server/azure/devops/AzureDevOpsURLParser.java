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
package org.eclipse.che.api.factory.server.azure.devops;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import jakarta.validation.constraints.NotNull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;

/**
 * Parser of String Azure DevOps URLs and provide {@link AzureDevOpsUrl} objects.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class AzureDevOpsURLParser {

  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final String azureDevOpsScmApiEndpointHost;
  /**
   * Regexp to find repository details (repository name, organization name and branch or tag)
   * Examples of valid URLs are in the test class.
   */
  private final Pattern azureDevOpsPattern;

  @Inject
  public AzureDevOpsURLParser(
      DevfileFilenamesProvider devfileFilenamesProvider,
      @Named("che.integration.azure.devops.scm.api_endpoint") String azureDevOpsScmApiEndpoint) {

    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.azureDevOpsScmApiEndpointHost =
        trimEnd(azureDevOpsScmApiEndpoint, '/').substring("https://".length());
    this.azureDevOpsPattern =
        compile(
            format(
                "^https://(?<organizationCanIgnore>[^@]++)?@?%s/(?<organization>[^/]++)/((?<project>[^/]++)/)?_git/"
                    + "(?<repoName>[^?]++)"
                    + "([?&]path=(?<path>[^&]++))?"
                    + "([?&]version=GT(?<tag>[^&]++))?"
                    + "([?&]version=GB(?<branch>[^&]++))?"
                    + "(.*)",
                azureDevOpsScmApiEndpointHost));
  }

  public boolean isValid(@NotNull String url) {
    return azureDevOpsPattern.matcher(url).matches();
  }

  public AzureDevOpsUrl parse(String url) {
    Matcher matcher = azureDevOpsPattern.matcher(url);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(format("The given url %s is not a valid.", url));
    }

    String repoName = matcher.group("repoName");
    String project = matcher.group("project");
    if (project == null) {
      // if project is not specified, repo name must be equal to project name
      // https://dev.azure.com/<MyOrg>/<MyRepo>/_git/<MyRepo> ==
      // https://dev.azure.com/<MyOrg>/_git/<MyRepo>
      project = repoName;
    }

    String organization = matcher.group("organization");
    String branch = matcher.group("branch");
    String tag = matcher.group("tag");

    // The url might have the following formats:
    // - https://<organization>@<host>/<organization>/<project>/_git/<repoName>
    // - https://<credentials>@<host>/<organization>/<project>/_git/<repoName>
    // For the first case we need to remove the `organization` from the url to distinguish it from
    // `credentials`
    // TODO: return empty credentials like the BitBucketUrl
    String organizationCanIgnore = matcher.group("organizationCanIgnore");
    if (!isNullOrEmpty(organization) && organization.equals(organizationCanIgnore)) {
      url = url.replace(organizationCanIgnore + "@", "");
    }

    return new AzureDevOpsUrl()
        .withHostName(azureDevOpsScmApiEndpointHost)
        .withProject(project)
        .withRepository(repoName)
        .withOrganization(organization)
        .withBranch(branch)
        .withTag(tag)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .withUrl(url);
  }
}
