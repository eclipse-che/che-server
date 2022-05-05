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
package org.eclipse.che.api.factory.server.scm;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
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
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Common implementation of file content provider which is able to access content of private
 * repositories using personal access tokens from specially formatted secret in user's namespace.
 */
public class AuthorizingFileContentProvider<T extends RemoteFactoryUrl>
    implements FileContentProvider {

  private final T remoteFactoryUrl;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private final GitCredentialManager gitCredentialManager;
  protected final URLFetcher urlFetcher;

  public AuthorizingFileContentProvider(
      T remoteFactoryUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager,
      GitCredentialManager gitCredentialManager) {
    this.remoteFactoryUrl = remoteFactoryUrl;
    this.urlFetcher = urlFetcher;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.gitCredentialManager = gitCredentialManager;
  }

  @Override
  public String fetchContent(String fileURL) throws IOException, DevfileException {
    final String requestURL = formatUrl(fileURL);
    try {
      Optional<PersonalAccessToken> token =
          personalAccessTokenManager.get(
              EnvironmentContext.getCurrent().getSubject(), remoteFactoryUrl.getHostName());
      if (token.isPresent()) {
        PersonalAccessToken personalAccessToken = token.get();
        String content =
            urlFetcher.fetch(requestURL, formatAuthorization(personalAccessToken.getToken()));
        gitCredentialManager.createOrReplace(personalAccessToken);
        return content;
      } else {
        // try to authenticate for the given URL
        try {
          PersonalAccessToken personalAccessToken =
              personalAccessTokenManager.fetchAndSave(
                  EnvironmentContext.getCurrent().getSubject(), remoteFactoryUrl.getHostName());
          String content =
              urlFetcher.fetch(requestURL, formatAuthorization(personalAccessToken.getToken()));
          gitCredentialManager.createOrReplace(personalAccessToken);
          return content;
        } catch (UnknownScmProviderException e) {
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
                String.format("%s: %s", e.getMessage(), exception.getMessage()), exception);
          }
        } catch (ScmUnauthorizedException e) {
          throw new DevfileException(e.getMessage(), e);
        } catch (ScmCommunicationException e) {
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
      }
    } catch (ScmConfigurationPersistenceException
        | UnsatisfiedScmPreconditionException
        | ScmUnauthorizedException
        | ScmCommunicationException e) {
      throw new DevfileException(e.getMessage(), e);
    }
  }

  protected boolean isPublicRepository(T remoteFactoryUrl) {
    return false;
  }

  private String formatUrl(String fileURL) throws DevfileException {
    String requestURL;
    try {
      if (new URI(fileURL).isAbsolute()) {
        requestURL = fileURL;
      } else {
        // since files retrieved via REST, we cannot use path symbols like . ./ so cut them off
        requestURL = remoteFactoryUrl.rawFileLocation(fileURL.replaceAll("^[/.]+", ""));
      }
    } catch (URISyntaxException e) {
      throw new DevfileException(e.getMessage(), e);
    }
    return requestURL;
  }

  protected String formatAuthorization(String token) {
    return "Bearer " + token;
  }
}
