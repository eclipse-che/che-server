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
package org.eclipse.che.multiuser.permission.workspace.server.spi.jpa;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.google.inject.persist.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.workspace.server.model.impl.ProjectConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.eclipse.che.api.workspace.shared.event.WorkspaceRemovedEvent;

/**
 * JPA based implementation of {@link WorkspaceDao}.
 *
 * @author Yevhenii Voevodin
 */
@Singleton
public class MultiuserJpaWorkspaceDao implements WorkspaceDao {

  @Inject private EventService eventService;
  @Inject private Provider<EntityManager> managerProvider;

  private static final String findByWorkerQuery =
      "SELECT ws FROM Worker worker  "
          + "          LEFT JOIN worker.workspace ws "
          + "          WHERE worker.userId = :userId "
          + "          AND 'read' MEMBER OF worker.actions";
  private static final String findByWorkerCountQuery =
      "SELECT COUNT(ws) FROM Worker worker  "
          + "          LEFT JOIN worker.workspace ws "
          + "          WHERE worker.userId = :userId "
          + "          AND 'read' MEMBER OF worker.actions";

  @Override
  public WorkspaceImpl create(WorkspaceImpl workspace) throws ConflictException, ServerException {
    requireNonNull(workspace, "Required non-null workspace");
    try {
      doCreate(workspace);
    } catch (RuntimeException x) {
      throw new ServerException(x.getMessage(), x);
    }
    return new WorkspaceImpl(workspace);
  }

  @Override
  public WorkspaceImpl update(WorkspaceImpl update)
      throws NotFoundException, ConflictException, ServerException {
    requireNonNull(update, "Required non-null update");
    try {
      return new WorkspaceImpl(doUpdate(update));
    } catch (RuntimeException x) {
      throw new ServerException(x.getMessage(), x);
    }
  }

  @Override
  public Optional<WorkspaceImpl> remove(String id) throws ServerException {
    requireNonNull(id, "Required non-null id");
    Optional<WorkspaceImpl> workspaceOpt;
    try {
      workspaceOpt = doRemove(id);
      workspaceOpt.ifPresent(
          workspace -> eventService.publish(new WorkspaceRemovedEvent(workspace)));
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
    return workspaceOpt;
  }

  @Override
  @Transactional
  public WorkspaceImpl get(String id) throws NotFoundException, ServerException {
    requireNonNull(id, "Required non-null id");
    try {
      final WorkspaceImpl workspace = managerProvider.get().find(WorkspaceImpl.class, id);
      if (workspace == null) {
        throw new NotFoundException(format("Workspace with id '%s' doesn't exist", id));
      }
      return new WorkspaceImpl(workspace);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public WorkspaceImpl get(String name, String namespace)
      throws NotFoundException, ServerException {
    requireNonNull(name, "Required non-null name");
    requireNonNull(namespace, "Required non-null namespace");
    try {
      return new WorkspaceImpl(
          managerProvider
              .get()
              .createNamedQuery("Workspace.getByName", WorkspaceImpl.class)
              .setParameter("namespace", namespace)
              .setParameter("name", name)
              .getSingleResult());
    } catch (NoResultException noResEx) {
      throw new NotFoundException(
          format("Workspace with name '%s' in namespace '%s' doesn't exist", name, namespace));
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public Page<WorkspaceImpl> getByNamespace(String namespace, int maxItems, long skipCount)
      throws ServerException {
    requireNonNull(namespace, "Required non-null namespace");
    try {
      final EntityManager manager = managerProvider.get();
      final List<WorkspaceImpl> list =
          manager
              .createNamedQuery("Workspace.getByNamespace", WorkspaceImpl.class)
              .setParameter("namespace", namespace)
              .setMaxResults(maxItems)
              .setFirstResult((int) skipCount)
              .getResultList()
              .stream()
              .map(WorkspaceImpl::new)
              .collect(Collectors.toList());
      final long count =
          manager
              .createNamedQuery("Workspace.getByNamespaceCount", Long.class)
              .setParameter("namespace", namespace)
              .getSingleResult();
      return new Page<>(list, skipCount, maxItems, count);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public Page<WorkspaceImpl> getWorkspaces(String userId, int maxItems, long skipCount)
      throws ServerException {
    try {
      final List<WorkspaceImpl> list =
          managerProvider
              .get()
              .createQuery(findByWorkerQuery, WorkspaceImpl.class)
              .setParameter("userId", userId)
              .setMaxResults(maxItems)
              .setFirstResult((int) skipCount)
              .getResultList();

      final long count =
          managerProvider
              .get()
              .createQuery(findByWorkerCountQuery, Long.class)
              .setParameter("userId", userId)
              .getSingleResult();

      return new Page<>(list, skipCount, maxItems, count);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public Page<WorkspaceImpl> getWorkspaces(boolean isTemporary, int maxItems, long skipCount)
      throws ServerException {
    checkArgument(maxItems >= 0, "The number of items to return can't be negative.");
    checkArgument(
        skipCount >= 0,
        "The number of items to skip can't be negative or greater than " + Integer.MAX_VALUE);
    try {
      final List<WorkspaceImpl> list =
          managerProvider
              .get()
              .createNamedQuery("Workspace.getByTemporary", WorkspaceImpl.class)
              .setParameter("temporary", isTemporary)
              .setMaxResults(maxItems)
              .setFirstResult((int) skipCount)
              .getResultList()
              .stream()
              .map(WorkspaceImpl::new)
              .collect(toList());
      final long count =
          managerProvider
              .get()
              .createNamedQuery("Workspace.getByTemporaryCount", Long.class)
              .setParameter("temporary", isTemporary)
              .getSingleResult();
      return new Page<>(list, skipCount, maxItems, count);
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Override
  @Transactional
  public long getWorkspacesTotalCount() throws ServerException {
    try {
      return managerProvider
          .get()
          .createNamedQuery("Workspace.getWorkspacesTotalCount", Long.class)
          .getSingleResult();
    } catch (RuntimeException x) {
      throw new ServerException(x.getLocalizedMessage(), x);
    }
  }

  @Transactional
  protected void doCreate(WorkspaceImpl workspace) {
    if (workspace.getConfig() != null) {
      workspace.getConfig().getProjects().forEach(ProjectConfigImpl::prePersistAttributes);
    }
    EntityManager manager = managerProvider.get();
    manager.persist(workspace);
    manager.flush();
  }

  @Transactional(rollbackOn = {RuntimeException.class, ServerException.class})
  protected Optional<WorkspaceImpl> doRemove(String id) throws ServerException {
    final WorkspaceImpl workspace = managerProvider.get().find(WorkspaceImpl.class, id);
    if (workspace == null) {
      return Optional.empty();
    }
    final EntityManager manager = managerProvider.get();
    manager.remove(workspace);
    manager.flush();
    return Optional.of(workspace);
  }

  @Transactional
  protected WorkspaceImpl doUpdate(WorkspaceImpl update) throws NotFoundException {
    EntityManager manager = managerProvider.get();
    if (manager.find(WorkspaceImpl.class, update.getId()) == null) {
      throw new NotFoundException(format("Workspace with id '%s' doesn't exist", update.getId()));
    }
    if (update.getConfig() != null) {
      update.getConfig().getProjects().forEach(ProjectConfigImpl::prePersistAttributes);
    }
    WorkspaceImpl merged = manager.merge(update);
    manager.flush();
    return merged;
  }
}
