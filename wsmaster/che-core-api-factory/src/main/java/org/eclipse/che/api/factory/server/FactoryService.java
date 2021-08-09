/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
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
import java.util.Set;
import javax.inject.Inject;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.user.server.UserManager;

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

  private final UserManager userManager;
  private final FactoryAcceptValidator acceptValidator;
  private final FactoryParametersResolverHolder factoryParametersResolverHolder;
  private final AdditionalFilenamesProvider additionalFilenamesProvider;

  @Inject
  public FactoryService(
      UserManager userManager,
      FactoryAcceptValidator acceptValidator,
      FactoryParametersResolverHolder factoryParametersResolverHolder,
      AdditionalFilenamesProvider additionalFilenamesProvider) {
    this.userManager = userManager;
    this.acceptValidator = acceptValidator;
    this.factoryParametersResolverHolder = factoryParametersResolverHolder;
    this.additionalFilenamesProvider = additionalFilenamesProvider;
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

  /** Injects factory links. If factory is named then accept named link will be injected. */
  private FactoryMetaDto injectLinks(FactoryMetaDto factory, Map<String, String> parameters) {
    String username = null;
    if (factory.getCreator() != null && factory.getCreator().getUserId() != null) {
      try {
        username = userManager.getById(factory.getCreator().getUserId()).getName();
      } catch (ApiException ignored) {
        // when impossible to get username then named factory link won't be injected
      }
    }
    return factory.withLinks(
        createLinks(
            factory,
            getServiceContext(),
            additionalFilenamesProvider,
            username,
            parameters.get(URL_PARAMETER_NAME)));
  }

  /** Usage of a dedicated class to manage the optional service-specific resolvers */
  protected static class FactoryParametersResolverHolder {

    @Inject
    @SuppressWarnings("unused")
    private Set<FactoryParametersResolver> specificFactoryParametersResolvers;

    @Inject private DefaultFactoryParameterResolver defaultFactoryResolver;

    /**
     * Provides a suitable resolver for the given parameters. If there is no at least one resolver
     * able to process parameters,then {@link BadRequestException} will be thrown
     *
     * @return suitable service-specific resolver or default one
     */
    public FactoryParametersResolver getFactoryParametersResolver(Map<String, String> parameters)
        throws BadRequestException {
      for (FactoryParametersResolver factoryParametersResolver :
          specificFactoryParametersResolvers) {
        if (factoryParametersResolver.accept(parameters)) {
          return factoryParametersResolver;
        }
      }
      if (defaultFactoryResolver.accept(parameters)) {
        return defaultFactoryResolver;
      } else {
        throw new BadRequestException(FACTORY_NOT_RESOLVABLE);
      }
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
