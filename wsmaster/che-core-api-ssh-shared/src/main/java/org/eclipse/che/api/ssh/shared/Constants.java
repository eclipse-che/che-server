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
package org.eclipse.che.api.ssh.shared;

/**
 * Constants for ssh API
 *
 * @author Sergii Leschenko
 */
public final class Constants {
  public static final String LINK_REL_GENERATE_PAIR = "create pair";
  public static final String LINK_REL_CREATE_PAIR = "create pair";
  public static final String LINK_REL_GET_PAIRS = "get pairs";
  public static final String LINK_REL_GET_PAIR = "get pair";
  public static final String LINK_REL_REMOVE_PAIR = "remove pair";

  private Constants() {}
}
