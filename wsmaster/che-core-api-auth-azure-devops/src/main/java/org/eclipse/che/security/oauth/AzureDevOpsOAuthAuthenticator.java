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

import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.commons.json.JsonParseException;
import org.eclipse.che.security.oauth.shared.User;

/**
 * OAuth2 authenticator for Azure DevOps Service.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class AzureDevOpsOAuthAuthenticator extends OAuthAuthenticator {
  private final String azureDevOpsScmApiEndpoint;
  private final String cheApiEndpoint;
  private final String azureDevOpsUserProfileDataApiUrl;
  private final String API_VERSION = "7.0";
  private final String PROVIDER_NAME = "azure-devops";
  private final String clientSecret;

  public AzureDevOpsOAuthAuthenticator(
      String cheApiEndpoint,
      String clientId,
      String clientSecret,
      String azureDevOpsApiEndpoint,
      String azureDevOpsScmApiEndpoint,
      String authUri,
      String tokenUri,
      String[] redirectUris)
      throws IOException {
    this.cheApiEndpoint = cheApiEndpoint;
    this.clientSecret = clientSecret;
    this.azureDevOpsScmApiEndpoint = trimEnd(azureDevOpsScmApiEndpoint, '/');
    this.azureDevOpsUserProfileDataApiUrl =
        String.format(
            "%s/_apis/profile/profiles/me?api-version=%s",
            trimEnd(azureDevOpsApiEndpoint, '/'), API_VERSION);
    configure(
        clientId, clientSecret, redirectUris, authUri, tokenUri, new MemoryDataStoreFactory());
  }

  /**
   * Returns the URL to redirect the user to in order to authenticate. Sets the {@code
   * response_type} to {@code redirect_uri} accordingly the Azure DevOps OAuth docs. See details at:
   *
   * <p>https://learn.microsoft.com/en-us/azure/devops/integrate/get-started/authentication/oauth?toc=%2Fazure%2Fdevops%2Fmarketplace-extensibility%2Ftoc.json&view=azure-devops#2-authorize-your-app.
   */
  @Override
  public String getAuthenticateUrl(URL requestUrl, List<String> scopes) {
    AuthorizationCodeRequestUrl url = flow.newAuthorizationUrl().setScopes(scopes);
    url.set("response_type", "Assertion");
    url.set("redirect_uri", String.format("%s/oauth/callback", cheApiEndpoint));
    url.setState(prepareState(requestUrl));
    return url.build();
  }

  @Override
  public User getUser(OAuthToken accessToken) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final String getOAuthProvider() {
    return PROVIDER_NAME;
  }

  @Override
  public OAuthToken getToken(String userId) throws IOException {
    final OAuthToken token = super.getToken(userId);
    try {
      // check if user's token is valid by requesting user profile data
      if (token == null
          || token.getToken() == null
          || token.getToken().isEmpty()
          || getUserProfile(token.getToken()) == null) {
        return null;
      }
    } catch (OAuthAuthenticationException e) {
      return null;
    }
    return token;
  }

  public String getEndpointUrl() {
    return azureDevOpsScmApiEndpoint;
  }

  private AzureDevOpsUserProfile getUserProfile(String accessToken)
      throws OAuthAuthenticationException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(azureDevOpsUserProfileDataApiUrl))
            .header("Authorization", "Bearer " + accessToken)
            .build();

    try {
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      return JsonHelper.fromJson(response.body(), AzureDevOpsUserProfile.class, null);
    } catch (IOException | InterruptedException | JsonParseException e) {
      throw new OAuthAuthenticationException(e.getMessage(), e);
    }
  }

  /**
   * Returns the token request. Overrides the default implementation to set the {@code grant_type},
   * {@code assertion}, {@code client_assertion} and {@code client_assertion_type} accordingly to
   * Azure DevOps OAuth docs. See details at:
   *
   * <p>https://learn.microsoft.com/en-us/azure/devops/integrate/get-started/authentication/oauth?toc=%2Fazure%2Fdevops%2Fmarketplace-extensibility%2Ftoc.json&view=azure-devops#3-get-an-access-and-refresh-token-for-the-user
   */
  @Override
  protected AuthorizationCodeTokenRequest getAuthorizationCodeTokenRequest(
      URL requestUrl, List<String> scopes, String code) {
    AuthorizationCodeTokenRequest request =
        super.getAuthorizationCodeTokenRequest(requestUrl, scopes, code);
    request.set("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
    request.set("assertion", code);
    request.set("client_assertion", clientSecret);
    request.set("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    request.setResponseClass(AzureDevOpsTokenResponse.class);
    return request;
  }
}
