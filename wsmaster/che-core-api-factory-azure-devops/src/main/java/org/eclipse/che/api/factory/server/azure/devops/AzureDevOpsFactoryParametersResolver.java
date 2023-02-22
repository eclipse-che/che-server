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
package org.eclipse.che.api.factory.server.azure.devops;

import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.eclipse.che.security.oauth1.OAuthAuthenticationService.ERROR_QUERY_NAME;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ApiException;
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

/**
 * Provides Factory Parameters resolver for Azure DevOps repositories.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class AzureDevOpsFactoryParametersResolver extends DefaultFactoryParameterResolver {

  /** Parser which will allow to check validity of URLs and create objects. */
  private final AzureDevOpsURLParser azureDevOpsURLParser;

  private final PersonalAccessTokenManager personalAccessTokenManager;
  private final ProjectConfigDtoMerger projectConfigDtoMerger;

  @Inject
  public AzureDevOpsFactoryParametersResolver(
      ProjectConfigDtoMerger projectConfigDtoMerger,
      AzureDevOpsURLParser azureDevOpsURLParser,
      URLFetcher urlFetcher,
      URLFactoryBuilder urlFactoryBuilder,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(urlFactoryBuilder, urlFetcher);
    this.azureDevOpsURLParser = azureDevOpsURLParser;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.projectConfigDtoMerger = projectConfigDtoMerger;
  }

  @Override
  public boolean accept(@NotNull final Map<String, String> factoryParameters) {
    return factoryParameters.containsKey(URL_PARAMETER_NAME)
        && azureDevOpsURLParser.isValid(factoryParameters.get(URL_PARAMETER_NAME));
  }

  @Override
  public FactoryMetaDto createFactory(@NotNull final Map<String, String> factoryParameters)
      throws ApiException {
    boolean skipAuthentication =
        factoryParameters.get(ERROR_QUERY_NAME) != null
            && factoryParameters.get(ERROR_QUERY_NAME).equals("access_denied");

    // no need to check null value of url parameter as accept() method has performed the check
    final AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse(factoryParameters.get(URL_PARAMETER_NAME));

    // create factory from the following location if location exists, else create default factory
    return urlFactoryBuilder
        .createFactoryFromDevfile(
            azureDevOpsUrl,
            new AzureDevOpsAuthorizingFileContentProvider(
                azureDevOpsUrl, urlFetcher, personalAccessTokenManager),
            extractOverrideParams(factoryParameters),
            skipAuthentication)
        .orElseGet(() -> newDto(FactoryDto.class).withV(CURRENT_VERSION).withSource("repo"))
        .acceptVisitor(new AzureDevOpsFactoryVisitor(azureDevOpsUrl));
  }

  /**
   * Visitor that puts the default devfile or updates devfile projects into the Azure DevOps
   * Factory, if needed.
   */
  private class AzureDevOpsFactoryVisitor implements FactoryVisitor {

    private final AzureDevOpsUrl azureDevOpsUrl;

    private AzureDevOpsFactoryVisitor(AzureDevOpsUrl azureDevOpsUrl) {
      this.azureDevOpsUrl = azureDevOpsUrl;
    }

    @Override
    public FactoryDevfileV2Dto visit(FactoryDevfileV2Dto factoryDto) {
      ScmInfoDto scmInfo =
          newDto(ScmInfoDto.class)
              .withScmProviderName(azureDevOpsUrl.getProviderName())
              .withRepositoryUrl(azureDevOpsUrl.getRepositoryLocation())
              .withBranch(azureDevOpsUrl.getBranch());
      return factoryDto.withScmInfo(scmInfo);
    }

    @Override
    public FactoryDto visit(FactoryDto factory) {
      // Should never happen
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) throws ApiException {
    return azureDevOpsURLParser.parse(factoryUrl);
  }
}
