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

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.json.JsonString;
import com.google.api.client.util.Key;

/**
 * The only difference between from {@link TokenResponse} is that {@link #expiresInSeconds} field is
 * represented in a {@link String} format.
 *
 * <p>https://learn.microsoft.com/en-us/azure/devops/integrate/get-started/authentication/oauth?view=azure-devops#response---authorize-app
 *
 * @author Anatolii Bazko
 */
public class AzureDevOpsTokenResponse extends TokenResponse {
  @JsonString
  @Key("expires_in")
  private Long expiresInSeconds;

  public Long getExpiresInSeconds() {
    return expiresInSeconds;
  }

  public AzureDevOpsTokenResponse setExpiresInSeconds(Long expiresInSeconds) {
    this.expiresInSeconds = expiresInSeconds;
    return this;
  }
}
