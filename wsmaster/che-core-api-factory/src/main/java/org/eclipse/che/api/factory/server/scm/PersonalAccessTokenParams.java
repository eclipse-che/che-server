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
package org.eclipse.che.api.factory.server.scm;

/** An object to hold parameters for creating a personal access token. */
public class PersonalAccessTokenParams {
  private final String scmProviderUrl;
  private final String scmProviderName;
  private final String scmTokenName;
  private final String scmTokenId;
  private final String token;
  private final String organization;

  public PersonalAccessTokenParams(
      String scmProviderUrl,
      String scmProviderName,
      String scmTokenName,
      String scmTokenId,
      String token,
      String organization) {
    this.scmProviderUrl = scmProviderUrl;
    this.scmProviderName = scmProviderName;
    this.scmTokenName = scmTokenName;
    this.scmTokenId = scmTokenId;
    this.token = token;
    this.organization = organization;
  }

  public String getScmProviderUrl() {
    return scmProviderUrl;
  }

  public String getScmTokenName() {
    return scmTokenName;
  }

  public String getScmTokenId() {
    return scmTokenId;
  }

  public String getToken() {
    return token;
  }

  public String getOrganization() {
    return organization;
  }

  public String getScmProviderName() {
    return scmProviderName;
  }
}
