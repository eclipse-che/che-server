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

import static jakarta.ws.rs.HttpMethod.POST;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.commons.lang.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Requests the Keycloak access token with grant_type=password, using a Keycloak username, password,
 * and token endpoint.
 */
@Singleton
public class KeycloakPasswordGrantTokenRequester {

  private static final Logger LOG =
      LoggerFactory.getLogger(KeycloakPasswordGrantTokenRequester.class);

  private static final String GRANT_TYPE = "grant_type";
  private static final String GRANT_TYPE_VALUE = "password";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String CLIENT_ID = "client_id";
  private static final String CLIENT_ID_VALUE = "admin-cli";
  private static final String ACCESS_TOKEN = "access_token";

  /**
   * Requests the Keycloak access token with grant_type=password for the provided Keycloak user,
   * password, and token endpoint.
   *
   * @param keycloakUser the Keycloak user
   * @param keycloakPassword the Keycloak password
   * @param keycloakTokenEndpoint the Keycloak token endpoint
   * @return Keycloak access token
   * @throws ServerException when any server error occurs while performing the request
   * @throws IOException when an IO error occurs while connecting to the server
   * @throws JsonSyntaxException when an error occurs while parsing JSON response
   */
  public String requestToken(
      String keycloakUser, String keycloakPassword, String keycloakTokenEndpoint)
      throws ServerException, IOException, JsonSyntaxException {

    String accessToken = "";
    HttpURLConnection http = null;
    try {
      http = createURLConnection(keycloakUser, keycloakPassword, keycloakTokenEndpoint);
      checkResponseCode(http, keycloakTokenEndpoint);

      final BufferedReader response =
          new BufferedReader(new InputStreamReader(http.getInputStream(), UTF_8));
      accessToken = getAccessToken(response);

    } catch (IOException | JsonSyntaxException ex) {
      throw ex;
    } finally {
      if (http != null) {
        http.disconnect();
      }
    }
    return accessToken;
  }

  private HttpURLConnection createURLConnection(
      String keycloakUser, String keycloakPassword, String keycloakTokenEndpoint)
      throws IOException {
    HttpURLConnection http = (HttpURLConnection) new URL(keycloakTokenEndpoint).openConnection();
    http.setConnectTimeout(60000);
    http.setReadTimeout(60000);
    http.setRequestMethod(POST);
    http.setAllowUserInteraction(false);
    http.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
    http.setInstanceFollowRedirects(true);
    http.setDoOutput(true);
    StringBuilder sb = new StringBuilder();
    sb.append(GRANT_TYPE + "=" + GRANT_TYPE_VALUE)
        .append("&" + USERNAME + "=")
        .append(keycloakUser)
        .append("&" + PASSWORD + "=")
        .append(keycloakPassword)
        .append("&" + CLIENT_ID + "=" + CLIENT_ID_VALUE);

    try (OutputStream output = http.getOutputStream()) {
      output.write(sb.toString().getBytes(UTF_8));
    }
    return http;
  }

  private void checkResponseCode(HttpURLConnection http, String keycloakTokenEndpoint)
      throws IOException, ServerException {
    if (http.getResponseCode() != 200) {
      throw new ServerException(
          "Cannot get Keycloak access token. Server response: "
              + keycloakTokenEndpoint
              + " "
              + http.getResponseCode()
              + IoUtil.readStream(http.getErrorStream()));
    }
  }

  private String getAccessToken(BufferedReader response)
      throws ServerException, JsonSyntaxException {
    JsonParser jsonParser = new JsonParser();
    JsonElement jsonElement = jsonParser.parse(response);
    JsonObject asJsonObject = jsonElement.getAsJsonObject();
    if (!asJsonObject.has(ACCESS_TOKEN)) {
      throw new ServerException("Keycloak access token does not exist.");
    }
    return asJsonObject.get(ACCESS_TOKEN).getAsString();
  }
}
