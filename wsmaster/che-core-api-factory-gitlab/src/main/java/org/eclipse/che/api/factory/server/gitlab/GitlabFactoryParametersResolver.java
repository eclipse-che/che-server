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
package org.eclipse.che.api.factory.server.gitlab;

import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.eclipse.che.security.oauth1.OAuthAuthenticationService.ERROR_QUERY_NAME;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.factory.server.DefaultFactoryParameterResolver;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.factory.shared.dto.FactoryVisitor;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.shared.dto.devfile.ProjectDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.SourceDto;

/**
 * Provides Factory Parameters resolver for Gitlab repositories.
 *
 * @author Max Shaposhnyk
 */
@Singleton
public class GitlabFactoryParametersResolver extends DefaultFactoryParameterResolver {

  private final GitlabUrlParser gitlabURLParser;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  @Inject
  public GitlabFactoryParametersResolver(
      URLFactoryBuilder urlFactoryBuilder,
      URLFetcher urlFetcher,
      GitlabUrlParser gitlabURLParser,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(urlFactoryBuilder, urlFetcher);
    this.gitlabURLParser = gitlabURLParser;
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
    return factoryParameters.containsKey(URL_PARAMETER_NAME)
        && gitlabURLParser.isValid(factoryParameters.get(URL_PARAMETER_NAME));
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
    final GitlabUrl gitlabUrl = gitlabURLParser.parse(factoryParameters.get(URL_PARAMETER_NAME));
    boolean skipAuthentication =
        factoryParameters.get(ERROR_QUERY_NAME) != null
            && factoryParameters.get(ERROR_QUERY_NAME).equals("access_denied");
    // create factory from the following location if location exists, else create default factory
    return urlFactoryBuilder
        .createFactoryFromDevfile(
            gitlabUrl,
            new GitlabAuthorizingFileContentProvider(
                gitlabUrl, urlFetcher, personalAccessTokenManager),
            extractOverrideParams(factoryParameters),
            skipAuthentication)
        .orElseGet(() -> newDto(FactoryDto.class).withV(CURRENT_VERSION).withSource("repo"))
        .acceptVisitor(new GitlabFactoryVisitor(gitlabUrl));
  }

  /**
   * Visitor that puts the default devfile or updates devfile projects into the Gitlab Factory, if
   * needed.
   */
  private class GitlabFactoryVisitor implements FactoryVisitor {

    private final GitlabUrl gitlabUrl;

    private GitlabFactoryVisitor(GitlabUrl gitlabUrl) {
      this.gitlabUrl = gitlabUrl;
    }

    @Override
    public FactoryDevfileV2Dto visit(FactoryDevfileV2Dto factoryDto) {
      ScmInfoDto scmInfo =
          newDto(ScmInfoDto.class)
              .withScmProviderName(gitlabUrl.getProviderName())
              .withRepositoryUrl(gitlabUrl.repositoryLocation());
      if (gitlabUrl.getBranch() != null) {
        scmInfo.withBranch(gitlabUrl.getBranch());
      }
      return factoryDto.withScmInfo(scmInfo);
    }

    @Override
    public FactoryDto visit(FactoryDto factory) {

      if (factory.getDevfile() == null) {
        // initialize default devfile
        factory.setDevfile(urlFactoryBuilder.buildDefaultDevfile(gitlabUrl.getProject()));
      }

      updateProjects(
          factory.getDevfile(),
          () ->
              newDto(ProjectDto.class)
                  .withSource(
                      newDto(SourceDto.class)
                          .withLocation(gitlabUrl.repositoryLocation())
                          .withType("git")
                          .withBranch(gitlabUrl.getBranch())
                          .withSparseCheckoutDir(gitlabUrl.getSubfolder()))
                  .withName(gitlabUrl.getProject()),
          project -> {
            final String location = project.getSource().getLocation();
            if (location.equals(gitlabUrl.repositoryLocation())) {
              project.getSource().setBranch(gitlabUrl.getBranch());
            }
          });

      return factory;
    }
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) throws ApiException {
    return gitlabURLParser.parse(factoryUrl);
  }
}
