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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import javax.inject.Inject;
import org.eclipse.che.api.core.jsonrpc.commons.RequestHandlerManager;
import org.slf4j.Logger;

/**
 * Method configurator is used to define method name that the request handler will be associated
 * with.
 */
public class MethodNameConfigurator {
  private static final Logger LOGGER = getLogger(MethodNameConfigurator.class);

  private final RequestHandlerManager requestHandlerManager;

  @Inject
  MethodNameConfigurator(RequestHandlerManager requestHandlerManager) {
    this.requestHandlerManager = requestHandlerManager;
  }

  public ParamsConfigurator methodName(String name) {
    checkNotNull(name, "Method name must not be null");
    checkArgument(!name.isEmpty(), "Method name must not be empty");

    LOGGER.debug("Configuring incoming request method name name: " + name);

    return new ParamsConfigurator(requestHandlerManager, name);
  }
}
