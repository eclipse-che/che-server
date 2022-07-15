/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
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

import com.google.api.client.util.store.MemoryDataStoreFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.commons.json.JsonParseException;
import org.eclipse.che.security.oauth.shared.User;

/** OAuth authentication for BitBucket SAAS account. */
@Singleton
public class BitbucketOAuthAuthenticator extends OAuthAuthenticator {
  public BitbucketOAuthAuthenticator(
      String clientId, String clientSecret, String[] redirectUris, String authUri, String tokenUri)
      throws IOException {
    configure(
        clientId, clientSecret, redirectUris, authUri, tokenUri, new MemoryDataStoreFactory());
  }

  @Override
  public User getUser(OAuthToken accessToken) throws OAuthAuthenticationException {
    BitbucketUser user =
        getJson("https://api.bitbucket.org/2.0/user", accessToken.getToken(), BitbucketUser.class);
    return user;
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
      if (token == null
          || token.getToken() == null
          || token.getToken().isEmpty()
          || getJson("https://api.bitbucket.org/2.0/user", token.getToken(), BitbucketUser.class)
              == null) {
        return null;
      }
    } catch (OAuthAuthenticationException e) {
      return null;
    }
    return token;
  }

  @Override
  protected <O> O getJson(String getUserUrl, String accessToken, Class<O> userClass)
      throws OAuthAuthenticationException {
    HttpURLConnection urlConnection = null;
    InputStream urlInputStream = null;

    try {
      urlConnection = (HttpURLConnection) new URL(getUserUrl).openConnection();
      urlConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
      urlInputStream = urlConnection.getInputStream();
      return JsonHelper.fromJson(urlInputStream, userClass, null);
    } catch (JsonParseException | IOException e) {
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
