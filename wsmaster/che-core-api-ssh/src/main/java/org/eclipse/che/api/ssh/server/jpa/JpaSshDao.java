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
package org.eclipse.che.api.ssh.server.jpa;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.inject.persist.Transactional;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.ssh.server.model.impl.SshPairImpl;
import org.eclipse.che.api.ssh.server.spi.SshDao;

/**
 * JPA based implementation of {@link SshDao}.
 *
 * @author Mihail Kuznyetsov
 * @author Yevhenii Voevodin
 */
@Singleton
public class JpaSshDao implements SshDao {

  @Inject private Provider<EntityManager> managerProvider;

  @Override
  public void create(SshPairImpl sshPair) throws ServerException, ConflictException {
    requireNonNull(sshPair);
    try {
      doCreate(sshPair);
    } catch (RuntimeException e) {
      throw new ServerException(e);
    }
  }

  @Override
  @Transactional
  public List<SshPairImpl> get(String owner, String service) throws ServerException {
    requireNonNull(owner);
    requireNonNull(service);
    try {
      return managerProvider
          .get()
          .createNamedQuery("SshKeyPair.getByOwnerAndService", SshPairImpl.class)
          .setParameter("owner", owner)
          .setParameter("service", service)
          .getResultList();
    } catch (RuntimeException e) {
      throw new ServerException(e.getLocalizedMessage(), e);
    }
  }

  @Override
  @Transactional
  public SshPairImpl get(String owner, String service, String name)
      throws ServerException, NotFoundException {
    requireNonNull(owner);
    requireNonNull(service);
    requireNonNull(name);
    try {
      SshPairImpl result =
          managerProvider
              .get()
              .find(SshPairImpl.class, new SshPairPrimaryKey(owner, service, name));
      if (result == null) {
        throw new NotFoundException(
            format("Ssh pair with service '%s' and name '%s' was not found.", service, name));
      }
      return result;
    } catch (RuntimeException e) {
      throw new ServerException(e.getLocalizedMessage(), e);
    }
  }

  @Override
  public void remove(String owner, String service, String name)
      throws ServerException, NotFoundException {
    requireNonNull(owner);
    requireNonNull(service);
    requireNonNull(name);
    try {
      doRemove(owner, service, name);
    } catch (RuntimeException e) {
      throw new ServerException(e);
    }
  }

  @Override
  @Transactional
  public List<SshPairImpl> get(String owner) throws ServerException {
    requireNonNull(owner, "Required non-null owner");
    try {
      return managerProvider
          .get()
          .createNamedQuery("SshKeyPair.getByOwner", SshPairImpl.class)
          .setParameter("owner", owner)
          .getResultList();
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Transactional
  protected void doCreate(SshPairImpl entity) {
    EntityManager manager = managerProvider.get();
    manager.persist(entity);
    manager.flush();
  }

  @Transactional
  protected void doRemove(String owner, String service, String name) throws NotFoundException {
    EntityManager manager = managerProvider.get();
    SshPairImpl entity =
        manager.find(SshPairImpl.class, new SshPairPrimaryKey(owner, service, name));
    if (entity == null) {
      throw new NotFoundException(
          format("Ssh pair with service '%s' and name '%s' was not found.", service, name));
    }
    manager.remove(entity);
    manager.flush();
  }
}
