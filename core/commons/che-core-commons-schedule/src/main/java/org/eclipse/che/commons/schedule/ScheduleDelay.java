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
package org.eclipse.che.commons.schedule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Mark method for execution periodic action that becomes enabled first after the given initial
 * delay, and subsequently with the given delay between the termination of one execution and the
 * commencement of the next.
 *
 * <p>Analogue of {@link java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate(Runnable,
 * long, long, java.util.concurrent.TimeUnit)} }
 *
 * @author Sergii Kabashniuk
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScheduleDelay {

  /** @return - the time to delay first execution */
  long initialDelay() default 0;

  /** @return the delay between the termination of one execution and the commencement of the next */
  long delay() default 0;

  /** @return the time unit of the initialDelay and delay parameters */
  TimeUnit unit() default TimeUnit.SECONDS;

  /** @return - name of configuration parameter for initialDelay */
  String initialDelayParameterName() default "";

  /**
   * @return - name of configuration parameter for delay. A non positive delay value will disable
   *     the scheduling of the method.
   */
  String delayParameterName() default "";
}
