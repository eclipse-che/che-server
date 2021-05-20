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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import org.eclipse.che.api.core.util.lineconsumer.ConsumerAlreadyClosedException;

/**
 * Consumes logs and writes them into file. <br>
 * This class is not thread safe. Also see multithreaded implementation {@link
 * org.eclipse.che.api.core.util.lineconsumer.ConcurrentFileLineConsumer}
 *
 * @author andrew00x
 * @author Mykola Morhun
 */
public class FileLineConsumer implements LineConsumer {
  private final File file;
  private final Writer writer;

  private boolean isOpen;

  public FileLineConsumer(File file) throws IOException {
    this.file = file;
    writer = Files.newBufferedWriter(file.toPath(), Charset.defaultCharset());
    isOpen = true;
  }

  public File getFile() {
    return file;
  }

  @Override
  public void writeLine(String line) throws IOException {
    if (isOpen) {
      try {
        if (line != null) {
          writer.write(line);
        }
        writer.write('\n');
        writer.flush();
      } catch (IOException e) {
        if ("Stream closed".equals(e.getMessage())) {
          throw new ConsumerAlreadyClosedException(e.getMessage());
        }
        throw e;
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (isOpen) {
      isOpen = false;
      writer.close();
    }
  }
}
