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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Alexander Garagatyi */
@Listeners(value = {MockitoTestNGListener.class})
public class ApiAccessRejectionFilterTest {
  @Mock private FilterChain chain;

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private ServletOutputStream outputStream;

  @InjectMocks private ApiAccessRejectionFilter filter;

  @Test
  public void shouldReturnError() throws Exception {
    // given
    when(response.getOutputStream()).thenReturn(outputStream);

    // when
    filter.doFilter(request, response, chain);

    // then
    verifyNoMoreInteractions(chain);
    verify(response).setStatus(500);
    verify(outputStream).write(eq(ApiAccessRejectionFilter.ERROR_MESSAGE.getBytes()));
  }
}
