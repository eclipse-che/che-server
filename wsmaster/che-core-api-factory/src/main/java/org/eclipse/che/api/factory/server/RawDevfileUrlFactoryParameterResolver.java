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
package org.eclipse.che.api.factory.server;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static org.eclipse.che.api.factory.server.FactoryResolverPriority.HIGHEST;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;

import jakarta.validation.constraints.NotNull;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import javax.inject.Inject;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.factory.server.urlfactory.DefaultFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.URLFileContentProvider;

/**
 * {@link FactoryParametersResolver} implementation to resolve factory based on url parameter as a
 * direct URL to a devfile content. Extracts and applies devfile values override parameters.
 */
public class RawDevfileUrlFactoryParameterResolver implements FactoryParametersResolver {

  protected final URLFactoryBuilder urlFactoryBuilder;
  protected final URLFetcher urlFetcher;

  @Inject
  public RawDevfileUrlFactoryParameterResolver(
      URLFactoryBuilder urlFactoryBuilder, URLFetcher urlFetcher) {
    this.urlFactoryBuilder = urlFactoryBuilder;
    this.urlFetcher = urlFetcher;
  }

  /**
   * Check if this resolver can be used with the given parameters.
   *
   * @param factoryParameters map of parameters dedicated to factories
   * @return true if it will be accepted by the resolver implementation or false if it is not
   *     accepted
   */
  @Override
  public boolean accept(Map<String, String> factoryParameters) {
    String url = factoryParameters.get(URL_PARAMETER_NAME);
    return !isNullOrEmpty(url) && (url.endsWith(".yaml") || url.endsWith(".yml"));
  }

  /**
   * Creates factory based on provided parameters. Presumes url parameter as direct URL to a devfile
   * content.
   *
   * @param factoryParameters map containing factory data parameters provided through URL
   */
  @Override
  public FactoryMetaDto createFactory(@NotNull final Map<String, String> factoryParameters)
      throws ApiException {
    // This should never be null, because our contract in #accept prohibits that
    String devfileLocation = factoryParameters.get(URL_PARAMETER_NAME);

    URI devfileURI;
    try {
      devfileURI = new URL(devfileLocation).toURI();
    } catch (MalformedURLException | URISyntaxException e) {
      throw new BadRequestException(
          format(
              "Unable to process provided factory URL. Please check its validity and try again. Parser message: %s",
              e.getMessage()));
    }
    return urlFactoryBuilder
        .createFactoryFromDevfile(
            new DefaultFactoryUrl()
                .withDevfileFileLocation(devfileLocation)
                .withUrl(devfileLocation),
            new URLFileContentProvider(devfileURI, urlFetcher),
            extractOverrideParams(factoryParameters),
            false)
        .orElse(null);
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) throws ApiException {
    throw new ApiException("Operation is not supported");
  }

  @Override
  public FactoryResolverPriority priority() {
    return HIGHEST;
  }
}
