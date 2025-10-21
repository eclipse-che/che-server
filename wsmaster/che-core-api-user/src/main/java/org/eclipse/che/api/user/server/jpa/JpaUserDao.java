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
package org.eclipse.che.api.user.server.jpa;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.google.inject.persist.Transactional;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.user.server.spi.UserDao;
import org.eclipse.che.security.PasswordEncryptor;

/**
 * JPA based implementation of {@link UserDao}.
 *
 * @author Yevhenii Voevodin
 * @author Anton Korneta
 * @author Igor Vinokur
 */
@Singleton
public class JpaUserDao implements UserDao {

  @Inject protected Provider<EntityManager> managerProvider;
  @Inject private PasswordEncryptor encryptor;

  @Override
  @Transactional
  public UserImpl getByAliasAndPassword(String emailOrName, String password)
      throws NotFoundException, ServerException {
    requireNonNull(emailOrName, "Required non-null email or name");
    requireNonNull(password, "Required non-null password");
    try {
      final UserImpl user =
          managerProvider
              .get()
              .createNamedQuery("User.getByAliasAndPassword", UserImpl.class)
              .setParameter("alias", emailOrName)
              .getSingleResult();
      if (!encryptor.test(password, user.getPassword())) {
        throw new NotFoundException(
            format("User with email or name '%s' and given password doesn't exist", emailOrName));
      }
      return erasePassword(user);
    } catch (NoResultException x) {
      throw new NotFoundException(
          format("User with email or name '%s' and given password doesn't exist", emailOrName));
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  public void create(UserImpl user) throws ConflictException, ServerException {
    requireNonNull(user, "Required non-null user");
    try {
      if (user.getPassword() != null) {
        user.setPassword(encryptor.encrypt(user.getPassword()));
      }
      doCreate(user);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  public void update(UserImpl update) throws NotFoundException, ServerException, ConflictException {
    requireNonNull(update, "Required non-null update");
    try {
      doUpdate(update);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  public void remove(String id) throws ServerException {
    requireNonNull(id, "Required non-null id");
    try {
      doRemove(id);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  public UserImpl getByAlias(String alias) throws NotFoundException, ServerException {
    requireNonNull(alias, "Required non-null alias");
    try {
      return erasePassword(
          managerProvider
              .get()
              .createNamedQuery("User.getByAlias", UserImpl.class)
              .setParameter("alias", alias)
              .getSingleResult());
    } catch (NoResultException x) {
      throw new NotFoundException(format("User with alias '%s' doesn't exist", alias));
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public UserImpl getById(String id) throws NotFoundException, ServerException {
    requireNonNull(id, "Required non-null id");
    try {
      final UserImpl user = managerProvider.get().find(UserImpl.class, id);
      if (user == null) {
        throw new NotFoundException(format("User with id '%s' doesn't exist", id));
      }
      return erasePassword(user);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public UserImpl getByName(String name) throws NotFoundException, ServerException {
    requireNonNull(name, "Required non-null name");
    try {
      return erasePassword(
          managerProvider
              .get()
              .createNamedQuery("User.getByName", UserImpl.class)
              .setParameter("name", name)
              .getSingleResult());
    } catch (NoResultException x) {
      throw new NotFoundException(format("User with name '%s' doesn't exist", name));
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public UserImpl getByEmail(String email) throws NotFoundException, ServerException {
    requireNonNull(email, "Required non-null email");
    try {
      return erasePassword(
          managerProvider
              .get()
              .createNamedQuery("User.getByEmail", UserImpl.class)
              .setParameter("email", email)
              .getSingleResult());
    } catch (NoResultException x) {
      throw new NotFoundException(format("User with email '%s' doesn't exist", email));
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public Page<UserImpl> getAll(int maxItems, long skipCount) throws ServerException {
    // TODO need to ensure that 'getAll' query works with same data as 'getTotalCount'
    checkArgument(maxItems >= 0, "The number of items to return can't be negative.");
    checkArgument(
        skipCount >= 0 && skipCount <= Integer.MAX_VALUE,
        "The number of items to skip can't be negative or greater than " + Integer.MAX_VALUE);
    try {
      final List<UserImpl> list =
          managerProvider
              .get()
              .createNamedQuery("User.getAll", UserImpl.class)
              .setMaxResults(maxItems)
              .setFirstResult((int) skipCount)
              .getResultList()
              .stream()
              .map(JpaUserDao::erasePassword)
              .collect(toList());
      return new Page<>(list, skipCount, maxItems, getTotalCount());
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public Page<UserImpl> getByNamePart(String namePart, int maxItems, long skipCount)
      throws ServerException {
    requireNonNull(namePart, "Required non-null name part");
    checkArgument(maxItems >= 0, "The number of items to return can't be negative");
    checkArgument(
        skipCount >= 0 && skipCount <= Integer.MAX_VALUE,
        "The number of items to skip can't be negative or greater than " + Integer.MAX_VALUE);
    try {
      final List<UserImpl> list =
          managerProvider
              .get()
              .createNamedQuery("User.getByNamePart", UserImpl.class)
              .setParameter("name", namePart.toLowerCase())
              .setMaxResults(maxItems)
              .setFirstResult((int) skipCount)
              .getResultList()
              .stream()
              .map(JpaUserDao::erasePassword)
              .collect(toList());
      final long count =
          managerProvider
              .get()
              .createNamedQuery("User.getByNamePartCount", Long.class)
              .setParameter("name", namePart.toLowerCase())
              .getSingleResult();
      return new Page<>(list, skipCount, maxItems, count);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public Page<UserImpl> getByEmailPart(String emailPart, int maxItems, long skipCount)
      throws ServerException {
    requireNonNull(emailPart, "Required non-null email part");
    checkArgument(maxItems >= 0, "The number of items to return can't be negative");
    checkArgument(
        skipCount >= 0 && skipCount <= Integer.MAX_VALUE,
        "The number of items to skip can't be negative or greater than " + Integer.MAX_VALUE);
    try {
      final List<UserImpl> list =
          managerProvider
              .get()
              .createNamedQuery("User.getByEmailPart", UserImpl.class)
              .setParameter("email", emailPart.toLowerCase())
              .setMaxResults(maxItems)
              .setFirstResult((int) skipCount)
              .getResultList()
              .stream()
              .map(JpaUserDao::erasePassword)
              .collect(toList());
      final long count =
          managerProvider
              .get()
              .createNamedQuery("User.getByEmailPartCount", Long.class)
              .setParameter("email", emailPart.toLowerCase())
              .getSingleResult();
      return new Page<>(list, skipCount, maxItems, count);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public long getTotalCount() throws ServerException {
    try {
      return managerProvider
          .get()
          .createNamedQuery("User.getTotalCount", Long.class)
          .getSingleResult();
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Transactional(rollbackOn = {RuntimeException.class, ApiException.class})
  protected void doCreate(UserImpl user) throws ConflictException, ServerException {
    EntityManager manage = managerProvider.get();
    manage.persist(user);
    manage.flush();
  }

  @Transactional
  protected void doUpdate(UserImpl update) throws NotFoundException {
    final EntityManager manager = managerProvider.get();
    final UserImpl user = manager.find(UserImpl.class, update.getId());
    if (user == null) {
      throw new NotFoundException(
          format("Couldn't update user with id '%s' because it doesn't exist", update.getId()));
    }
    final String password = update.getPassword();
    if (password != null) {
      update.setPassword(encryptor.encrypt(password));
    } else {
      update.setPassword(user.getPassword());
    }
    manager.merge(update);
    manager.flush();
  }

  @Transactional(rollbackOn = {RuntimeException.class, ServerException.class})
  protected void doRemove(String id) {
    final EntityManager manager = managerProvider.get();
    final UserImpl user = manager.find(UserImpl.class, id);
    if (user != null) {
      manager.remove(user);
      manager.flush();
    }
  }

  // Returns user instance copy without password
  private static UserImpl erasePassword(UserImpl source) {
    return new UserImpl(
        source.getId(), source.getEmail(), source.getName(), null, source.getAliases());
  }
}
