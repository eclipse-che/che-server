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
package org.eclipse.che.multiuser.oidc;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.proxy.ProxyAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OIDCInfoProvider retrieves OpenID Connect (OIDC) configuration for well-known endpoint. These
 * information is useful to provide access to the Keycloak api.
 */
public class OIDCInfoProvider implements Provider<OIDCInfo> {

  private static final Logger LOG = LoggerFactory.getLogger(OIDCInfoProvider.class);

  private static final String KEYCLOAK_SETTING_PREFIX = "che.keycloak.";

  public static final String AUTH_SERVER_URL_SETTING = KEYCLOAK_SETTING_PREFIX + "auth_server_url";
  public static final String AUTH_SERVER_URL_INTERNAL_SETTING =
      KEYCLOAK_SETTING_PREFIX + "auth_internal_server_url";

  public static final String REALM_SETTING = KEYCLOAK_SETTING_PREFIX + "realm";
  public static final String CLIENT_ID_SETTING = KEYCLOAK_SETTING_PREFIX + "client_id";
  public static final String OIDC_PROVIDER_SETTING = KEYCLOAK_SETTING_PREFIX + "oidc_provider";
  public static final String USERNAME_CLAIM_SETTING = KEYCLOAK_SETTING_PREFIX + "username_claim";
  public static final String USE_NONCE_SETTING = KEYCLOAK_SETTING_PREFIX + "use_nonce";
  public static final String USE_FIXED_REDIRECT_URLS_SETTING =
      KEYCLOAK_SETTING_PREFIX + "use_fixed_redirect_urls";
  public static final String JS_ADAPTER_URL_SETTING = KEYCLOAK_SETTING_PREFIX + "js_adapter_url";
  public static final String ALLOWED_CLOCK_SKEW_SEC =
      KEYCLOAK_SETTING_PREFIX + "allowed_clock_skew_sec";

  public static final String OSO_ENDPOINT_SETTING = KEYCLOAK_SETTING_PREFIX + "oso.endpoint";
  public static final String PROFILE_ENDPOINT_SETTING =
      KEYCLOAK_SETTING_PREFIX + "profile.endpoint";
  public static final String PASSWORD_ENDPOINT_SETTING =
      KEYCLOAK_SETTING_PREFIX + "password.endpoint";
  public static final String LOGOUT_ENDPOINT_SETTING = KEYCLOAK_SETTING_PREFIX + "logout.endpoint";
  public static final String TOKEN_ENDPOINT_SETTING = KEYCLOAK_SETTING_PREFIX + "token.endpoint";
  public static final String JWKS_ENDPOINT_SETTING = KEYCLOAK_SETTING_PREFIX + "jwks.endpoint";
  public static final String USERINFO_ENDPOINT_SETTING =
      KEYCLOAK_SETTING_PREFIX + "userinfo.endpoint";
  public static final String GITHUB_ENDPOINT_SETTING = KEYCLOAK_SETTING_PREFIX + "github.endpoint";

  public static final String FIXED_REDIRECT_URL_FOR_DASHBOARD =
      KEYCLOAK_SETTING_PREFIX + "redirect_url.dashboard";
  public static final String FIXED_REDIRECT_URL_FOR_IDE =
      KEYCLOAK_SETTING_PREFIX + "redirect_url.ide";

  protected String serverURL;
  protected String serverInternalURL;
  protected String oidcProviderUrl;
  protected String realm;

  @Inject
  public OIDCInfoProvider(
      @Nullable @Named(AUTH_SERVER_URL_SETTING) String serverURL,
      @Nullable @Named(AUTH_SERVER_URL_INTERNAL_SETTING) String serverInternalURL,
      @Nullable @Named(OIDC_PROVIDER_SETTING) String oidcProviderUrl,
      @Nullable @Named(REALM_SETTING) String realm) {
    this.serverURL = serverURL;
    this.serverInternalURL = serverInternalURL;
    this.oidcProviderUrl = oidcProviderUrl;
    this.realm = realm;
  }

  /** @return OIDCInfo with OIDC settings information. */
  @Override
  public OIDCInfo get() {
    this.validate();

    String serverAuthUrl = (serverInternalURL != null) ? serverInternalURL : serverURL;
    String wellKnownEndpoint = this.getWellKnownEndpoint(serverAuthUrl);

    LOG.info("Retrieving OpenId configuration from endpoint: {}", wellKnownEndpoint);
    ProxyAuthenticator.initAuthenticator(wellKnownEndpoint);
    try (InputStream inputStream = new URL(wellKnownEndpoint).openStream()) {
      final JsonParser parser = new JsonFactory().createParser(inputStream);
      final TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {};

      Map<String, Object> openIdConfiguration =
          new ObjectMapper().reader().readValue(parser, typeReference);

      LOG.info("openid configuration = {}", openIdConfiguration);

      String tokenPublicEndPoint = setPublicUrl((String) openIdConfiguration.get("token_endpoint"));
      String userInfoPublicEndpoint =
          setPublicUrl((String) openIdConfiguration.get("userinfo_endpoint"));
      String endSessionPublicEndpoint =
          openIdConfiguration.containsKey("end_session_endpoint")
              ? setPublicUrl((String) openIdConfiguration.get("end_session_endpoint"))
              : null;
      String jwksPublicUri = setPublicUrl((String) openIdConfiguration.get("jwks_uri"));
      String jwksInternalUri = setInternalUrl(jwksPublicUri);
      String userInfoInternalEndpoint = setInternalUrl(userInfoPublicEndpoint);

      return new OIDCInfo(
          tokenPublicEndPoint,
          endSessionPublicEndpoint,
          userInfoPublicEndpoint,
          userInfoInternalEndpoint,
          jwksPublicUri,
          jwksInternalUri,
          serverAuthUrl,
          serverURL);
    } catch (IOException e) {
      throw new RuntimeException(
          "Exception while retrieving OpenId configuration from endpoint: " + wellKnownEndpoint, e);
    } finally {
      ProxyAuthenticator.resetAuthenticator();
    }
  }

  private String getWellKnownEndpoint(String serverAuthUrl) {
    String wellKnownEndpoint = firstNonNull(oidcProviderUrl, constructServerAuthUrl(serverAuthUrl));
    if (!wellKnownEndpoint.endsWith("/")) {
      wellKnownEndpoint = wellKnownEndpoint + "/";
    }
    wellKnownEndpoint += ".well-known/openid-configuration";
    return wellKnownEndpoint;
  }

  protected String constructServerAuthUrl(String serverAuthUrl) {
    return serverAuthUrl;
  }

  protected void validate() {
    if (serverURL == null && serverInternalURL == null && oidcProviderUrl == null) {
      throw new RuntimeException(
          "Either the '"
              + AUTH_SERVER_URL_SETTING
              + "' or '"
              + AUTH_SERVER_URL_INTERNAL_SETTING
              + "' or '"
              + OIDC_PROVIDER_SETTING
              + "' property should be set");
    }
  }

  private String setInternalUrl(String endpointUrl) {
    if (serverURL != null && serverInternalURL != null) {
      return endpointUrl.replace(serverURL, serverInternalURL);
    }
    return null;
  }

  private String setPublicUrl(String endpointUrl) {
    if (serverURL != null && serverInternalURL != null) {
      return endpointUrl.replace(serverInternalURL, serverURL);
    }
    return endpointUrl;
  }
}
