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
package org.eclipse.che.commons.test.tck.repository;

import static java.lang.String.format;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;
import java.util.Collection;
import javax.inject.Provider;
import javax.persistence.Entity;
import javax.persistence.EntityManager;

/**
 * Simplifies implementation for Jpa repository in general case.
 *
 * <p>Expected usage:
 *
 * <pre>
 *      class MyTckModule extends TckModule {
 *          &#064;Override configure() {
 *              bind(new TypeLiteral&lt;TckRepository&lt;UserImpl&gt;&gt;() {})
 *                  .toInstance(new JpaTckRepository(Concrete.class));
 *          }
 *      }
 * </pre>
 *
 * @param <T> type of the entity
 * @author Yevhenii Voevodin
 */
public class JpaTckRepository<T> implements TckRepository<T> {

  @Inject protected Provider<EntityManager> managerProvider;

  @Inject protected UnitOfWork uow;

  private final Class<? extends T> entityClass;

  public JpaTckRepository(Class<? extends T> entityClass) {
    this.entityClass = entityClass;
  }

  @Override
  public void createAll(Collection<? extends T> entities) throws TckRepositoryException {
    uow.begin();
    final EntityManager manager = managerProvider.get();
    try {
      manager.getTransaction().begin();
      for (T entity : entities) {
        manager.persist(entity);
        manager.flush();
      }

      manager.getTransaction().commit();
    } catch (RuntimeException x) {
      if (manager.getTransaction().isActive()) {
        manager.getTransaction().rollback();
      }
      throw new TckRepositoryException(x.getLocalizedMessage(), x);
    } finally {
      uow.end();
    }
  }

  @Override
  public void removeAll() throws TckRepositoryException {
    uow.begin();
    final EntityManager manager = managerProvider.get();
    try {
      manager.getTransaction().begin();
      // The query 'DELETE FROM Entity' won't be correct as it will ignore orphanRemoval
      // and may also ignore some configuration options, while EntityManager#remove won't
      manager
          .createQuery(format("SELECT e FROM %s e", getEntityName(entityClass)), entityClass)
          .getResultList()
          .forEach(manager::remove);
      manager.getTransaction().commit();
    } catch (RuntimeException x) {
      if (manager.getTransaction().isActive()) {
        manager.getTransaction().rollback();
      }
      throw new TckRepositoryException(x.getLocalizedMessage(), x);
    } finally {
      uow.end();
    }
  }

  private String getEntityName(Class<?> clazz) {
    if (!clazz.isAnnotationPresent(Entity.class)) {
      return clazz.getSimpleName();
    }
    final Entity entity = clazz.getAnnotation(Entity.class);
    if (entity.name().isEmpty()) {
      return clazz.getSimpleName();
    }
    return entity.name();
  }
}
