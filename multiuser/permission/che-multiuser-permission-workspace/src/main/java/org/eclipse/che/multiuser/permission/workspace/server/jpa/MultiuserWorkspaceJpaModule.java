/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.permission.workspace.server.jpa;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.eclipse.che.multiuser.api.permission.server.AbstractPermissionsDomain;
import org.eclipse.che.multiuser.permission.workspace.server.WorkspaceDomain;
import org.eclipse.che.multiuser.permission.workspace.server.model.impl.WorkerImpl;
import org.eclipse.che.multiuser.permission.workspace.server.spi.jpa.MultiuserJpaWorkspaceDao;

/** @author Yevhenii Voevodin */
public class MultiuserWorkspaceJpaModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(WorkspaceDao.class).to(MultiuserJpaWorkspaceDao.class);

    bind(new TypeLiteral<AbstractPermissionsDomain<WorkerImpl>>() {}).to(WorkspaceDomain.class);
  }
}
