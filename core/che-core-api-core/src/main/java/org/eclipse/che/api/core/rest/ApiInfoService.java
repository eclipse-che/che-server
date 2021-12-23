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
package org.eclipse.che.api.core.rest;

import static java.util.stream.Collectors.toList;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.function.Function;
import javax.inject.Inject;
import org.eclipse.che.api.core.rest.annotations.OPTIONS;
import org.eclipse.che.api.core.rest.shared.dto.ApiInfo;
import org.eclipse.che.commons.annotation.Nullable;
import org.everrest.core.ObjectFactory;
import org.everrest.core.ResourceBinder;
import org.everrest.core.resource.ResourceDescriptor;
import org.everrest.services.RestServicesList.RootResource;
import org.everrest.services.RestServicesList.RootResourcesList;

/** @author andrew00x */
@Path("/")
public class ApiInfoService {

  @Inject private ApiInfo apiInfo;

  @OPTIONS
  public ApiInfo info() {
    return apiInfo;
  }

  @GET
  @Produces({MediaType.APPLICATION_JSON})
  public RootResourcesList listJSON(@Context ServletContext context) {
    ResourceBinder binder = (ResourceBinder) context.getAttribute(ResourceBinder.class.getName());
    return new RootResourcesList(
        binder.getResources().stream()
            .map(
                new Function<ObjectFactory<ResourceDescriptor>, RootResource>() {
                  @Nullable
                  @Override
                  public RootResource apply(ObjectFactory<ResourceDescriptor> input) {
                    ResourceDescriptor descriptor = input.getObjectModel();
                    return new RootResource(
                        descriptor.getObjectClass().getName(), //
                        descriptor.getPathValue().getPath(), //
                        descriptor.getUriPattern().getRegex());
                  }
                })
            .collect(toList()));
  }
}
