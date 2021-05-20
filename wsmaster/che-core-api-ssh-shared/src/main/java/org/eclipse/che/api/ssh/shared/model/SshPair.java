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
package org.eclipse.che.api.ssh.shared.model;

import org.eclipse.che.commons.annotation.Nullable;

/**
 * Defines ssh pair
 *
 * @author Sergii Leschenko
 */
public interface SshPair {
  /** Returns name service that use current ssh pair. It is mandatory. */
  String getService();

  /** Returns name of ssh pair. It is mandatory. */
  String getName();

  /** Returns content of public key. It is optional */
  @Nullable
  String getPublicKey();

  /** Returns content of private key. It is optional */
  @Nullable
  String getPrivateKey();
}
