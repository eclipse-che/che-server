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
package org.eclipse.che.api.factory.server.urlfactory;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.Collections.singletonList;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link RemoteFactoryUrl} which used with all factory URL's until there
 * is no specific implementation for given URL.
 */
public class DefaultFactoryUrl implements RemoteFactoryUrl {

  private String devfileFileLocation;
  private URL url;

  @Override
  public String getProviderName() {
    return "default";
  }

  @Override
  public List<DevfileLocation> devfileFileLocations() {
    return singletonList(
        new DevfileLocation() {
          @Override
          public Optional<String> filename() {
            return Optional.empty();
          }

          @Override
          public String location() {
            return devfileFileLocation;
          }
        });
  }

  @Override
  public String rawFileLocation(String filename) {
    return URI.create(devfileFileLocation).resolve(filename).toString();
  }

  @Override
  public String getHostName() {
    return URI.create(devfileFileLocation).getHost();
  }

  @Override
  public String getBranch() {
    return null;
  }

  @Override
  public Optional<String> getCredentials() {
    if (url == null || isNullOrEmpty(url.getUserInfo())) {
      return Optional.empty();
    }
    String userInfo = url.getUserInfo();
    String[] credentials = userInfo.split(":");
    String username = credentials[0];
    String password = credentials.length == 2 ? credentials[1] : null;
    if (!isNullOrEmpty(username) || !isNullOrEmpty(password)) {
      return Optional.of(
          format(
              "%s:%s",
              isNullOrEmpty(username) ? "" : username, isNullOrEmpty(password) ? "" : password));
    }
    return Optional.empty();
  }

  public <U extends DefaultFactoryUrl> U withUrl(String url) {
    try {
      this.url = new URL(url);
    } catch (MalformedURLException e) {
      // Do nothing, wrong URL.
    }
    return (U) this;
  }

  public DefaultFactoryUrl withDevfileFileLocation(String devfileFileLocation) {
    this.devfileFileLocation = devfileFileLocation;
    return this;
  }

  @Override
  public void setDevfileFilename(String devfileName) {
    // do nothing as the devfile location is absolute
  }
}
