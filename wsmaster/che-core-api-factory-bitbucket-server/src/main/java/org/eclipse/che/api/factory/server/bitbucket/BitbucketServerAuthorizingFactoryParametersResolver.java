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
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.factory.shared.dto.FactoryVisitor;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.shared.dto.devfile.ProjectDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.SourceDto;

/**
 * Provides Factory Parameters resolver for both public and private bitbucket repositories.
 *
 * @author Max Shaposhnyk
 */
@Singleton
public class BitbucketServerAuthorizingFactoryParametersResolver
    implements FactoryParametersResolver {

  private final URLFactoryBuilder urlFactoryBuilder;
  private final URLFetcher urlFetcher;
  /** Parser which will allow to check validity of URLs and create objects. */
  private final BitbucketServerURLParser bitbucketURLParser;

  private final PersonalAccessTokenManager personalAccessTokenManager;

  @Inject
  public BitbucketServerAuthorizingFactoryParametersResolver(
      URLFactoryBuilder urlFactoryBuilder,
      URLFetcher urlFetcher,
      BitbucketServerURLParser bitbucketURLParser,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.urlFactoryBuilder = urlFactoryBuilder;
    this.urlFetcher = urlFetcher;
    this.bitbucketURLParser = bitbucketURLParser;
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
    final BitbucketServerUrl bitbucketServerUrl =
        bitbucketURLParser.parse(factoryParameters.get(URL_PARAMETER_NAME));

    final FileContentProvider fileContentProvider =
        new BitbucketServerAuthorizingFileContentProvider(
            bitbucketServerUrl, urlFetcher, personalAccessTokenManager);

    boolean skipAuthentication =
        factoryParameters.get(ERROR_QUERY_NAME) != null
            && factoryParameters.get(ERROR_QUERY_NAME).equals("access_denied");
    // create factory from the following location if location exists, else create default factory
    return urlFactoryBuilder
        .createFactoryFromDevfile(
            bitbucketServerUrl,
            fileContentProvider,
            extractOverrideParams(factoryParameters),
            skipAuthentication)
        .orElseGet(() -> newDto(FactoryDto.class).withV(CURRENT_VERSION).withSource("repo"))
        .acceptVisitor(new BitbucketFactoryVisitor(bitbucketServerUrl));
  }

  /**
   * Visitor that puts the default devfile or updates devfile projects into the Bitbucket Factory,
   * if needed.
   */
  private class BitbucketFactoryVisitor implements FactoryVisitor {

    private final BitbucketServerUrl bitbucketServerUrl;

    private BitbucketFactoryVisitor(BitbucketServerUrl bitbucketServerUrl) {
      this.bitbucketServerUrl = bitbucketServerUrl;
    }

    @Override
    public FactoryDevfileV2Dto visit(FactoryDevfileV2Dto factoryDto) {
      ScmInfoDto scmInfo =
          newDto(ScmInfoDto.class)
              .withScmProviderName(bitbucketServerUrl.getProviderName())
              .withRepositoryUrl(bitbucketServerUrl.repositoryLocation());
      if (bitbucketServerUrl.getBranch() != null) {
        String branch = bitbucketServerUrl.getBranch();
        scmInfo.withBranch(
            branch.startsWith("refs%2Fheads%2F")
                ? branch.substring(branch.lastIndexOf("%2F") + 3)
                : branch);
      }
      return factoryDto.withScmInfo(scmInfo);
    }

    @Override
    public FactoryDto visit(FactoryDto factory) {
      if (factory.getDevfile() == null) {
        // initialize default devfile
        factory.setDevfile(
            urlFactoryBuilder.buildDefaultDevfile(bitbucketServerUrl.getRepository()));
      }

      updateProjects(
          factory.getDevfile(),
          () ->
              newDto(ProjectDto.class)
                  .withSource(
                      newDto(SourceDto.class)
                          .withLocation(bitbucketServerUrl.repositoryLocation())
                          .withType("git")
                          .withBranch(bitbucketServerUrl.getBranch()))
                  .withName(bitbucketServerUrl.getRepository()),
          project -> {
            final String location = project.getSource().getLocation();
            if (location.equals(bitbucketServerUrl.repositoryLocation())) {
              project.getSource().setBranch(bitbucketServerUrl.getBranch());
            }
          });

      return factory;
    }
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) throws ApiException {
    return bitbucketURLParser.parse(factoryUrl);
  }
}
