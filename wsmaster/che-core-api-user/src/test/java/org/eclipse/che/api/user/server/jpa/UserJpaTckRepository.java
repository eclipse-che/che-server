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
package org.eclipse.che.api.user.server.jpa;

import com.google.inject.persist.Transactional;
import java.util.Collection;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.commons.test.tck.repository.TckRepository;
import org.eclipse.che.commons.test.tck.repository.TckRepositoryException;
import org.eclipse.che.security.PasswordEncryptor;

@Transactional
public class UserJpaTckRepository implements TckRepository<UserImpl> {

  @Inject private Provider<EntityManager> managerProvider;

  @Inject private PasswordEncryptor encryptor;

  @Override
  public void createAll(Collection<? extends UserImpl> entities) throws TckRepositoryException {
    final EntityManager manager = managerProvider.get();
    entities.stream()
        .map(
            user ->
                new UserImpl(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    encryptor.encrypt(user.getPassword()),
                    user.getAliases()))
        .forEach(manager::persist);
  }

  @Override
  public void removeAll() throws TckRepositoryException {
    managerProvider
        .get()
        .createQuery("SELECT u FROM Usr u", UserImpl.class)
        .getResultList()
        .forEach(managerProvider.get()::remove);
  }
}
