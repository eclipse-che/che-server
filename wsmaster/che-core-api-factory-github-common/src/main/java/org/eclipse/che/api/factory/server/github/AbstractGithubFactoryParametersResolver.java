/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.github;

import static java.util.Collections.emptyMap;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.factory.server.BaseFactoryParameterResolver;
import org.eclipse.che.api.factory.server.FactoryParametersResolver;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.*;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/**
 * Provides Factory Parameters resolver for github repositories.
 *
 * @author Florent Benoit
 */
public abstract class AbstractGithubFactoryParametersResolver extends BaseFactoryParameterResolver
    implements FactoryParametersResolver {

  /** Parser which will allow to check validity of URLs and create objects. */
  private final AbstractGithubURLParser githubUrlParser;

  private final URLFetcher urlFetcher;

  /** Builder allowing to build objects from github URL. */
  private final GithubSourceStorageBuilder githubSourceStorageBuilder;

  private final URLFactoryBuilder urlFactoryBuilder;

  private final PersonalAccessTokenManager personalAccessTokenManager;

  private final String providerName;

  public AbstractGithubFactoryParametersResolver(
      AbstractGithubURLParser githubUrlParser,
      URLFetcher urlFetcher,
      GithubSourceStorageBuilder githubSourceStorageBuilder,
      AuthorisationRequestManager authorisationRequestManager,
      URLFactoryBuilder urlFactoryBuilder,
      PersonalAccessTokenManager personalAccessTokenManager,
      String providerName) {
    super(authorisationRequestManager, urlFactoryBuilder, providerName);
    this.providerName = providerName;
    this.githubUrlParser = githubUrlParser;
    this.urlFetcher = urlFetcher;
    this.githubSourceStorageBuilder = githubSourceStorageBuilder;
    this.urlFactoryBuilder = urlFactoryBuilder;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  /**
   * Check if this resolver can be used with the given parameters.
   *
   * @param factoryParameters map of parameters dedicated to factories
   * @return true if it will be accepted by the resolver implementation or false if it is not
   *     accepted
   */
  @Override
  public boolean accept(@NotNull final Map<String, String> factoryParameters) {
    // Check if url parameter is a github URL
    return factoryParameters.containsKey(URL_PARAMETER_NAME)
        && githubUrlParser.isValid(factoryParameters.get(URL_PARAMETER_NAME));
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  /**
   * Create factory object based on provided parameters
   *
   * @param factoryParameters map containing factory data parameters provided through URL
   * @throws BadRequestException when data are invalid
   */
  @Override
  public FactoryMetaDto createFactory(@NotNull final Map<String, String> factoryParameters)
      throws ApiException {
    // no need to check null value of url parameter as accept() method has performed the check
    final GithubUrl githubUrl;
    if (getSkipAuthorisation(factoryParameters)) {
      githubUrl =
          githubUrlParser.parseWithoutAuthentication(factoryParameters.get(URL_PARAMETER_NAME));
    } else {
      githubUrl = githubUrlParser.parse(factoryParameters.get(URL_PARAMETER_NAME));
    }

    return createFactory(
        factoryParameters,
        githubUrl,
        new GithubFactoryVisitor(githubUrl),
        new GithubAuthorizingFileContentProvider(
            githubUrl, urlFetcher, personalAccessTokenManager));
  }

  /**
   * Visitor that puts the default devfile or updates devfile projects into the Github Factory, if
   * needed.
   */
  private class GithubFactoryVisitor implements FactoryVisitor {

    private final GithubUrl githubUrl;

    private GithubFactoryVisitor(GithubUrl githubUrl) {
      this.githubUrl = githubUrl;
    }

    @Override
    public FactoryDevfileV2Dto visit(FactoryDevfileV2Dto factoryDto) {
      ScmInfoDto scmInfo =
          newDto(ScmInfoDto.class)
              .withScmProviderName(githubUrl.getProviderName())
              .withRepositoryUrl(githubUrl.repositoryLocation());
      if (githubUrl.getBranch() != null) {
        scmInfo.withBranch(githubUrl.getBranch());
      }
      return factoryDto.withScmInfo(scmInfo);
    }
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) throws ApiException {
    if (getSkipAuthorisation(emptyMap())) {
      return githubUrlParser.parseWithoutAuthentication(factoryUrl);
    } else {
      return githubUrlParser.parse(factoryUrl);
    }
  }
}
