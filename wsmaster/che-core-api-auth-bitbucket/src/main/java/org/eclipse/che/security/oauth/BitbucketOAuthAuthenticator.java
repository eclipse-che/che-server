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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;

/** OAuth authentication for BitBucket SAAS account. */
@Singleton
public class BitbucketOAuthAuthenticator extends OAuthAuthenticator {
  private final String bitbucketEndpoint;

  public BitbucketOAuthAuthenticator(
      String bitbucketEndpoint,
      String clientId,
      String clientSecret,
      String[] redirectUris,
      String authUri,
      String tokenUri)
      throws IOException {
    this.bitbucketEndpoint = bitbucketEndpoint;
    configure(
        clientId, clientSecret, redirectUris, authUri, tokenUri, new MemoryDataStoreFactory());
  }

  @Override
  public String getAuthenticateUrl(URL requestUrl, List<String> scopes) {
    AuthorizationCodeRequestUrl url = flow.newAuthorizationUrl().setScopes(scopes);
    url.setState(prepareState(requestUrl));
    url.set("redirect_uri", findRedirectUrl(requestUrl));
    return url.build();
  }

  @Override
  public final String getOAuthProvider() {
    return "bitbucket";
  }

  @Override
  public OAuthToken getToken(String userId) throws IOException {
    final OAuthToken token = super.getToken(userId);
    // Need to check if token is valid for requests, if valid - return it to caller.
    try {
      if (token == null || isNullOrEmpty(token.getToken())) {
        return null;
      }
      testRequest(getTestRequestUrl(), token.getToken());
    } catch (OAuthAuthenticationException e) {
      return null;
    }
    return token;
  }

  private String getTestRequestUrl() {
    return bitbucketEndpoint.equals("https://bitbucket.org")
        ? "https://api.bitbucket.org/2.0/user"
        : bitbucketEndpoint + "/rest/api/1.0/application-properties";
  }

  @Override
  public String getEndpointUrl() {
    return bitbucketEndpoint;
  }

  private void testRequest(String requestUrl, String accessToken)
      throws OAuthAuthenticationException {
    HttpURLConnection urlConnection = null;
    InputStream urlInputStream = null;

    try {
      urlConnection = (HttpURLConnection) new URL(requestUrl).openConnection();
      urlConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
      urlInputStream = urlConnection.getInputStream();
    } catch (IOException e) {
      throw new OAuthAuthenticationException(e.getMessage(), e);
    } finally {
      if (urlInputStream != null) {
        try {
          urlInputStream.close();
        } catch (IOException ignored) {
        }
      }

      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
  }
}
