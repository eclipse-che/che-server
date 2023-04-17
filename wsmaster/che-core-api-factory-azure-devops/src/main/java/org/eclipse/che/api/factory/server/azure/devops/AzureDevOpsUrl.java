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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.eclipse.che.api.factory.server.urlfactory.DefaultFactoryUrl;

/**
 * Representation of Azure DevOps URL, allowing to get details from it.
 *
 * <p>https://learn.microsoft.com/en-us/rest/api/azure/devops/git/items/get?view=azure-devops-rest-7.0&tabs=HTTP
 * Repository should allow OAUth requests TODO doc
 *
 * @author Anatolii Bazko
 */
public class AzureDevOpsUrl extends DefaultFactoryUrl {

  private String hostName;

  private String repository;

  private String project;

  private String organization;

  private String branch;

  private String tag;

  private final List<String> devfileFilenames = new ArrayList<>();

  protected AzureDevOpsUrl() {}

  @Override
  public String getProviderName() {
    return AzureDevOps.PROVIDER_NAME;
  }

  public String getProject() {
    return project;
  }

  public AzureDevOpsUrl withProject(String project) {
    this.project = project;
    return this;
  }

  public String getRepository() {
    return this.repository;
  }

  public AzureDevOpsUrl withRepository(String repository) {
    this.repository = repository;
    return this;
  }

  public String getOrganization() {
    return organization;
  }

  public AzureDevOpsUrl withOrganization(String organization) {
    this.organization = organization;
    return this;
  }

  public String getTag() {
    return tag;
  }

  public AzureDevOpsUrl withTag(String tag) {
    this.tag = tag;
    return this;
  }

  @Override
  public String getBranch() {
    return branch;
  }

  public AzureDevOpsUrl withBranch(String branch) {
    this.branch = branch;
    return this;
  }

  protected AzureDevOpsUrl withDevfileFilenames(List<String> devfileFilenames) {
    this.devfileFilenames.addAll(devfileFilenames);
    return this;
  }

  @Override
  public void setDevfileFilename(String devfileName) {
    this.devfileFilenames.clear();
    this.devfileFilenames.add(devfileName);
  }

  @Override
  public List<DevfileLocation> devfileFileLocations() {
    return devfileFilenames.stream().map(this::createDevfileLocation).collect(Collectors.toList());
  }

  private DevfileLocation createDevfileLocation(String devfileFilename) {
    return new DevfileLocation() {
      @Override
      public Optional<String> filename() {
        return Optional.of(devfileFilename);
      }

      @Override
      public String location() {
        return rawFileLocation(devfileFilename);
      }
    };
  }

  public String rawFileLocation(String fileName) {
    return getRepoPathJoiner()
        .add("_apis")
        .add("git")
        .add("repositories")
        .add(repository)
        .add(
            "items"
                + String.format("?path=/%s", fileName)
                + (!isNullOrEmpty(branch)
                    ? String.format("&versionType=branch&version=%s", branch)
                    : "")
                + (!isNullOrEmpty(tag) ? String.format("&versionType=tag&version=%s", tag) : "")
                + String.format("&api-version=%s", AzureDevOps.API_VERSION))
        .toString();
  }

  public String getRepositoryLocation() {
    return getRepoPathJoiner().add("_git").add(repository).toString();
  }

  private StringJoiner getRepoPathJoiner() {
    StringJoiner repoPath = new StringJoiner("/").add(hostName).add(organization);
    if (project != null) {
      repoPath.add("_git");
    }
    return repoPath;
  }

  @Override
  public String getHostName() {
    return hostName;
  }

  public AzureDevOpsUrl withHostName(String hostName) {
    this.hostName = "https://" + hostName;
    return this;
  }
}
