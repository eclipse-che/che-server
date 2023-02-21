/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.core.db.h2.jpa.eclipselink;

import static com.google.common.base.MoreObjects.firstNonNull;

import org.eclipse.che.core.db.JNDIDataSourceFactory;

public class H2SQLJndiDataSourceFactory extends JNDIDataSourceFactory {

  private static final String DEFAULT_USERNAME = "username";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String DEFAULT_URL = "jdbc:h2:file:/data/h2";
  private static final String DEFAULT_DRIVER__CLASS__NAME = "org.h2.Driver";
  private static final String DEFAULT_MAX__TOTAL = "20";
  private static final String DEFAULT_MAX__IDLE = "2";
  private static final String DEFAULT_MAX__WAIT__MILLIS = "-1";

  public H2SQLJndiDataSourceFactory() throws Exception {
    super(
        firstNonNull(
            nullStringToNullReference(System.getenv("CHE_JDBC_USERNAME")), DEFAULT_USERNAME),
        firstNonNull(
            nullStringToNullReference(System.getenv("CHE_JDBC_PASSWORD")), DEFAULT_PASSWORD),
        firstNonNull(nullStringToNullReference(System.getenv("CHE_JDBC_H2_URL")), DEFAULT_URL),
        firstNonNull(
            nullStringToNullReference(System.getenv("CHE_JDBC_DRIVER__CLASS__NAME")),
            DEFAULT_DRIVER__CLASS__NAME),
        firstNonNull(
            nullStringToNullReference(System.getenv("CHE_JDBC_MAX__TOTAL")), DEFAULT_MAX__TOTAL),
        firstNonNull(
            nullStringToNullReference(System.getenv("CHE_JDBC_MAX__IDLE")), DEFAULT_MAX__IDLE),
        firstNonNull(
            nullStringToNullReference(System.getenv("CHE_JDBC_MAX__WAIT__MILLIS")),
            DEFAULT_MAX__WAIT__MILLIS));
    ;
  }
}
