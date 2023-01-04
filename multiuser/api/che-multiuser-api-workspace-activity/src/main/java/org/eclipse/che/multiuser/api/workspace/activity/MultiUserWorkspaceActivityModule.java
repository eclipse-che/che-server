/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.api.workspace.activity;

import com.google.inject.AbstractModule;
import org.eclipse.che.api.workspace.activity.JpaWorkspaceActivityDao;
import org.eclipse.che.api.workspace.activity.WorkspaceActivityDao;

/**
 * Implementation of
 *
 * @author Mykhailo Kuznietsov
 */
public class MultiUserWorkspaceActivityModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(WorkspaceActivityDao.class).to(JpaWorkspaceActivityDao.class);
  }
}
