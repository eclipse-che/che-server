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

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Request;
import java.io.IOException;

/**
 * JAX-RS implementation of download filter.
 *
 * @author Florent Benoit
 */
public class JAXRSDownloadFileResponseFilter extends DownloadFileResponseFilter
    implements ContainerResponseFilter {

  /**
   * JAX-RS Filter method called after a response has been provided for a request
   *
   * <p>Filters in the filter chain are ordered according to their {@code
   * jakarta.annotation.Priority} class-level annotation value.
   *
   * @param requestContext request context.
   * @param responseContext response context.
   * @throws IOException if an I/O exception occurs.
   */
  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {

    // Apply header if all if correct
    Request request = requestContext.getRequest();
    String filename =
        getFileName(
            request,
            responseContext.getMediaType(),
            requestContext.getUriInfo(),
            responseContext.getStatus());
    if (filename != null) {
      if (hasCompliantEntity(responseContext.getEntity())) {
        responseContext
            .getHeaders()
            .putSingle(CONTENT_DISPOSITION, "attachment; filename=" + filename);
      }
    }
  }
}
