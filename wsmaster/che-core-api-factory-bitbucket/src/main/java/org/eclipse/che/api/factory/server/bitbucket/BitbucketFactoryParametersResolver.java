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
package org.eclipse.che.api.factory.server.bitbucket;

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
import org.eclipse.che.api.factory.server.FactoryParametersResolver;
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

/** Provides Factory Parameters resolver for bitbucket repositories. */
@Singleton
public class BitbucketFactoryParametersResolver implements FactoryParametersResolver {

  /** Parser which will allow to check validity of URLs and create objects. */
  private final BitbucketURLParser bitbucketURLParser;

  private final URLFetcher urlFetcher;
  /** Builder allowing to build objects from bitbucket URL. */
  private final BitbucketSourceStorageBuilder bitbucketSourceStorageBuilder;

  private final URLFactoryBuilder urlFactoryBuilder;
  /** ProjectDtoMerger */
  private final ProjectConfigDtoMerger projectConfigDtoMerger;

  /** Personal Access Token manager used when fetching protected content. */
  private final PersonalAccessTokenManager personalAccessTokenManager;

  private final BitbucketApiClient bitbucketApiClient;

  @Inject
  public BitbucketFactoryParametersResolver(
      BitbucketURLParser bitbucketURLParser,
      URLFetcher urlFetcher,
      BitbucketSourceStorageBuilder bitbucketSourceStorageBuilder,
      URLFactoryBuilder urlFactoryBuilder,
      ProjectConfigDtoMerger projectConfigDtoMerger,
      PersonalAccessTokenManager personalAccessTokenManager,
      BitbucketApiClient bitbucketApiClient) {
    this.bitbucketURLParser = bitbucketURLParser;
    this.urlFetcher = urlFetcher;
    this.bitbucketSourceStorageBuilder = bitbucketSourceStorageBuilder;
    this.urlFactoryBuilder = urlFactoryBuilder;
    this.projectConfigDtoMerger = projectConfigDtoMerger;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.bitbucketApiClient = bitbucketApiClient;
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
    // Check if url parameter is a bitbucket URL
    return factoryParameters.containsKey(URL_PARAMETER_NAME)
        && bitbucketURLParser.isValid(factoryParameters.get(URL_PARAMETER_NAME));
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
    final BitbucketUrl bitbucketUrl =
        bitbucketURLParser.parse(factoryParameters.get(URL_PARAMETER_NAME));
    boolean skipAuthentication =
        factoryParameters.get(ERROR_QUERY_NAME) != null
            && factoryParameters.get(ERROR_QUERY_NAME).equals("access_denied");
    // create factory from the following location if location exists, else create default factory
    return urlFactoryBuilder
        .createFactoryFromDevfile(
            bitbucketUrl,
            new BitbucketAuthorizingFileContentProvider(
                bitbucketUrl, urlFetcher, personalAccessTokenManager, bitbucketApiClient),
            extractOverrideParams(factoryParameters),
            skipAuthentication)
        .orElseGet(() -> newDto(FactoryDto.class).withV(CURRENT_VERSION).withSource("repo"))
        .acceptVisitor(new BitbucketFactoryVisitor(bitbucketUrl));
  }

  /**
   * Visitor that puts the default devfile or updates devfile projects into the Bitbucket Factory,
   * if needed.
   */
  private class BitbucketFactoryVisitor implements FactoryVisitor {

    private final BitbucketUrl bitbucketUrl;

    private BitbucketFactoryVisitor(BitbucketUrl bitbucketUrl) {
      this.bitbucketUrl = bitbucketUrl;
    }

    @Override
    public FactoryDevfileV2Dto visit(FactoryDevfileV2Dto factoryDto) {
      ScmInfoDto scmInfo =
          newDto(ScmInfoDto.class)
              .withScmProviderName(bitbucketUrl.getProviderName())
              .withRepositoryUrl(bitbucketUrl.repositoryLocation());
      if (bitbucketUrl.getBranch() != null) {
        scmInfo.withBranch(bitbucketUrl.getBranch());
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
                  .withSource(
                      bitbucketSourceStorageBuilder.buildWorkspaceConfigSource(bitbucketUrl))
                  .withName(bitbucketUrl.getRepository())
                  .withPath("/".concat(bitbucketUrl.getRepository()));
            });
      } else if (factory.getDevfile() == null) {
        // initialize default devfile
        factory.setDevfile(urlFactoryBuilder.buildDefaultDevfile(bitbucketUrl.getRepository()));
      }

      updateProjects(
          factory.getDevfile(),
          () ->
              newDto(ProjectDto.class)
                  .withSource(bitbucketSourceStorageBuilder.buildDevfileSource(bitbucketUrl))
                  .withName(bitbucketUrl.getRepository()),
          project -> {
            final String location = project.getSource().getLocation();
            if (location.equals(bitbucketUrl.repositoryLocation())) {
              project.getSource().setBranch(bitbucketUrl.getBranch());
            }
          });

      return factory;
    }
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) {
    return bitbucketURLParser.parse(factoryUrl);
  }
}
