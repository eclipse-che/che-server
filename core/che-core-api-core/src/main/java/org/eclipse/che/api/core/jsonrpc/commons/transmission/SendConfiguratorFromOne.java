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
package org.eclipse.che.api.core.jsonrpc.commons.transmission;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;
import org.eclipse.che.api.core.jsonrpc.commons.JsonRpcMarshaller;
import org.eclipse.che.api.core.jsonrpc.commons.JsonRpcParams;
import org.eclipse.che.api.core.jsonrpc.commons.JsonRpcPromise;
import org.eclipse.che.api.core.jsonrpc.commons.JsonRpcRequest;
import org.eclipse.che.api.core.jsonrpc.commons.ResponseDispatcher;
import org.eclipse.che.api.core.websocket.commons.WebSocketMessageTransmitter;
import org.slf4j.Logger;

/**
 * Configurator defines the type of a result (if present) and send a request. Result types that are
 * supported: {@link String}, {@link Boolean}, {@link Double}, {@link Void} and DTO. This
 * configurator is used when we have defined request params as a single object.
 *
 * @param <P> type of params objects
 */
public class SendConfiguratorFromOne<P> {
  private static final Logger LOGGER = getLogger(SendConfiguratorFromOne.class);

  private final ResponseDispatcher dispatcher;
  private final WebSocketMessageTransmitter transmitter;
  private final JsonRpcMarshaller marshaller;

  private final String method;
  private final P pValue;
  private final String endpointId;

  SendConfiguratorFromOne(
      JsonRpcMarshaller marshaller,
      ResponseDispatcher dispatcher,
      WebSocketMessageTransmitter transmitter,
      String method,
      P pValue,
      String endpointId) {
    this.marshaller = marshaller;
    this.dispatcher = dispatcher;
    this.transmitter = transmitter;

    this.method = method;
    this.pValue = pValue;
    this.endpointId = endpointId;
  }

  public void sendAndSkipResult() {
    LOGGER.debug(
        "Transmitting request: "
            + "endpoint ID: "
            + endpointId
            + ", "
            + "method: "
            + method
            + ", "
            + (pValue != null ? "params object class: " + pValue.getClass() + ", " : "")
            + "params list value"
            + pValue);

    transmitNotification();
  }

  public <R> JsonRpcPromise<R> sendAndReceiveResultAsDto(final Class<R> rClass) {
    return sendAndReceiveResultAsDto(rClass, 0);
  }

  public <R> JsonRpcPromise<R> sendAndReceiveResultAsDto(
      final Class<R> rClass, int timeoutInMillis) {
    checkNotNull(rClass, "Result class value must not be null");

    final String requestId = transmitRequest();

    LOGGER.debug(
        "Transmitting request: "
            + "endpoint ID: "
            + endpointId
            + ", "
            + "request ID: "
            + requestId
            + ", "
            + "method: "
            + method
            + ", "
            + (pValue != null ? "params object class: " + pValue.getClass() + ", " : "")
            + "params list value"
            + pValue
            + ", "
            + "result object class: "
            + rClass);

    return dispatcher.registerPromiseForSingleObject(
        endpointId, requestId, rClass, timeoutInMillis);
  }

  public JsonRpcPromise<String> sendAndReceiveResultAsString() {
    return sendAndReceiveResultAsString(0);
  }

  public JsonRpcPromise<String> sendAndReceiveResultAsString(int timeoutInMillis) {
    final String requestId = transmitRequest();

    LOGGER.debug(
        "Transmitting request: "
            + "endpoint ID: "
            + endpointId
            + ", "
            + "request ID: "
            + requestId
            + ", "
            + "method: "
            + method
            + ", "
            + (pValue != null ? "params object class: " + pValue.getClass() + ", " : "")
            + "params list value"
            + pValue
            + ", "
            + "result object class: "
            + String.class);

    return dispatcher.registerPromiseForSingleObject(
        endpointId, requestId, String.class, timeoutInMillis);
  }

  public JsonRpcPromise<Double> sendAndReceiveResultAsDouble() {
    return sendAndReceiveResultAsDouble(0);
  }

  public JsonRpcPromise<Double> sendAndReceiveResultAsDouble(int timeoutInMillis) {
    final String requestId = transmitRequest();

    LOGGER.debug(
        "Transmitting request: "
            + "endpoint ID: "
            + endpointId
            + ", "
            + "request ID: "
            + requestId
            + ", "
            + "method: "
            + method
            + ", "
            + (pValue != null ? "params object class: " + pValue.getClass() + ", " : "")
            + "params list value"
            + pValue
            + ", "
            + "result object class: "
            + Double.class);

    return dispatcher.registerPromiseForSingleObject(
        endpointId, requestId, Double.class, timeoutInMillis);
  }

  public JsonRpcPromise<Boolean> sendAndReceiveResultAsBoolean() {
    return sendAndReceiveResultAsBoolean(0);
  }

  public JsonRpcPromise<Boolean> sendAndReceiveResultAsBoolean(int timeoutInMillis) {
    final String requestId = transmitRequest();

    LOGGER.debug(
        "Transmitting request: "
            + "endpoint ID: "
            + endpointId
            + ", "
            + "request ID: "
            + requestId
            + ", "
            + "method: "
            + method
            + ", "
            + (pValue != null ? "params object class: " + pValue.getClass() + ", " : "")
            + "params list value"
            + pValue
            + ", "
            + "result object class: "
            + Boolean.class);

    return dispatcher.registerPromiseForSingleObject(
        endpointId, requestId, Boolean.class, timeoutInMillis);
  }

  public <R> JsonRpcPromise<List<R>> sendAndReceiveResultAsListOfDto(Class<R> rClass) {
    return sendAndReceiveResultAsListOfDto(rClass, 0);
  }

  public <R> JsonRpcPromise<List<R>> sendAndReceiveResultAsListOfDto(
      Class<R> rClass, int timeoutInMillis) {
    checkNotNull(rClass, "Result class value must not be null");

    final String requestId = transmitRequest();

    LOGGER.debug(
        "Transmitting request: "
            + "endpoint ID: "
            + endpointId
            + ", "
            + "request ID: "
            + requestId
            + ", "
            + "method: "
            + method
            + ", "
            + (pValue != null ? "params object class: " + pValue.getClass() + ", " : "")
            + "params list value"
            + pValue
            + ", "
            + "result list items class: "
            + rClass);

    return dispatcher.registerPromiseForListOfObjects(
        endpointId, requestId, rClass, timeoutInMillis);
  }

  public JsonRpcPromise<List<String>> sendAndReceiveResultAsListOfString() {
    return sendAndReceiveResultAsListOfString(0);
  }

  public JsonRpcPromise<List<String>> sendAndReceiveResultAsListOfString(int timeoutInMillis) {
    final String requestId = transmitRequest();

    LOGGER.debug(
        "Transmitting request: "
            + "endpoint ID: "
            + endpointId
            + ", "
            + "request ID: "
            + requestId
            + ", "
            + "method: "
            + method
            + ", "
            + (pValue != null ? "params object class: " + pValue.getClass() + ", " : "")
            + "params list value"
            + pValue
            + ", "
            + "result list items class: "
            + String.class);

    return dispatcher.registerPromiseForListOfObjects(
        endpointId, requestId, String.class, timeoutInMillis);
  }

  public JsonRpcPromise<List<Boolean>> sendAndReceiveResultAsListOfBoolean() {
    return sendAndReceiveResultAsListOfBoolean(0);
  }

  public JsonRpcPromise<List<Boolean>> sendAndReceiveResultAsListOfBoolean(int timeoutInMillis) {
    final String requestId = transmitRequest();

    LOGGER.debug(
        "Transmitting request: "
            + "endpoint ID: "
            + endpointId
            + ", "
            + "request ID: "
            + requestId
            + ", "
            + "method: "
            + method
            + ", "
            + (pValue != null ? "params object class: " + pValue.getClass() + ", " : "")
            + "params list value"
            + pValue
            + ", "
            + "result list items class: "
            + Boolean.class);

    return dispatcher.registerPromiseForListOfObjects(
        endpointId, requestId, Boolean.class, timeoutInMillis);
  }

  public JsonRpcPromise<List<Double>> sendAndReceiveResultAsListOfDouble() {
    return sendAndReceiveResultAsListOfDouble(0);
  }

  public JsonRpcPromise<List<Double>> sendAndReceiveResultAsListOfDouble(int timeoutInMillis) {
    final String requestId = transmitRequest();

    LOGGER.debug(
        "Transmitting request: "
            + "endpoint ID: "
            + endpointId
            + ", "
            + "request ID: "
            + requestId
            + ", "
            + "method: "
            + method
            + ", "
            + (pValue != null ? "params object class: " + pValue.getClass() + ", " : "")
            + "params list value"
            + pValue
            + ", "
            + "result list items class: "
            + Double.class);

    return dispatcher.registerPromiseForListOfObjects(
        endpointId, requestId, Double.class, timeoutInMillis);
  }

  private void transmitNotification() {
    JsonRpcParams params = new JsonRpcParams(pValue);
    JsonRpcRequest request = new JsonRpcRequest(null, method, params);
    String message = marshaller.marshall(request);
    transmitter.transmit(endpointId, message);
  }

  private String transmitRequest() {
    Integer id = MethodNameConfigurator.id.incrementAndGet();
    String requestId = id.toString();

    JsonRpcParams params = new JsonRpcParams(pValue);
    JsonRpcRequest request = new JsonRpcRequest(requestId, method, params);
    String message = marshaller.marshall(request);
    transmitter.transmit(endpointId, message);

    return requestId;
  }
}
