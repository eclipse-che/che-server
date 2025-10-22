/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.account.api;

import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.account.shared.model.Account;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.notification.EventService;

/**
 * Facade for Account related operations.
 *
 * @author Sergii Leschenko
 */
@Deprecated
@Singleton
public class AccountManager {
  private final EventService eventService;

  @Inject
  public AccountManager(EventService eventService) {
    this.eventService = eventService;
  }

  /**
   * Account information is no longer persisted in the database
   *
   * @param account account to create
   * @throws NullPointerException when {@code account} is null
   * @throws ConflictException when account with such name or id already exists
   * @throws ServerException when any other error occurs during account creating
   */
  public void create(Account account) throws ConflictException, ServerException {
    throw new ServerException("Account creation is obsolete and not expected to be invoked");
  }

  /**
   * Updates account by replacing an existing account entity with a new one.
   *
   * @param account account to update
   * @throws NullPointerException when {@code account} is null
   * @throws NotFoundException when account with id {@code account.getId()} is not found
   * @throws ConflictException when account's new name is not unique
   * @throws ServerException when any other error occurs
   */
  public void update(Account account) throws NotFoundException, ConflictException, ServerException {
    requireNonNull(account, "Required non-null account");
  }

  /**
   * Gets account by identifier.
   *
   * @param id id of account to fetch
   * @return account instance with given id
   * @throws NullPointerException when {@code id} is null
   * @throws NotFoundException when account with given {@code id} was not found
   * @throws ServerException when any other error occurs during account fetching
   */
  public Account getById(String id) throws NotFoundException, ServerException {
    requireNonNull(id, "Required non-null account id");
    return null;
  }

  /**
   * Gets account by name.
   *
   * @param name name of account to fetch
   * @return account instance with given name
   * @throws NullPointerException when {@code name} is null
   * @throws NotFoundException when account with given {@code name} was not found
   * @throws ServerException when any other error occurs during account fetching
   */
  public Account getByName(String name) throws NotFoundException, ServerException {
    requireNonNull(name, "Required non-null account name");
    return null;
  }

  /**
   * Removes account by specified {@code id}
   *
   * @param id account identifier
   * @throws NullPointerException when {@code id} is null
   * @throws ServerException when any other error occurs
   */
  public void remove(String id) throws ServerException {
    requireNonNull(id, "Required non-null account id");
  }
}
