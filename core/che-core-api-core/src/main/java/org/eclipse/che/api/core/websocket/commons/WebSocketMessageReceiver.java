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
package org.eclipse.che.api.core.websocket.commons;

/**
 * Used as entry point for a web socket protocol message consumers.
 *
 * @author Dmitry Kuleshov
 */
public interface WebSocketMessageReceiver {
  /**
   * Receives a message by a a web socket protocol.
   *
   * @param endpointId identifier of an endpoint known to an transmitter implementation
   * @param message plain text message
   */
  void receive(String endpointId, String message);
}
