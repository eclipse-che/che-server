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

import static java.util.Collections.emptySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import java.io.IOException;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Test for {@link BasicWebSocketMessageTransmitter}
 *
 * @author Dmitry Kuleshov
 */
@Listeners(MockitoTestNGListener.class)
public class BasicWebSocketMessageTransmitterTest {
  private static final String MESSAGE = "message";
  private static final String ENDPOINT_ID = "id";

  @Mock private WebSocketSessionRegistry registry;
  @Mock private MessagesReSender reSender;
  @InjectMocks private BasicWebSocketMessageTransmitter transmitter;

  @Mock private Session session;
  @Mock private RemoteEndpoint.Basic remote;

  @BeforeMethod
  public void setUp() throws Exception {
    lenient().when(session.getBasicRemote()).thenReturn(remote);
    when(session.isOpen()).thenReturn(true);

    when(registry.get(ENDPOINT_ID)).thenReturn(Optional.of(session));
    lenient().when(registry.getSessions()).thenReturn(emptySet());
  }

  @Test
  public void shouldSendDirectMessageIfSessionIsOpenAndEndpointIsSet() throws IOException {
    transmitter.transmit(ENDPOINT_ID, MESSAGE);

    verify(session).getBasicRemote();
    verify(remote).sendText(MESSAGE);
    verify(reSender, never()).add(eq(ENDPOINT_ID), anyString());
  }

  @Test
  public void shouldAddMessageToPendingIfSessionIsNotOpenedAndEndpointIsSet() throws IOException {
    when(session.isOpen()).thenReturn(false);

    transmitter.transmit(ENDPOINT_ID, MESSAGE);

    verify(session, never()).getBasicRemote();
    verify(remote, never()).sendText(MESSAGE);
    verify(reSender).add(ENDPOINT_ID, MESSAGE);
  }
}
