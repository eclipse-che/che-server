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
package org.eclipse.che.inject.lifecycle;

import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/** @author andrew00x */
public final class DestroyModule extends LifecycleModule {
  private final Class<? extends Annotation> annotationType;
  private final DestroyErrorHandler errorHandler;

  public DestroyModule(
      Class<? extends Annotation> annotationType, DestroyErrorHandler errorHandler) {
    this.annotationType = annotationType;
    this.errorHandler = errorHandler;
  }

  @Override
  protected void configure() {
    final Destroyer destroyer = new Destroyer(errorHandler);
    bind(Destroyer.class).toInstance(destroyer);
    bindListener(
        Matchers.any(),
        new TypeListener() {
          @Override
          public <T> void hear(TypeLiteral<T> type, TypeEncounter<T> encounter) {
            encounter.register(
                new InjectionListener<T>() {
                  @Override
                  public void afterInjection(T injectee) {
                    final Method[] methods = get(injectee.getClass(), annotationType);
                    if (methods.length > 0) {
                      // copy array when pass it outside
                      final Method[] copy = new Method[methods.length];
                      System.arraycopy(methods, 0, copy, 0, methods.length);
                      destroyer.add(injectee, copy);
                    }
                  }
                });
          }
        });
  }
}
