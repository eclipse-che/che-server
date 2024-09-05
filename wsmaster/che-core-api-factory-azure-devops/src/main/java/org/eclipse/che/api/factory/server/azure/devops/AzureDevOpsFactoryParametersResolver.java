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
package org.eclipse.che.api.factory.server.azure.devops;

import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import com.google.common.base.Strings;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.BaseFactoryParameterResolver;
import org.eclipse.che.api.factory.server.FactoryParametersResolver;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.factory.shared.dto.FactoryVisitor;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;

/**
 * Provides Factory Parameters resolver for Azure DevOps repositories.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class AzureDevOpsFactoryParametersResolver extends BaseFactoryParameterResolver
    implements FactoryParametersResolver {

  private static final String PROVIDER_NAME = "azure-devops";

  /** Parser which will allow to check validity of URLs and create objects. */
  private final AzureDevOpsURLParser azureDevOpsURLParser;

  private final URLFetcher urlFetcher;
  private final URLFactoryBuilder urlFactoryBuilder;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  @Inject
  public AzureDevOpsFactoryParametersResolver(
      AzureDevOpsURLParser azureDevOpsURLParser,
      URLFetcher urlFetcher,
      URLFactoryBuilder urlFactoryBuilder,
      PersonalAccessTokenManager personalAccessTokenManager,
      AuthorisationRequestManager authorisationRequestManager) {
    super(authorisationRequestManager, urlFactoryBuilder, PROVIDER_NAME);
    this.azureDevOpsURLParser = azureDevOpsURLParser;
    this.urlFetcher = urlFetcher;
    this.urlFactoryBuilder = urlFactoryBuilder;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public boolean accept(@NotNull final Map<String, String> factoryParameters) {
    return factoryParameters.containsKey(URL_PARAMETER_NAME)
        && azureDevOpsURLParser.isValid(factoryParameters.get(URL_PARAMETER_NAME));
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public FactoryMetaDto createFactory(@NotNull final Map<String, String> factoryParameters)
      throws ApiException {
    // no need to check null value of url parameter as accept() method has performed the check
    final AzureDevOpsUrl azureDevOpsUrl =
        azureDevOpsURLParser.parse(factoryParameters.get(URL_PARAMETER_NAME));

    // create factory from the following location if location exists, else create default factory
    return createFactory(
        factoryParameters,
        azureDevOpsUrl,
        new AzureDevOpsFactoryVisitor(azureDevOpsUrl),
        new AzureDevOpsAuthorizingFileContentProvider(
            azureDevOpsUrl, urlFetcher, personalAccessTokenManager));
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
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) throws ApiException {
    return azureDevOpsURLParser.parse(factoryUrl);
  }

  private SourceStorageDto buildWorkspaceConfigSource(AzureDevOpsUrl azureDevOpsUrl) {
    Map<String, String> parameters = new HashMap<>(1);
    if (!Strings.isNullOrEmpty(azureDevOpsUrl.getBranch())) {
      parameters.put("branch", azureDevOpsUrl.getBranch());
    }
    if (!Strings.isNullOrEmpty(azureDevOpsUrl.getTag())) {
      parameters.put("tag", azureDevOpsUrl.getTag());
    }

    return newDto(SourceStorageDto.class)
        .withLocation(azureDevOpsUrl.getRepositoryLocation())
        .withType("git")
        .withParameters(parameters);
  }
}
