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
import static java.util.regex.Pattern.compile;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Default implementation of {@link RemoteFactoryUrl} which used with all factory URL's until there
 * is no specific implementation for given URL.
 */
public class DefaultFactoryUrl implements RemoteFactoryUrl {

  private String devfileFileLocation;
  private String url;

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
  public @Nullable String getCredentials() {
    if (isNullOrEmpty(url)) {
      return null;
    }
    Matcher matcher =
        compile("https?://((?<username>[^:|@]+)?:?(?<password>[^@]+)?@)?.*").matcher(url);
    String password = null;
    String username = null;
    try {
      username = matcher.matches() ? matcher.group("username") : null;
    } catch (IllegalArgumentException e) {
      // no such group
    }
    try {
      password = matcher.matches() ? matcher.group("password") : null;
    } catch (IllegalArgumentException e) {
      // no such group
    }
    if (!isNullOrEmpty(username) || !isNullOrEmpty(password)) {
      return format(
          "%s:%s",
          isNullOrEmpty(username) ? "" : username, isNullOrEmpty(password) ? "" : password);
    }
    return null;
  }

  public <T extends DefaultFactoryUrl> T withUrl(String url) {
    this.url = url;
    return (T) this;
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
