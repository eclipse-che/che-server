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
package org.eclipse.che.api.core.rest;

import com.google.common.annotations.Beta;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * This interceptor must be bound for the method {@link MessageBodyReader#readFrom(Class, Type,
 * Annotation[], MediaType, MultivaluedMap, InputStream)}
 *
 * @author Yevhenii Voevodin
 */
@Beta
public class MessageBodyAdapterInterceptor implements MethodInterceptor {

  private final Map<Class<?>, MessageBodyAdapter> adapters = new HashMap<>();

  @Inject
  public void init(Set<MessageBodyAdapter> adapters) {
    for (MessageBodyAdapter adapter : adapters) {
      for (Class<?> trigger : adapter.getTriggers()) {
        this.adapters.put(trigger, adapter);
      }
    }
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    final Object[] args = invocation.getArguments();
    final MessageBodyAdapter adapter = adapters.get((Class<?>) args[0]);
    if (adapter != null) {
      args[args.length - 1] = adapter.adapt((InputStream) args[args.length - 1]);
    }
    return invocation.proceed();
  }
}
