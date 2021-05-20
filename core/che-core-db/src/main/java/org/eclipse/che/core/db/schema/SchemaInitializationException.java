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
package org.eclipse.che.core.db.schema;

/**
 * Thrown when any schema initialization/migration problem occurs.
 *
 * @author Yevhenii Voevodin
 */
public class SchemaInitializationException extends Exception {

  public SchemaInitializationException(String message, Throwable cause) {
    super(message, cause);
  }

  public SchemaInitializationException(String message) {
    super(message);
  }
}
