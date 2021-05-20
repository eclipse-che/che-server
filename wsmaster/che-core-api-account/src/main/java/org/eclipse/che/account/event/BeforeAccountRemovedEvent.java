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
package org.eclipse.che.account.event;

import org.eclipse.che.account.spi.AccountImpl;
import org.eclipse.che.core.db.cascade.event.RemoveEvent;

/**
 * Published before {@link AccountImpl account} removed.
 *
 * @author Antona Korneta
 */
public class BeforeAccountRemovedEvent extends RemoveEvent {

  private final AccountImpl account;

  public BeforeAccountRemovedEvent(AccountImpl account) {
    this.account = account;
  }

  /** Returns account which is going to be removed. */
  public AccountImpl getAccount() {
    return account;
  }
}
