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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.multiuser.api.authentication.commons.SessionStore;
import org.eclipse.che.multiuser.api.authentication.commons.SubjectHttpRequestWrapper;
import org.eclipse.che.multiuser.api.authentication.commons.token.RequestTokenExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs basic environment initialization actions as follows:
 *
 * <ul>
 *   <li>Extracts token from request
 *   <li>Checks token for validity and fetch user Id (implementation specific)
 *   <li>Fetch cached {@link HttpSession} or requests to create new one
 *   <li>Gets {@link Subject} stored in session or construct new (implementation specific))
 *   <li>Set subject for current request into {@link EnvironmentContext}
 * </ul>
 *
 * @param <T> the type of intermediary type used for conversion from a string token to a Subject
 * @author Max Shaposhnyk (mshaposh@redhat.com)
 */
public abstract class MultiUserEnvironmentInitializationFilter<T> implements Filter {

  private static final Logger LOG =
      LoggerFactory.getLogger(MultiUserEnvironmentInitializationFilter.class);

  private final SessionStore sessionStore;
  private final RequestTokenExtractor tokenExtractor;

  public MultiUserEnvironmentInitializationFilter(
      SessionStore sessionStore, RequestTokenExtractor tokenExtractor) {
    this.sessionStore = sessionStore;
    this.tokenExtractor = tokenExtractor;
  }

  /**
   * Wraps {@link HttpServletRequest} to handle HTTP session creation requests and return cached one
   * if possible, so the same user will always have single session despite the fact he is using
   * different kind of tokens or several tokens of the same kind (for example logged in in different
   * browsers)
   */
  protected class SessionCachedHttpRequest extends HttpServletRequestWrapper {

    private final String userId;

    /**
     * Constructs a request object wrapping the given request.
     *
     * @throws IllegalArgumentException if the request is null
     */
    public SessionCachedHttpRequest(ServletRequest request, String userId) {
      super((HttpServletRequest) request);
      this.userId = userId;
    }

    @Override
    public HttpSession getSession() {
      return getOrCreateSession(true);
    }

    @Override
    public HttpSession getSession(boolean create) {
      return getOrCreateSession(create);
    }

    /* Finds cached session or creates new if allowed */
    private HttpSession getOrCreateSession(boolean createNew) {
      HttpSession session = super.getSession(false);
      if (session != null) {
        return session;
      }
      if (!createNew) {
        return sessionStore.getSession(userId);
      } else {
        return sessionStore.getSession(
            userId, s -> SessionCachedHttpRequest.super.getSession(true));
      }
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    Subject sessionSubject;
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    final String token = tokenExtractor.getToken(httpRequest);
    if (token == null) {
      handleMissingToken(request, response, chain);
      return;
    }
    Optional<T> maybeProcessedToken = processToken(token);
    if (maybeProcessedToken.isEmpty()) {
      handleMissingToken(request, response, chain);
      return;
    }

    T processedToken = maybeProcessedToken.get();

    String userId = getUserId(processedToken);

    // retrieve cached session if any or create new
    httpRequest = new SessionCachedHttpRequest(request, userId);
    HttpSession session = httpRequest.getSession(true);
    // retrieve and check / create new subject
    sessionSubject = (Subject) session.getAttribute(CHE_SUBJECT_ATTRIBUTE);
    if (sessionSubject == null) {
      sessionSubject = extractSubject(token, processedToken);
      session.setAttribute(CHE_SUBJECT_ATTRIBUTE, sessionSubject);
    } else if (!sessionSubject.getUserId().equals(userId)) {
      LOG.debug(
          "Invalidating session with mismatched user IDs: old was '{}', new is '{}'.",
          sessionSubject.getUserId(),
          userId);
      session.invalidate();
      HttpSession new_session = httpRequest.getSession(true);
      sessionSubject = extractSubject(token, processedToken);
      new_session.setAttribute(CHE_SUBJECT_ATTRIBUTE, sessionSubject);
    } else if (!sessionSubject.getToken().equals(token)) {
      sessionSubject = extractSubject(token, processedToken);
      session.setAttribute(CHE_SUBJECT_ATTRIBUTE, sessionSubject);
    }
    // set current subject
    try {
      EnvironmentContext.getCurrent().setSubject(sessionSubject);
      chain.doFilter(new SubjectHttpRequestWrapper(httpRequest, sessionSubject), response);
    } finally {
      EnvironmentContext.reset();
    }
  }

  /**
   * Processes the token and creates implementation-specific intermediary type using which the
   * subclasses can extract different kinds of information like user ID or subject.
   *
   * <p>This <b>may</b> also imply verification of token signature or encryption for
   * encrypted/signed tokens (like JWT etc).
   *
   * @param token the token to process
   * @return a processed token or null if the token could not be processed for valid reasons (like
   *     expiry or not matching any user).
   */
  protected abstract Optional<T> processToken(String token);

  /**
   * Retrieves the id of the user from given authentication token.
   *
   * @param processedToken the processed authentication string
   * @return user id given token belongs to
   */
  protected abstract String getUserId(T processedToken);

  /**
   * Calculates user {@link Subject} from given authentication token.
   *
   * @param token the original authentication token string
   * @param processedToken the processed authentication string
   * @return constructed subject
   */
  protected abstract Subject extractSubject(String token, T processedToken) throws ServletException;

  /**
   * Describes behavior when the token is missed. In case if performing authentication by given
   * implementing filter isn't required, it may be invocation of {@link FilterChain#doFilter} or
   * throwing of appropriate exception otherwise.
   *
   * @param request http request
   * @param response http response
   * @param chain filter chain
   * @throws IOException inherited from {@link FilterChain#doFilter}
   * @throws ServletException inherited from {@link FilterChain#doFilter}
   */
  protected abstract void handleMissingToken(
      ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException;

  /**
   * Sends appropriate error status code and message into response.
   *
   * @param res response to send error message
   * @param errorCode status code to send
   * @param message sessage to send
   * @throws IOException inherited from {@link HttpServletResponse#sendError}
   */
  protected void sendError(ServletResponse res, int errorCode, String message) throws IOException {
    HttpServletResponse response = (HttpServletResponse) res;
    response.sendError(errorCode, message);
  }
}
