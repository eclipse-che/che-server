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
package org.eclipse.che.multiuser.api.authentication.commons.filter;

import static org.eclipse.che.multiuser.api.authentication.commons.Constants.CHE_SUBJECT_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.multiuser.api.authentication.commons.SessionStore;
import org.eclipse.che.multiuser.api.authentication.commons.token.RequestTokenExtractor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(value = MockitoTestNGListener.class)
public class MultiUserEnvironmentInitializationFilterTest {

  @Mock private SessionStore sessionStore;
  @Mock private RequestTokenExtractor tokenExtractor;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private HttpSession session;
  @Mock private FilterChain chain;

  private final String userId = "user-abc-123";
  private final String token = "token-abc123";
  private final Subject subject = new SubjectImpl("user", userId, token, false);

  private MultiUserEnvironmentInitializationFilter<Object> filter;

  @BeforeMethod
  @SuppressWarnings("unchecked")
  public void setUp() throws Exception {
    filter =
        mock(
            MultiUserEnvironmentInitializationFilter.class,
            withSettings()
                .defaultAnswer(Mockito.CALLS_REAL_METHODS)
                .useConstructor(sessionStore, tokenExtractor));
    lenient().when(filter.getUserId(any())).thenReturn(userId);
    lenient().when(filter.extractSubject(anyString(), any())).thenReturn(subject);
    // pretend like we successfully processed the token unless defined otherwise in the tests
    lenient().when(filter.processToken(anyString())).thenReturn(Optional.of(new Object()));
  }

  @Test
  public void shouldCallHandleMissingTokenIfTokenIsNull() throws Exception {
    // given
    when(tokenExtractor.getToken(any(HttpServletRequest.class))).thenReturn(null);

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(tokenExtractor).getToken(eq(request));
    verify(filter).handleMissingToken(eq(request), eq(response), eq(chain));
    verifyNoMoreInteractions(request);
    verify(filter, never()).getUserId(any());
    verify(filter, never()).extractSubject(anyString(), any());
  }

  @Test
  public void shouldCallHandleMissingTokenIfTokenCannotBeProcessed() throws Exception {
    // given
    when(tokenExtractor.getToken(any(HttpServletRequest.class))).thenReturn("abc");
    when(filter.processToken(anyString())).thenReturn(Optional.empty());

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(tokenExtractor).getToken(eq(request));
    verify(filter).handleMissingToken(eq(request), eq(response), eq(chain));
    verifyNoMoreInteractions(request);
    verify(filter, never()).getUserId(any());
    verify(filter, never()).extractSubject(anyString(), any());
  }

  @Test
  public void shouldGetSessionFromStoreWithCorrectUserId() throws Exception {
    when(tokenExtractor.getToken(any(HttpServletRequest.class))).thenReturn(token);
    when(sessionStore.getSession(eq(userId), any())).thenReturn(session);

    // when
    filter.doFilter(request, response, chain);
    // then
    verify(request).getSession(eq(false));
    verify(tokenExtractor).getToken(eq(request));
    verify(filter).getUserId(any());
    verify(sessionStore).getSession(eq(userId), any());
  }

  @Test
  public void shouldCreateSubjectIfSessionDidNotContainOne() throws Exception {
    when(tokenExtractor.getToken(any(HttpServletRequest.class))).thenReturn(token);
    when(sessionStore.getSession(eq(userId), any())).thenReturn(session);
    when(session.getAttribute(eq(CHE_SUBJECT_ATTRIBUTE))).thenReturn(null);
    // when
    filter.doFilter(request, response, chain);
    // then
    verify(filter).extractSubject(eq(token), any());
  }

  @Test
  public void shouldReCreateSubjectIfTokensDidNotMatch() throws Exception {
    Subject otherSubject =
        new SubjectImpl(subject.getUserId(), subject.getUserName(), "token111", false);
    when(tokenExtractor.getToken(any(HttpServletRequest.class))).thenReturn(token);
    when(sessionStore.getSession(eq(userId), any())).thenReturn(session);
    when(session.getAttribute(eq(CHE_SUBJECT_ATTRIBUTE))).thenReturn(otherSubject);
    // when
    filter.doFilter(request, response, chain);
    // then
    verify(filter).extractSubject(eq(token), any());
  }

  @Test
  public void shouldInvalidateSessionIfUserChanged() throws Exception {
    Subject otherSubject = new SubjectImpl("another_user", "user987", "token111", false);
    when(tokenExtractor.getToken(any(HttpServletRequest.class))).thenReturn(token);
    when(sessionStore.getSession(eq(userId), any())).thenReturn(session);
    when(session.getAttribute(eq(CHE_SUBJECT_ATTRIBUTE))).thenReturn(otherSubject);
    // when
    filter.doFilter(request, response, chain);
    // then
    verify(session).invalidate();
    verify(filter).extractSubject(eq(token), any());
  }

  @Test
  public void shouldSetSubjectIntoEnvironmentContext() throws Exception {
    EnvironmentContext context = spy(EnvironmentContext.getCurrent());
    EnvironmentContext.setCurrent(context);
    when(tokenExtractor.getToken(any(HttpServletRequest.class))).thenReturn(token);
    when(sessionStore.getSession(eq(userId), any())).thenReturn(session);
    when(session.getAttribute(eq(CHE_SUBJECT_ATTRIBUTE))).thenReturn(subject);
    // when
    filter.doFilter(request, response, chain);
    // then
    verify(context).setSubject(eq(subject));
  }
}
