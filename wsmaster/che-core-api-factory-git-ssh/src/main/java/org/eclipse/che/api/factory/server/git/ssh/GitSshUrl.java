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
package org.eclipse.che.api.factory.server.git.ssh;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.che.api.factory.server.urlfactory.DefaultFactoryUrl;

/**
 * Representation of Git Ssh URL, allowing to get details from it.
 *
 * @author Anatolii Bazko
 */
public class GitSshUrl extends DefaultFactoryUrl {

  private String repository;
  private String hostName;

  private String repositoryLocation;

  private final List<String> devfileFilenames = new ArrayList<>();

  protected GitSshUrl() {}

  @Override
  public String getProviderName() {
    return "git-ssh";
  }

  @Override
  public String getBranch() {
    return null;
  }

  public GitSshUrl withDevfileFilenames(List<String> devfileFilenames) {
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

  @Override
  public String rawFileLocation(String filename) {
    return filename;
  }

  private DevfileLocation createDevfileLocation(String devfileFilename) {
    return new DevfileLocation() {
      @Override
      public Optional<String> filename() {
        return Optional.of(devfileFilename);
      }

      @Override
      public String location() {
        return String.format("https://%s/%s/%s", hostName, repository, devfileFilename);
      }
    };
  }

  @Override
  public String getHostName() {
    return hostName;
  }

  public GitSshUrl withHostName(String hostName) {
    this.hostName = hostName;
    return this;
  }

  public String getRepositoryLocation() {
    return repositoryLocation;
  }

  public GitSshUrl withRepositoryLocation(String repositoryLocation) {
    this.repositoryLocation = repositoryLocation;
    return this;
  }

  public String getRepository() {
    return repository;
  }

  public GitSshUrl withRepository(String repository) {
    this.repository = repository;
    return this;
  }
}
