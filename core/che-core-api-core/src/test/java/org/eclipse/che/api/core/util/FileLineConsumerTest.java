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
package org.eclipse.che.api.core.util;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.slf4j.Logger;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Mykola Morhun */
@Listeners(value = {MockitoTestNGListener.class})
public class FileLineConsumerTest {
  private static final Logger LOG = getLogger(FileLineConsumerTest.class);

  @Mock private Writer writer;

  private FileLineConsumer fileLineConsumer;

  private File file;

  @BeforeMethod
  public void beforeMethod() throws Exception {
    file = File.createTempFile("file", ".tmp");
    fileLineConsumer = new FileLineConsumer(file);
    injectWriterMock(fileLineConsumer, writer);
  }

  @AfterClass
  public void afterClass() {
    if (!file.delete()) {
      LOG.warn("Failed to remove temporary file: '{}'.", file);
    }
  }

  @Test
  public void shouldBeAbleToWriteIntoFile() throws Exception {
    // given
    final String message = "Test line";

    // when
    fileLineConsumer.writeLine(message);

    // then
    verify(writer).write(eq(message));
  }

  @Test
  public void shouldNotWriteIntoFileAfterConsumerClosing() throws Exception {
    // given
    final String message = "Test line";

    // when
    fileLineConsumer.close();
    fileLineConsumer.writeLine(message);

    // then
    verify(writer, never()).write(anyString());
  }

  /**
   * Inject Writer mock into FileLineConsumer class. This allow to test the FileLineConsumer
   * operations.
   *
   * @param fileLineConsumer instance in which mock will be injected
   * @param writerMock mock to inject
   * @throws Exception
   */
  private void injectWriterMock(FileLineConsumer fileLineConsumer, Writer writerMock)
      throws Exception {
    Field writerField = fileLineConsumer.getClass().getDeclaredField("writer");
    writerField.setAccessible(true);

    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(writerField, writerField.getModifiers() & ~Modifier.FINAL);

    writerField.set(fileLineConsumer, writerMock);
  }
}
