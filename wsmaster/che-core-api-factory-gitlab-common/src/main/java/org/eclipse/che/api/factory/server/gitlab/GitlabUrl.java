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
import static java.net.URLEncoder.encode;

import com.google.common.base.Charsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.eclipse.che.api.factory.server.urlfactory.DefaultFactoryUrl;

/**
 * Representation of a gitlab URL, allowing to get details from it.
 *
 * <p>like https://gitlab.com/path/to/repository or
 * https://gitlab.com/path/to/repository/-/tree/<branch>
 *
 * @author Max Shaposhnyk
 */
public class GitlabUrl extends DefaultFactoryUrl {

  private final String NAME = "gitlab";

  /** Hostname of the gitlab URL */
  private String hostName;

  /** Scheme of the gitlab URL */
  private String scheme;

  /** Project part of the gitlab URL */
  private String project;

  /**
   * Incorporates subgroups in the project path of the gitlab URL. See details at:
   * https://docs.gitlab.com/ee/user/group/subgroups/
   */
  private String subGroups;

  /** Branch name */
  private String branch;

  /** Devfile filenames list */
  private final List<String> devfileFilenames = new ArrayList<>();

  /**
   * Creation of this instance is made by the parser so user may not need to create a new instance
   * directly
   */
  protected GitlabUrl() {}

  @Override
  public String getProviderName() {
    return NAME;
  }

  @Override
  public String getProviderUrl() {
    return (isNullOrEmpty(scheme) ? "https" : scheme) + "://" + hostName;
  }

  /**
   * Gets hostname of this gitlab url
   *
   * @return the project part
   */
  public String getHostName() {
    return this.hostName;
  }

  public GitlabUrl withHostName(String hostName) {
    this.hostName = hostName;
    return this;
  }

  public GitlabUrl withScheme(String scheme) {
    this.scheme = scheme;
    return this;
  }

  /**
   * Gets project of this bitbucket url
   *
   * @return the project part
   */
  public String getProject() {
    return this.project;
  }

  public String getSubGroups() {
    return subGroups;
  }

  protected GitlabUrl withSubGroups(String subGroups) {
    this.subGroups = subGroups;

    String[] subGroupsItems = subGroups.split("/");
    this.project =
        subGroupsItems[subGroupsItems.length - 1]; // project (repository) name is the last item
    return this;
  }

  protected GitlabUrl withDevfileFilenames(List<String> devfileFilenames) {
    this.devfileFilenames.addAll(devfileFilenames);
    return this;
  }

  @Override
  public void setDevfileFilename(String devfileName) {
    this.devfileFilenames.clear();
    this.devfileFilenames.add(devfileName);
  }

  /**
   * Gets branch of this gitlab url
   *
   * @return the branch part
   */
  public String getBranch() {
    return this.branch;
  }

  protected GitlabUrl withBranch(String branch) {
    if (!isNullOrEmpty(branch)) {
      this.branch = branch;
    }
    return this;
  }

  /**
   * Provides list of configured devfile filenames with locations
   *
   * @return list of devfile filenames and locations
   */
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

  /**
   * Provides location to raw content of specified file
   *
   * @return location of specified file in a repository
   */
  public String rawFileLocation(String fileName) {
    String resultUrl =
        new StringJoiner("/")
            .add((isNullOrEmpty(scheme) ? "https" : scheme) + "://" + hostName)
            .add("api/v4/projects")
            // use URL-encoded path to the project as a selector instead of id
            .add(encode(subGroups, Charsets.UTF_8))
            .add("repository")
            .add("files")
            .add(encode(fileName, Charsets.UTF_8))
            .add("raw?ref=" + (isNullOrEmpty(branch) ? "HEAD" : branch))
            .toString();

    return resultUrl;
  }

  /**
   * Provides location to the repository part of the full gitlab URL.
   *
   * @return location of the repository.
   */
  protected String repositoryLocation() {
    if (isNullOrEmpty(scheme)) {
      return "git@" + hostName + ":" + subGroups + ".git";
    }
    return scheme + "://" + hostName + "/" + subGroups + ".git";
  }
}
