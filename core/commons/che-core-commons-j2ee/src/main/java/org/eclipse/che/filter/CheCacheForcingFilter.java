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
package org.eclipse.che.filter;

import com.xemantic.tadedon.servlet.CacheForcingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Forcing caching for the given URL resource patterns.
 *
 * @author Max Shaposhnik
 */
public class CheCacheForcingFilter extends CacheForcingFilter {

  private Set<Pattern> actionPatterns = new HashSet<>();

  @Override
  public void init(FilterConfig filterConfig) {
    Enumeration<String> names = filterConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (name.startsWith("pattern")) {
        actionPatterns.add(Pattern.compile(filterConfig.getInitParameter(name)));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    for (Pattern pattern : actionPatterns) {
      if (pattern.matcher(((HttpServletRequest) request).getRequestURI()).matches()) {
        super.doFilter(request, response, chain);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
