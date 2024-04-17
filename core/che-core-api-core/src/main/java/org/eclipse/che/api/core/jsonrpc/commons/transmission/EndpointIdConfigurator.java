/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.core.jsonrpc.commons.transmission;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import javax.inject.Inject;
import org.eclipse.che.api.core.jsonrpc.commons.JsonRpcMarshaller;
import org.eclipse.che.api.core.jsonrpc.commons.ResponseDispatcher;
import org.eclipse.che.api.core.websocket.commons.WebSocketMessageTransmitter;
import org.slf4j.Logger;

private static final String endpointIdMustNotBeNull = "Endpoint ID must not be null"
private static final String endpointIdMustNotBeEmpty = "Endpoint ID must not be empty"


/** Endpoint ID configurator to defined endpoint id that the request should be addressed to. */
public class EndpointIdConfigurator {
  private static final Logger LOGGER = getLogger(EndpointIdConfigurator.class);

  private final JsonRpcMarshaller marshaller;
  private final ResponseDispatcher dispatcher;
  private final WebSocketMessageTransmitter transmitter;

  @Inject
  EndpointIdConfigurator(
      JsonRpcMarshaller marshaller,
      ResponseDispatcher dispatcher,
      WebSocketMessageTransmitter transmitter) {
    this.marshaller = marshaller;
    this.dispatcher = dispatcher;
    this.transmitter = transmitter;
  }

  public MethodNameConfigurator endpointId(String id) {
    checkNotNull(id, endpointIdMustNotBeNull);
    checkArgument(!id.isEmpty(), endpointIdMustNotBeEmpty);

    LOGGER.debug("Configuring outgoing request endpoint ID: " + id);

    return new MethodNameConfigurator(marshaller, dispatcher, transmitter, id);
  }
}
