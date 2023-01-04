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
package org.eclipse.che.api.factory.server.github;

import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.eclipse.che.security.oauth1.OAuthAuthenticationService.ERROR_QUERY_NAME;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.factory.server.DefaultFactoryParameterResolver;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.ProjectConfigDtoMerger;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.factory.shared.dto.FactoryVisitor;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.ProjectDto;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Provides Factory Parameters resolver for github repositories.
 *
 * @author Florent Benoit
 */
@Singleton
public class GithubFactoryParametersResolver extends DefaultFactoryParameterResolver {

  /** Parser which will allow to check validity of URLs and create objects. */
  private final GithubURLParser githubUrlParser;

  /** Builder allowing to build objects from github URL. */
  private final GithubSourceStorageBuilder githubSourceStorageBuilder;

  /** ProjectDtoMerger */
  private final ProjectConfigDtoMerger projectConfigDtoMerger;

  private final PersonalAccessTokenManager personalAccessTokenManager;

  @Inject
  public GithubFactoryParametersResolver(
      GithubURLParser githubUrlParser,
      URLFetcher urlFetcher,
      GithubSourceStorageBuilder githubSourceStorageBuilder,
      URLFactoryBuilder urlFactoryBuilder,
      ProjectConfigDtoMerger projectConfigDtoMerger,
      PersonalAccessTokenManager personalAccessTokenManager,
      @Nullable @Named("che.integration.github.oauth_endpoint") String oauthEndpoint) {
    this(
        githubUrlParser,
        urlFetcher,
        githubSourceStorageBuilder,
        urlFactoryBuilder,
        projectConfigDtoMerger,
        personalAccessTokenManager);
  }

  GithubFactoryParametersResolver(
      GithubURLParser githubUrlParser,
      URLFetcher urlFetcher,
      GithubSourceStorageBuilder githubSourceStorageBuilder,
      URLFactoryBuilder urlFactoryBuilder,
      ProjectConfigDtoMerger projectConfigDtoMerger,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(urlFactoryBuilder, urlFetcher);
    this.githubUrlParser = githubUrlParser;
    this.githubSourceStorageBuilder = githubSourceStorageBuilder;
    this.projectConfigDtoMerger = projectConfigDtoMerger;
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

  /**
   * Create factory object based on provided parameters
   *
   * @param factoryParameters map containing factory data parameters provided through URL
   * @throws BadRequestException when data are invalid
   */
  @Override
  public FactoryMetaDto createFactory(@NotNull final Map<String, String> factoryParameters)
      throws ApiException {
    boolean skipAuthentication =
        factoryParameters.get(ERROR_QUERY_NAME) != null
            && factoryParameters.get(ERROR_QUERY_NAME).equals("access_denied");
    // no need to check null value of url parameter as accept() method has performed the check
    final GithubUrl githubUrl;
    if (skipAuthentication) {
      githubUrl =
          githubUrlParser.parseWithoutAuthentication(factoryParameters.get(URL_PARAMETER_NAME));
    } else {
      githubUrl = githubUrlParser.parse(factoryParameters.get(URL_PARAMETER_NAME));
    }

    // create factory from the following location if location exists, else create default factory
    return urlFactoryBuilder
        .createFactoryFromDevfile(
            githubUrl,
            new GithubAuthorizingFileContentProvider(
                githubUrl, urlFetcher, personalAccessTokenManager),
            extractOverrideParams(factoryParameters),
            skipAuthentication)
        .orElseGet(() -> newDto(FactoryDto.class).withV(CURRENT_VERSION).withSource("repo"))
        .acceptVisitor(new GithubFactoryVisitor(githubUrl));
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

    @Override
    public FactoryDto visit(FactoryDto factory) {
      if (factory.getWorkspace() != null) {
        return projectConfigDtoMerger.merge(
            factory,
            () -> {
              // Compute project configuration
              return newDto(ProjectConfigDto.class)
                  .withSource(githubSourceStorageBuilder.buildWorkspaceConfigSource(githubUrl))
                  .withName(githubUrl.getRepository())
                  .withPath("/".concat(githubUrl.getRepository()));
            });
      } else if (factory.getDevfile() == null) {
        // initialize default devfile
        factory.setDevfile(urlFactoryBuilder.buildDefaultDevfile(githubUrl.getRepository()));
      }

      updateProjects(
          factory.getDevfile(),
          () ->
              newDto(ProjectDto.class)
                  .withSource(githubSourceStorageBuilder.buildDevfileSource(githubUrl))
                  .withName(githubUrl.getRepository()),
          project -> {
            final String location = project.getSource().getLocation();
            if (location.equals(githubUrl.repositoryLocation())) {
              project.getSource().setBranch(githubUrl.getBranch());
            }
          });

      return factory;
    }
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) throws ApiException {
    return githubUrlParser.parse(factoryUrl);
  }
}
