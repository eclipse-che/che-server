/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.keycloak.server;

import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.REALM_SETTING;

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.multiuser.oidc.OIDCInfoProvider;

/**
 * KeycloakOIDCInfoProvider retrieves OpenID Connect (OIDC) configuration for well-known endpoint.
 * These information is useful to provide access to the Keycloak api.
 */
public class KeycloakOIDCInfoProvider extends OIDCInfoProvider {
  public final String realm;

  @Inject
  public KeycloakOIDCInfoProvider(
      @Nullable @Named(AUTH_SERVER_URL_SETTING) String serverURL,
      @Nullable @Named(AUTH_SERVER_URL_INTERNAL_SETTING) String serverInternalURL,
      @Nullable @Named(OIDC_PROVIDER_SETTING) String oidcProviderUrl,
      @Nullable @Named(REALM_SETTING) String realm) {
    super(serverURL, serverInternalURL, oidcProviderUrl);
    this.realm = realm;
  }

  @Override
  protected String constructServerAuthUrl(String serverAuthUrl) {
    return serverAuthUrl + "/realms/" + realm;
  }

  protected void validate() {
    if (oidcProviderUrl == null && realm == null) {
      throw new RuntimeException("The '" + REALM_SETTING + "' property must be set");
    }
    super.validate();
  }
}
