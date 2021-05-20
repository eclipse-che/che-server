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

import java.io.IOException;

/**
 * No-op implementation of {@link MessageConsumer}
 *
 * @author Alexander Garagatyi
 */
public class AbstractMessageConsumer<T> implements MessageConsumer<T> {
  @Override
  public void consume(T message) throws IOException {}

  @Override
  public void close() throws IOException {}
}
