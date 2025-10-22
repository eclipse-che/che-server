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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Azure DevOps Server user's profile. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureDevOpsServerUserProfile {
  private AzureDevOpsServerUserIdentity identity;
  private AzureDevOpsServerUserPreferences userPreferences;
  private String defaultMailAddress;

  public AzureDevOpsServerUserIdentity getIdentity() {
    return identity;
  }

  public void setIdentity(AzureDevOpsServerUserIdentity identity) {
    this.identity = identity;
  }

  public String getDefaultMailAddress() {
    return defaultMailAddress;
  }

  public void setDefaultMailAddress(String defaultMailAddress) {
    this.defaultMailAddress = defaultMailAddress;
  }

  public AzureDevOpsServerUserPreferences getUserPreferences() {
    return userPreferences;
  }

  public void setUserPreferences(AzureDevOpsServerUserPreferences userPreferences) {
    this.userPreferences = userPreferences;
  }

  @Override
  public String toString() {
    return "AzureDevOpsServerUserProfile{"
        + "identity="
        + identity
        + ", userPreferences="
        + userPreferences
        + ", defaultMailAddress='"
        + defaultMailAddress
        + '\''
        + '}';
  }
}
