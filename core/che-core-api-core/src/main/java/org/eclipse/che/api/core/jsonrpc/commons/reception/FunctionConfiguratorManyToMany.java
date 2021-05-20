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
package org.eclipse.che.api.core.jsonrpc.commons.reception;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;
import java.util.function.BiFunction;
import org.eclipse.che.api.core.jsonrpc.commons.RequestHandlerManager;
import org.slf4j.Logger;

/**
 * Function configurator to define a function to be applied when we handle incoming JSON RPC request
 * with params object that is represented by a list. The result of a function is also a list.
 *
 * @param <P> type of params list items
 * @param <R> type of resulting list items
 */
public class FunctionConfiguratorManyToMany<P, R> {
  private static final Logger LOGGER = getLogger(FunctionConfiguratorManyToMany.class);

  private final RequestHandlerManager handlerManager;

  private final String method;
  private final Class<P> pClass;
  private final Class<R> rClass;

  FunctionConfiguratorManyToMany(
      RequestHandlerManager handlerManager, String method, Class<P> pClass, Class<R> rClass) {
    this.handlerManager = handlerManager;

    this.method = method;
    this.pClass = pClass;
    this.rClass = rClass;
  }

  /**
   * Define a function to be applied
   *
   * @param function function
   */
  public void withFunction(BiFunction<String, List<P>, List<R>> function) {
    checkNotNull(function, "Request function must not be null");

    LOGGER.debug(
        "Configuring incoming request: "
            + "binary function for method: "
            + method
            + ", "
            + "params list items class: "
            + pClass
            + ", "
            + "result list items class: "
            + rClass);

    handlerManager.registerManyToMany(method, pClass, rClass, function);
  }
}
