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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Azure DevOps user's profile. See more details at:
 * https://learn.microsoft.com/en-us/rest/api/azure/devops/profile/profiles/get?view=azure-devops-rest-7.0&tabs=HTTP#profile
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureDevOpsUser {
  private String displayName;
  private String emailAddress;

  private String id;

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return "AzureDevOpsUser{"
        + "displayName='"
        + displayName
        + '\''
        + ", emailAddress='"
        + emailAddress
        + '\''
        + ", id='"
        + id
        + '\''
        + '}';
  }
}
