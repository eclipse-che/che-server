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
package org.eclipse.che.api.factory.server.git.ssh;

import static org.eclipse.che.api.factory.server.FactoryResolverPriority.LOWEST;
import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.FactoryResolverPriority;
import org.eclipse.che.api.factory.server.RawDevfileUrlFactoryParameterResolver;
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
 * Provides Factory Parameters resolver for Git Ssh repositories.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class GitSshFactoryParametersResolver extends RawDevfileUrlFactoryParameterResolver {

  private final GitSshURLParser gitSshURLParser;

  private final PersonalAccessTokenManager personalAccessTokenManager;

  @Inject
  public GitSshFactoryParametersResolver(
      GitSshURLParser gitSshURLParser,
      URLFetcher urlFetcher,
      URLFactoryBuilder urlFactoryBuilder,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(urlFactoryBuilder, urlFetcher);
    this.gitSshURLParser = gitSshURLParser;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public boolean accept(@NotNull final Map<String, String> factoryParameters) {
    return factoryParameters.containsKey(URL_PARAMETER_NAME)
        && gitSshURLParser.isValid(factoryParameters.get(URL_PARAMETER_NAME));
  }

  @Override
  public FactoryMetaDto createFactory(@NotNull final Map<String, String> factoryParameters)
      throws ApiException {
    // no need to check null value of url parameter as accept() method has performed the check
    final GitSshUrl gitSshUrl = gitSshURLParser.parse(factoryParameters.get(URL_PARAMETER_NAME));

    // create factory from the following location if location exists, else create default factory
    return urlFactoryBuilder
        .createFactoryFromDevfile(
            gitSshUrl,
            new GitSshAuthorizingFileContentProvider(
                gitSshUrl, urlFetcher, personalAccessTokenManager),
            extractOverrideParams(factoryParameters),
            true)
        .orElseGet(() -> newDto(FactoryDto.class).withV(CURRENT_VERSION).withSource("repo"))
        .acceptVisitor(new GitSshFactoryVisitor(gitSshUrl));
  }

  /**
   * Visitor that puts the default devfile or updates devfile projects into the Git Ssh Factory, if
   * needed.
   */
  private class GitSshFactoryVisitor implements FactoryVisitor {

    private final GitSshUrl gitSshUrl;

    private GitSshFactoryVisitor(GitSshUrl gitSshUrl) {
      this.gitSshUrl = gitSshUrl;
    }

    @Override
    public FactoryDevfileV2Dto visit(FactoryDevfileV2Dto factoryDto) {
      ScmInfoDto scmInfo =
          newDto(ScmInfoDto.class)
              .withScmProviderName(gitSshUrl.getProviderName())
              .withRepositoryUrl(gitSshUrl.getRepositoryLocation());
      return factoryDto.withScmInfo(scmInfo);
    }

    @Override
    public FactoryDto visit(FactoryDto factory) {
      if (factory.getDevfile() == null) {
        factory.setDevfile(urlFactoryBuilder.buildDefaultDevfile(gitSshUrl.getRepository()));
      }

      updateProjects(
          factory.getDevfile(),
          () ->
              newDto(ProjectDto.class)
                  .withSource(
                      newDto(SourceDto.class)
                          .withLocation(gitSshUrl.getRepositoryLocation())
                          .withType("git"))
                  .withName(gitSshUrl.getRepository()),
          project -> {});

      return factory;
    }
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) {
    return gitSshURLParser.parse(factoryUrl);
  }

  @Override
  public FactoryResolverPriority priority() {
    return LOWEST;
  }
}
