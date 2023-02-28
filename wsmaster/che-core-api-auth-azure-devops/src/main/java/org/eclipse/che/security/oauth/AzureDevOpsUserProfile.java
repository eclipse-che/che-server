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

package org.eclipse.che.security.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents Azure DevOps user profile data. See details at:
 * https://learn.microsoft.com/en-us/rest/api/azure/devops/profile/profiles/get?view=azure-devops-rest-7.0&tabs=HTTP#profile
 *
 * @author Anatolii Bazko
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureDevOpsUserProfile {
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return "AzureDevOpsUserProfile{" + "id='" + id + '\'' + '}';
  }
}
