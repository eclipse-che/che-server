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
package org.eclipse.che.api.factory.server.bitbucket.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketApplicationProperties {

  private String version;
  private String buildNumber;
  private String buildDate;
  private String displayName;

  public BitbucketApplicationProperties() {}

  public BitbucketApplicationProperties(
      String version, String buildNumber, String buildDate, String displayName) {
    this.version = version;
    this.buildNumber = buildNumber;
    this.buildDate = buildDate;
    this.displayName = displayName;
  }

  public String getVersion() {
    return version;
  }

  public String getBuildNumber() {
    return buildNumber;
  }

  public String getBuildDate() {
    return buildDate;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return "BitbucketApplicationProperties{"
        + "version='"
        + version
        + '\''
        + ", buildNumber="
        + buildNumber
        + ", buildDate="
        + buildDate
        + ", displayName='"
        + displayName
        + '\''
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BitbucketApplicationProperties that = (BitbucketApplicationProperties) o;
    return buildNumber == that.buildNumber
        && buildDate == that.buildDate
        && Objects.equals(version, that.version)
        && Objects.equals(displayName, that.displayName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, buildNumber, buildDate, displayName);
  }
}
