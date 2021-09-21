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
package org.eclipse.che.api.core.websocket.impl;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests for {@link MessagesReSender}
 *
 * @author Dmitry Kuleshov
 */
@Listeners(MockitoTestNGListener.class)
public class MessagesReSenderTest {
  private static final String MESSAGE = "message";
  private static final String ENDPOINT_ID = "id";

  @Mock private WebSocketSessionRegistry sessionRegistry;
  @InjectMocks private MessagesReSender reSender;

  @Mock private Session session;
  @Mock private RemoteEndpoint.Async endpoint;

  @BeforeMethod
  public void beforeMethod() {
    when(sessionRegistry.get(anyString())).thenReturn(Optional.of(session));
    lenient().when(session.getAsyncRemote()).thenReturn(endpoint);
    lenient().when(session.isOpen()).thenReturn(true);
  }

  @BeforeMethod
  public void before() {
    reSender = new MessagesReSender(sessionRegistry);
  }

  @Test
  public void shouldStopIfSessionIsNotRegistered() {
    when(sessionRegistry.get(anyString())).thenReturn(Optional.empty());

    reSender.add(ENDPOINT_ID, MESSAGE);

    reSender.resend(ENDPOINT_ID);

    verify(sessionRegistry).get(ENDPOINT_ID);
    verify(session, never()).getAsyncRemote();
    verify(endpoint, never()).sendText(MESSAGE);
  }

  @Test
  public void shouldKeepMessagesIfSessionIsClosed() {
    reSender.add(ENDPOINT_ID, MESSAGE);

    when(session.isOpen()).thenReturn(false);
    reSender.resend(ENDPOINT_ID);

    verify(session, never()).getAsyncRemote();
    verify(endpoint, never()).sendText(MESSAGE);

    when(session.isOpen()).thenReturn(true);
    reSender.resend(ENDPOINT_ID);

    verify(session).getAsyncRemote();
    verify(endpoint).sendText(MESSAGE);
  }

  @Test
  public void shouldProperlyAddForSingleEndpoint() {
    reSender.add(ENDPOINT_ID, MESSAGE);

    reSender.resend(ENDPOINT_ID);

    verify(sessionRegistry).get(ENDPOINT_ID);
    verify(session).getAsyncRemote();
    verify(endpoint).sendText(MESSAGE);
  }

  @Test
  public void shouldProperlyAddForSeveralEndpoints() {
    reSender.add(ENDPOINT_ID, MESSAGE);
    reSender.add("1", MESSAGE);

    reSender.resend(ENDPOINT_ID);
    reSender.resend("1");

    verify(sessionRegistry).get(ENDPOINT_ID);
    verify(sessionRegistry).get("1");
    verify(session, times(2)).getAsyncRemote();
    verify(endpoint, times(2)).sendText(MESSAGE);
  }

  @Test
  public void shouldClearOnExtractionForSingleEndpoint() {
    reSender.add(ENDPOINT_ID, MESSAGE);

    reSender.resend(ENDPOINT_ID);
    verify(sessionRegistry).get(ENDPOINT_ID);
    verify(session).getAsyncRemote();
    verify(endpoint).sendText(MESSAGE);

    reSender.resend(ENDPOINT_ID);
    verify(sessionRegistry).get(ENDPOINT_ID);
    verify(session).getAsyncRemote();
    verify(endpoint).sendText(MESSAGE);
  }

  @Test
  public void shouldClearOnExtractionForSeveralEndpoint() {
    reSender.add(ENDPOINT_ID, MESSAGE);
    reSender.add("1", MESSAGE);

    reSender.resend(ENDPOINT_ID);
    reSender.resend("1");

    verify(sessionRegistry).get(ENDPOINT_ID);
    verify(sessionRegistry).get("1");
    verify(session, times(2)).getAsyncRemote();
    verify(endpoint, times(2)).sendText(MESSAGE);

    reSender.resend(ENDPOINT_ID);
    reSender.resend("1");

    verify(sessionRegistry).get(ENDPOINT_ID);
    verify(sessionRegistry).get("1");
    verify(session, times(2)).getAsyncRemote();
    verify(endpoint, times(2)).sendText(MESSAGE);
  }
}
