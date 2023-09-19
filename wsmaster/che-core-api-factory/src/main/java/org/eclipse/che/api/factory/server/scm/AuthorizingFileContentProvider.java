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
package org.eclipse.che.api.factory.server.scm;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher.OAUTH_2_PREFIX;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import javax.net.ssl.SSLException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Common implementation of file content provider which is able to access content of private
 * repositories using personal access tokens from specially formatted secret in user's namespace.
 */
public class AuthorizingFileContentProvider<T extends RemoteFactoryUrl>
    implements FileContentProvider {

  protected final T remoteFactoryUrl;
  protected final PersonalAccessTokenManager personalAccessTokenManager;
  protected final URLFetcher urlFetcher;

  public AuthorizingFileContentProvider(
      T remoteFactoryUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.remoteFactoryUrl = remoteFactoryUrl;
    this.urlFetcher = urlFetcher;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public String fetchContent(String fileURL) throws IOException, DevfileException {
    return fetchContent(fileURL, false, null);
  }

  @Override
  public String fetchContent(String fileURL, String credentials)
      throws IOException, DevfileException {
    return fetchContent(fileURL, false, credentials);
  }

  @Override
  public String fetchContentWithoutAuthentication(String fileURL)
      throws IOException, DevfileException {
    return fetchContent(fileURL, true, null);
  }

  private String fetchContent(
      String fileURL, boolean skipAuthentication, @Nullable String credentials)
      throws IOException, DevfileException {
    final String requestURL = formatUrl(fileURL);
    try {
      if (skipAuthentication) {
        return urlFetcher.fetch(requestURL);
      } else {
        // try to authenticate for the given URL
        String authorization;
        if (isNullOrEmpty(credentials)) {
          PersonalAccessToken token =
              personalAccessTokenManager.getAndStore(remoteFactoryUrl.getProviderUrl());
          authorization =
              formatAuthorization(
                  token.getToken(),
                  token.getScmTokenName() == null
                      || !token.getScmTokenName().startsWith(OAUTH_2_PREFIX));
        } else {
          authorization = getCredentialsAuthorization(credentials);
        }
        return urlFetcher.fetch(requestURL, authorization);
      }
    } catch (UnknownScmProviderException e) {
      return fetchContentWithoutToken(requestURL);
    } catch (ScmCommunicationException e) {
      return toIOException(fileURL, e);
    } catch (ScmUnauthorizedException
        | ScmConfigurationPersistenceException
        | UnsatisfiedScmPreconditionException e) {
      throw new DevfileException(e.getMessage(), e);
    }
  }

  protected String fetchContentWithoutToken(String requestURL)
      throws DevfileException, IOException {
    // we don't have any provider matching this SCM provider
    // so try without secrets being configured
    try {
      return urlFetcher.fetch(requestURL);
    } catch (IOException exception) {
      if (exception instanceof SSLException) {
        ScmCommunicationException cause =
            new ScmCommunicationException(
                String.format(
                    "Failed to fetch a content from URL %s due to TLS key misconfiguration. Please refer to the docs about how to correctly import it. ",
                    requestURL));
        throw new DevfileException(exception.getMessage(), cause);
      } else if (exception instanceof FileNotFoundException) {
        if (isPublicRepository(remoteFactoryUrl)) {
          // for public repo-s return 404 as-is
          throw exception;
        }
      }
      throw new DevfileException(
          "Could not reach devfile at " + "`" + exception.getMessage() + "`", exception);
    }
  }

  protected String toIOException(String fileURL, ScmCommunicationException e) throws IOException {
    throw new IOException(
        String.format(
            "Failed to fetch a content from URL %s. Make sure the URL"
                + " is correct. For private repository, make sure authentication is configured."
                + " Additionally, if you're using "
                + " relative form, make sure the referenced file are actually stored"
                + " relative to the devfile on the same host,"
                + " or try to specify URL in absolute form. The current attempt to authenticate"
                + " request, failed with the following error message: %s",
            fileURL, e.getMessage()),
        e);
  }

  protected boolean isPublicRepository(T remoteFactoryUrl) {
    return false;
  }

  protected String formatUrl(String fileURL) throws DevfileException {
    String requestURL;
    try {
      if (new URI(fileURL).isAbsolute()) {
        requestURL = fileURL;
      } else {
        // since files retrieved via REST, we cannot use path like '.' or one that starts with './'
        // so cut them off
        requestURL =
            remoteFactoryUrl.rawFileLocation(fileURL.replaceAll("^(?:\\.?\\/)|(?:\\.$)", ""));
      }
    } catch (URISyntaxException e) {
      throw new DevfileException(e.getMessage(), e);
    }
    return requestURL;
  }

  /**
   * Formats authorization header value.
   *
   * @param token personal access token
   * @param isPAT true if token is personal access token, false if it is OAuth token
   * @return formatted authorization header value
   */
  protected String formatAuthorization(String token, boolean isPAT) {
    return "Bearer " + token;
  }

  private String getCredentialsAuthorization(String credentials) {
    return "Basic " + new String(Base64.getEncoder().encode(credentials.getBytes()));
  }
}
