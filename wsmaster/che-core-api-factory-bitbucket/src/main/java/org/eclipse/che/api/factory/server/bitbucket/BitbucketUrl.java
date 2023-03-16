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

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.eclipse.che.api.factory.server.urlfactory.DefaultFactoryUrl;

/**
 * Representation of a bitbucket URL, allowing to get details from it.
 *
 * <p>like https://<your_username>@bitbucket.org/<workspace_ID>/<repo_name>.git
 */
public class BitbucketUrl extends DefaultFactoryUrl {

  private final String NAME = "bitbucket";

  private static final String HOSTNAME = "bitbucket.org";

  /** Username part of the bitbucket URL */
  private String username;

  /** workspace part of the bitbucket URL */
  private String workspaceId;

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
  protected BitbucketUrl() {}

  @Override
  public String getProviderName() {
    return NAME;
  }

  /**
   * Gets username of this bitbucket url
   *
   * @return the username part
   */
  public String getUsername() {
    return this.username;
  }

  public BitbucketUrl withUsername(String userName) {
    this.username = userName;
    return this;
  }

  public String getRepository() {
    return this.repository;
  }

  protected BitbucketUrl withRepository(String repository) {
    this.repository = repository;
    return this;
  }

  public String getWorkspaceId() {
    return workspaceId;
  }

  protected BitbucketUrl withWorkspaceId(String workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  protected BitbucketUrl withDevfileFilenames(List<String> devfileFilenames) {
    this.devfileFilenames.addAll(devfileFilenames);
    return this;
  }

  @Override
  public void setDevfileFilename(String devfileName) {
    this.devfileFilenames.clear();
    this.devfileFilenames.add(devfileName);
  }

  public String getBranch() {
    return this.branch;
  }

  protected BitbucketUrl withBranch(String branch) {
    if (!Strings.isNullOrEmpty(branch)) {
      this.branch = branch;
    }
    return this;
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
    return new StringJoiner("/")
        .add("https://bitbucket.org")
        .add(workspaceId)
        .add(repository)
        .add("raw")
        .add(firstNonNull(branch, "HEAD"))
        .add(fileName)
        .toString();
  }

  @Override
  public String getHostName() {
    return "https://" + HOSTNAME;
  }

  @Override
  public Optional<String> getCredentials() {
    if (!isNullOrEmpty(username) && super.getUrl().getUserInfo().equals(username)) {
      return Optional.empty();
    }
    return super.getCredentials();
  }

  /**
   * Provides location to the repository part of the full bitbucket URL.
   *
   * @return location of the repository.
   */
  protected String repositoryLocation() {
    return "https://"
        + (this.username != null ? this.username + '@' : "")
        + HOSTNAME
        + "/"
        + this.workspaceId
        + "/"
        + this.repository
        + ".git";
  }
}
