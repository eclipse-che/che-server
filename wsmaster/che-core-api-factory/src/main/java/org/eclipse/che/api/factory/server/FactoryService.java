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
package org.eclipse.che.api.factory.server;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Collections.singletonMap;
import static java.util.Comparator.comparingInt;
import static org.eclipse.che.api.factory.server.ApiExceptionMapper.toApiException;
import static org.eclipse.che.api.factory.server.FactoryLinksHelper.createLinks;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;

/**
 * Defines Factory REST API.
 *
 * @author Anton Korneta
 * @author Florent Benoit
 */
@Tag(name = "factory", description = "Factory manager")
@Path("/factory")
public class FactoryService extends Service {

  /** Error message if there is no plugged resolver. */
  public static final String FACTORY_NOT_RESOLVABLE =
      "Cannot build factory with any of the provided parameters. Please check parameters correctness, and resend query.";

  /** Validate query parameter. If true, factory will be validated */
  public static final String VALIDATE_QUERY_PARAMETER = "validate";

  private final FactoryAcceptValidator acceptValidator;
  private final FactoryParametersResolverHolder factoryParametersResolverHolder;
  private final AdditionalFilenamesProvider additionalFilenamesProvider;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private final AuthorisationRequestManager authorisationRequestManager;

  @Inject
  public FactoryService(
      FactoryAcceptValidator acceptValidator,
      FactoryParametersResolverHolder factoryParametersResolverHolder,
      AdditionalFilenamesProvider additionalFilenamesProvider,
      PersonalAccessTokenManager personalAccessTokenManager,
      AuthorisationRequestManager authorisationRequestManager) {
    this.acceptValidator = acceptValidator;
    this.factoryParametersResolverHolder = factoryParametersResolverHolder;
    this.additionalFilenamesProvider = additionalFilenamesProvider;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.authorisationRequestManager = authorisationRequestManager;
  }

  @POST
  @Path("/resolver")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Create factory by providing map of parameters. Get JSON with factory information",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Factory successfully built from parameters"),
        @ApiResponse(
            responseCode = "400",
            description = "Missed required parameters, failed to validate factory"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public FactoryMetaDto resolveFactory(
      @Parameter(description = "Parameters provided to create factories")
          Map<String, String> parameters,
      @Parameter(
              description =
                  "Whether or not to validate values like it is done when accepting a Factory")
          @DefaultValue("false")
          @QueryParam(VALIDATE_QUERY_PARAMETER)
          Boolean validate)
      throws ApiException {

    // check parameter
    requiredNotNull(parameters, "Factory build parameters");

    // search matching resolver and create factory from matching resolver
    FactoryMetaDto resolvedFactory =
        factoryParametersResolverHolder
            .getFactoryParametersResolver(parameters)
            .createFactory(parameters);
    if (resolvedFactory == null) {
      throw new BadRequestException(FACTORY_NOT_RESOLVABLE);
    }
    if (validate) {
      acceptValidator.validateOnAccept(resolvedFactory);
    }

    resolvedFactory = injectLinks(resolvedFactory, parameters);

    return resolvedFactory;
  }

  @POST
  @Path("/token/refresh")
  @Operation(
      summary = "Validate the the factory related OAuth token and update/create it if needed",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description =
                "The factory related OAuth token is valid or has been updated successfully"),
        @ApiResponse(
            responseCode = "401",
            description = "Failed to update the factory related OAuth token"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public void refreshToken(@Parameter(description = "Factory url") @QueryParam("url") String url)
      throws ApiException {

    // check parameter
    requiredNotNull(url, "Factory url");

    try {
      FactoryParametersResolver factoryParametersResolver =
          factoryParametersResolverHolder.getFactoryParametersResolver(
              singletonMap(URL_PARAMETER_NAME, url));
      if (!authorisationRequestManager.isStored(factoryParametersResolver.getProviderName())) {
        String scmServerUrl = factoryParametersResolver.parseFactoryUrl(url).getProviderUrl();
        if (Boolean.parseBoolean(System.getenv("CHE_FORCE_REFRESH_PERSONAL_ACCESS_TOKEN"))) {
          personalAccessTokenManager.forceRefreshPersonalAccessToken(scmServerUrl);
        } else {
          personalAccessTokenManager.getAndStore(scmServerUrl);
        }
      }
    } catch (ScmConfigurationPersistenceException | UnsatisfiedScmPreconditionException e) {
      throw new ApiException(e);
    } catch (ScmUnauthorizedException e) {
      throw toApiException(e);
    } catch (ScmCommunicationException e) {
      throw toApiException(e);
    } catch (UnknownScmProviderException e) {
      // ignore the exception as it is not a problem if the provider from the given URL is unknown
    }
  }

  /** Injects factory links */
  private FactoryMetaDto injectLinks(FactoryMetaDto factory, Map<String, String> parameters) {
    return factory.withLinks(
        createLinks(
            factory,
            getServiceContext(),
            additionalFilenamesProvider,
            null,
            parameters.get(URL_PARAMETER_NAME)));
  }

  /** Usage of a dedicated class to manage the optional service-specific resolvers */
  protected static class FactoryParametersResolverHolder {

    @Inject
    @SuppressWarnings("unused")
    private Set<FactoryParametersResolver> specificFactoryParametersResolvers;

    /**
     * Provides a suitable resolver for the given parameters. If there is no at least one resolver
     * able to process parameters,then {@link BadRequestException} will be thrown
     *
     * @return suitable service-specific resolver or default one
     */
    public FactoryParametersResolver getFactoryParametersResolver(Map<String, String> parameters)
        throws BadRequestException {
      Optional<FactoryParametersResolver> resolverOptional =
          specificFactoryParametersResolvers.stream()
              .filter(
                  r -> {
                    try {
                      return r.accept(parameters);
                    } catch (IllegalArgumentException e) {
                      return false;
                    }
                  })
              .max(comparingInt(r -> r.priority().getValue()));
      if (resolverOptional.isPresent()) {
        return resolverOptional.get();
      }
      throw new BadRequestException(FACTORY_NOT_RESOLVABLE);
    }
  }

  /**
   * Checks object reference is not {@code null}
   *
   * @param object object reference to check
   * @param subject used as subject of exception message "{subject} required"
   * @throws BadRequestException when object reference is {@code null}
   */
  private static void requiredNotNull(Object object, String subject) throws BadRequestException {
    if (object == null) {
      throw new BadRequestException(subject + " required");
    }
  }
}
