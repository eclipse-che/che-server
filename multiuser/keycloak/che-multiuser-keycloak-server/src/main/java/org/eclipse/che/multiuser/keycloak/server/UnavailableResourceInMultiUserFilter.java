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
package org.eclipse.che.multiuser.keycloak.server;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import javax.inject.Singleton;

/**
 * Filter that will return HTTP status 403. Used for resources, that are not meant to be available
 * in multi-user Che. Filter omits GET requests.
 */
@Singleton
public class UnavailableResourceInMultiUserFilter implements Filter {
  protected static final String ERROR_RESPONSE_JSON_MESSAGE =
      "{\"error\" : \"This operation is not allowed since third-party user management service is configured\" }";

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String requestMethod = ((HttpServletRequest) request).getMethod();
    if (requestMethod.equals("GET")) {
      // allow request to go through
      chain.doFilter(request, response);
      return;
    }

    HttpServletResponse httpResponse = (HttpServletResponse) response;
    httpResponse.setStatus(403);
    httpResponse.setContentType("application/json");
    httpResponse.getWriter().println(ERROR_RESPONSE_JSON_MESSAGE);
  }

  @Override
  public void destroy() {}
}
