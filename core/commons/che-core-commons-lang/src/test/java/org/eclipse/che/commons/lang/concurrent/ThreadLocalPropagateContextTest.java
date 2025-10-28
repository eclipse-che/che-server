/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.commons.lang.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * @author andrew00x
 */
public class ThreadLocalPropagateContextTest {
  private static ThreadLocal<String> tl1 = new ThreadLocal<>();

  private ExecutorService exec;
  private final String tlValue = "my value";

  @BeforeTest
  public void setUp() {
    tl1.set(tlValue);
    ThreadLocalPropagateContext.addThreadLocal(tl1);
    Assert.assertEquals(ThreadLocalPropagateContext.getThreadLocals().length, 1);
    exec = Executors.newSingleThreadExecutor();
  }

  @AfterTest
  public void tearDown() {
    if (exec != null) {
      exec.shutdownNow();
    }
    ThreadLocalPropagateContext.removeThreadLocal(tl1);
    Assert.assertEquals(ThreadLocalPropagateContext.getThreadLocals().length, 0);
    tl1.remove();
  }

  @Test
  public void testRunnableWithoutThreadLocalPropagateContext() throws Exception {
    final String[] holder = new String[1];
    exec.submit(
            new Runnable() {
              @Override
              public void run() {
                holder[0] = tl1.get();
              }
            })
        .get();
    Assert.assertNull(holder[0]);
  }

  @Test
  public void testRunnableWithThreadLocalPropagateContext() throws Exception {
    final String[] holder = new String[1];
    exec.submit(
            ThreadLocalPropagateContext.wrap(
                new Runnable() {
                  @Override
                  public void run() {
                    holder[0] = tl1.get();
                  }
                }))
        .get();
    Assert.assertEquals(holder[0], tlValue);
  }

  @Test
  public void testCallableWithoutThreadLocalPropagateContext() throws Exception {
    final String v =
        exec.submit(
                new Callable<String>() {
                  @Override
                  public String call() {
                    return tl1.get();
                  }
                })
            .get();
    Assert.assertNull(v);
  }

  @Test
  public void testCallableWithThreadLocalPropagateContext() throws Exception {
    final String v =
        exec.submit(
                ThreadLocalPropagateContext.wrap(
                    new Callable<String>() {
                      @Override
                      public String call() {
                        return tl1.get();
                      }
                    }))
            .get();
    Assert.assertEquals(v, tlValue);
  }
}
