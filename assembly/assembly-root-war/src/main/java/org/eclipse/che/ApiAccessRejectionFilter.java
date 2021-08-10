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
package org.eclipse.che;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import javax.inject.Singleton;

/**
 * Returns internal server error when request is supposed to be handled by API war. Situation occurs
 * when API war failed to start and all requests to it are handled by ROOT war.
 *
 * @author Alexander Garagatyi
 */
@Singleton
public class ApiAccessRejectionFilter implements Filter {
  public static final String ERROR_MESSAGE = "Internal server error occurs. API is not accessible";

  private static final byte[] ERROR_MESSAGE_IN_BYTES = ERROR_MESSAGE.getBytes();

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse resp = (HttpServletResponse) response;
    resp.setStatus(500);
    resp.getOutputStream().write(ERROR_MESSAGE_IN_BYTES);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void destroy() {}
}
