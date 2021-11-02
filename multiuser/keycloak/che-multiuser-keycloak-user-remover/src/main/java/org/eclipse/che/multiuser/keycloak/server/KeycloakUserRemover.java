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
import static org.eclipse.che.multiuser.oidc.OIDCInfoProvider.AUTH_SERVER_URL_INTERNAL_SETTING;
import static org.eclipse.che.multiuser.oidc.OIDCInfoProvider.AUTH_SERVER_URL_SETTING;

import com.google.common.base.Strings;
import com.google.gson.JsonSyntaxException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.user.server.event.BeforeUserRemovedEvent;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.core.db.cascade.CascadeEventSubscriber;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.multiuser.oidc.OIDCInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remove user from Keycloak server on {@link
 * org.eclipse.che.api.user.server.event.BeforeUserRemovedEvent}. Turn on with {@code
 * che.keycloak.cascade_user_removal_enabled} property.
 *
 * <p>For correct work need to set keycloak admin credentials via {@code
 * che.keycloak.admin_username} and {@code che.keycloak.admin_password} properties.
 */
@Singleton
public class KeycloakUserRemover {
  private static final Logger LOG = LoggerFactory.getLogger(KeycloakUserRemover.class);

  private final String keycloakUser;
  private final String keycloakPassword;
  private final KeycloakPasswordGrantTokenRequester keycloakTokenRequester;
  private final HttpJsonRequestFactory requestFactory;

  private String keycloakRemoveUserUrl;
  private String keycloakTokenEndpoint;

  @Inject
  public KeycloakUserRemover(
      @Nullable @Named("che.keycloak.cascade_user_removal_enabled") boolean userRemovalEnabled,
      @Nullable @Named("che.keycloak.admin_username") String keycloakUser,
      @Nullable @Named("che.keycloak.admin_password") String keycloakPassword,
      KeycloakSettings keycloakSettings,
      KeycloakPasswordGrantTokenRequester keycloakTokenRequester,
      OIDCInfo oidcInfo,
      HttpJsonRequestFactory requestFactory) {
    this.keycloakUser = keycloakUser;
    this.keycloakPassword = keycloakPassword;
    this.keycloakTokenRequester = keycloakTokenRequester;
    this.requestFactory = requestFactory;

    if (userRemovalEnabled) {
      String serverUrl = oidcInfo.getAuthServerURL();
      if (serverUrl == null) {
        throw new ConfigurationException(
            AUTH_SERVER_URL_SETTING
                + " or "
                + AUTH_SERVER_URL_INTERNAL_SETTING
                + " is not configured");
      }
      String realm = keycloakSettings.get().get(REALM_SETTING);
      if (realm == null) {
        throw new ConfigurationException(REALM_SETTING + " is not configured");
      }
      if (Strings.isNullOrEmpty(keycloakUser) || Strings.isNullOrEmpty(keycloakPassword)) {
        throw new ConfigurationException("Keycloak administrator username or password not set.");
      }
      this.keycloakTokenEndpoint = serverUrl + "/realms/master/protocol/openid-connect/token";
      this.keycloakRemoveUserUrl = serverUrl + "/admin/realms/" + realm + "/users/";
    }
  }

  /**
   * Remove user from Keycloak server by given user id.
   *
   * @param userId the user id to remove
   * @throws ServerException when user exists, but could not be removed from Keycloak
   */
  public void removeUserFromKeycloak(String userId)
      throws ServerException, IOException, JsonSyntaxException {

    String token =
        keycloakTokenRequester.requestToken(keycloakUser, keycloakPassword, keycloakTokenEndpoint);

    try {
      int responseCode =
          requestFactory
              .fromUrl(keycloakRemoveUserUrl + userId)
              .setAuthorizationHeader("Bearer " + token)
              .useDeleteMethod()
              .request()
              .getResponseCode();
      if (responseCode != 204) {
        throw new ServerException("Can't remove user from Keycloak. UserId:" + userId);
      }
    } catch (IOException | NotFoundException e) {
      LOG.warn(
          "User with userId: " + userId + " does not exist in Keycloak. Continuing user deletion.",
          e);
    } catch (ApiException e) {
      LOG.warn("Exception during removing user from Keycloak", e);
      throw new ServerException("Exception during removing user from Keycloak", e);
    }
  }

  @Singleton
  public static class RemoveUserListener extends CascadeEventSubscriber<BeforeUserRemovedEvent> {
    @Inject private EventService eventService;
    @Inject private KeycloakUserRemover keycloakUserRemover;

    @Inject
    @Nullable
    @Named("che.keycloak.cascade_user_removal_enabled")
    boolean userRemovalEnabled;

    @PostConstruct
    public void subscribe() {
      if (userRemovalEnabled) {
        eventService.subscribe(this, BeforeUserRemovedEvent.class);
      }
    }

    @PreDestroy
    public void unsubscribe() {
      if (userRemovalEnabled) {
        eventService.unsubscribe(this, BeforeUserRemovedEvent.class);
      }
    }

    @Override
    public void onCascadeEvent(BeforeUserRemovedEvent event) throws Exception {
      keycloakUserRemover.removeUserFromKeycloak(event.getUser().getId());
    }
  }
}
