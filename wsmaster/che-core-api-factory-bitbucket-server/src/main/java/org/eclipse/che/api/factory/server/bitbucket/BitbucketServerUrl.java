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
package org.eclipse.che.api.factory.server.bitbucket;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.eclipse.che.api.factory.server.urlfactory.DefaultFactoryUrl;

/** Representation of a bitbucket Server URL, allowing to get details from it. */
public class BitbucketServerUrl extends DefaultFactoryUrl {

  private final String NAME = "bitbucket";

  /** Hostname of bitbucket URL */
  private String hostName;

  private String scheme;
  private String port;

  /** Project part of bitbucket URL */
  private String project;

  private String user;

  /** Repository part of the URL. */
  private String repository;

  /** Branch name */
  private String branch;

  /** Devfile filenames list */
  private final List<String> devfileFilenames = new ArrayList<>();

  /**
   * Creation of this instance is made by the parser so user may not need to create a new instance
   * directly
   */
  protected BitbucketServerUrl() {}

  @Override
  public String getProviderName() {
    return NAME;
  }

  @Override
  public String getProviderUrl() {
    return (scheme.equals("ssh") ? "https" : scheme) + "://" + hostName;
  }

  /**
   * Gets hostname of this bitbucket server url
   *
   * @return the project part
   */
  public String getHostName() {
    return this.hostName;
  }

  public BitbucketServerUrl withHostName(String hostName) {
    this.hostName = hostName;
    return this;
  }

  public BitbucketServerUrl withScheme(String scheme) {
    this.scheme = scheme;
    return this;
  }

  public BitbucketServerUrl withPort(String port) {
    this.port = port;
    return this;
  }

  /**
   * Gets project of this bitbucket server url
   *
   * @return the project part
   */
  public String getProject() {
    return this.project;
  }

  public BitbucketServerUrl withProject(String project) {
    this.project = project;
    return this;
  }

  /**
   * Gets repository of this bitbucket url
   *
   * @return the repository part
   */
  public String getRepository() {
    return this.repository;
  }

  protected BitbucketServerUrl withRepository(String repository) {
    this.repository = repository;
    return this;
  }

  @Override
  public void setDevfileFilename(String devfileName) {
    this.devfileFilenames.clear();
    this.devfileFilenames.add(devfileName);
  }

  protected BitbucketServerUrl withDevfileFilenames(List<String> devfileFilenames) {
    this.devfileFilenames.addAll(devfileFilenames);
    return this;
  }

  /**
   * Gets branch of this bitbucket url
   *
   * @return the branch part
   */
  public String getBranch() {
    return this.branch;
  }

  protected BitbucketServerUrl withBranch(String branch) {
    if (!isNullOrEmpty(branch)) {
      this.branch = branch;
    }
    return this;
  }

  /**
   * Gets user of this bitbucket server url
   *
   * @return the user part
   */
  public String getUser() {
    return this.user;
  }

  protected BitbucketServerUrl withUser(String user) {
    if (!isNullOrEmpty(user)) {
      this.user = user;
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
    StringJoiner joiner =
        new StringJoiner("/")
            .add((scheme.equals("ssh") ? "https" : scheme) + "://" + hostName)
            .add("rest/api/1.0")
            .add(!isNullOrEmpty(user) && isNullOrEmpty(project) ? "users" : "projects")
            .add(firstNonNull(user, project))
            .add("repos")
            .add(repository)
            .add("raw")
            .add(fileName);
    String resultUrl = joiner.toString();
    if (branch != null) {
      resultUrl = resultUrl + "?at=" + branch;
    }
    return resultUrl;
  }

  /**
   * Provides location to the repository part of the full bitbucket URL.
   *
   * @return location of the repository.
   */
  protected String repositoryLocation() {
    if (scheme.equals("ssh")) {
      return String.format(
          "%s://git@%s:%s/%s/%s.git",
          scheme, hostName, port, (isNullOrEmpty(user) ? project : "~" + user), repository);
    }
    return scheme
        + "://"
        + hostName
        + "/scm/"
        + (isNullOrEmpty(user) ? project : "~" + user)
        + "/"
        + this.repository
        + ".git";
  }
}
