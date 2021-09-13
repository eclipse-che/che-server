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
package org.eclipse.che.core.metrics;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Filter for tracking all HTTP requests through {@link ApiResponseCounter}
 *
 * @author Mykhailo Kuznietsov
 */
@Singleton
public class ApiResponseMetricFilter implements Filter {

  private ApiResponseCounter apiResponseCounter;

  @Inject
  public void setApiResponseCounter(ApiResponseCounter counter) {
    this.apiResponseCounter = counter;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    filterChain.doFilter(request, response);
    if (response instanceof HttpServletResponse) {
      apiResponseCounter.handleStatus(((HttpServletResponse) response).getStatus());
    }
  }

  @Override
  public void destroy() {}
}
