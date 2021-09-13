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

import com.google.inject.Injector;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import javax.inject.Inject;

/**
 * Allows inject Guice instances on WEB SOCKET endpoint creation.
 *
 * @author Dmitry Kuleshov
 */
public class GuiceInjectorEndpointConfigurator extends ServerEndpointConfig.Configurator {
  @Inject private static Injector injector;

  public <T> T getEndpointInstance(Class<T> endpointClass) {
    return injector.getInstance(endpointClass);
  }

  @Override
  public void modifyHandshake(
      ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
    HttpSession httpSession = (HttpSession) request.getHttpSession();
    if (httpSession != null) {
      Object sessionSubject = httpSession.getAttribute("che_subject");
      if (sessionSubject != null) {
        sec.getUserProperties().put("che_subject", sessionSubject);
      }
    }
  }
}
