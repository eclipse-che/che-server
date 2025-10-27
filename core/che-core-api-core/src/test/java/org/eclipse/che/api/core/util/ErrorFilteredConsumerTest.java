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
package org.eclipse.che.api.core.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.testng.annotations.Test;

/**
 * @author Anatolii Bazko
 */
public class ErrorFilteredConsumerTest {

  @Test
  public void testRedirect() throws Exception {
    LineConsumer lineConsumer = mock(LineConsumer.class);

    ErrorFilteredConsumer errorFilteredConsumer = new ErrorFilteredConsumer(lineConsumer);

    errorFilteredConsumer.writeLine("Line 1");
    errorFilteredConsumer.writeLine("Line 2");
    errorFilteredConsumer.writeLine("[STDERR] Line 3");
    errorFilteredConsumer.writeLine("[STDERR] Line 4");
    errorFilteredConsumer.writeLine("Line 5");

    verify(lineConsumer).writeLine("[STDERR] Line 3");
    verify(lineConsumer).writeLine("[STDERR] Line 4");
  }
}
