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
package org.eclipse.che.api.core.model.workspace.runtime;

/**
 * Status of Machine
 *
 * @author gazarenkov
 */
public enum MachineStatus {

  /** Machine is starting */
  STARTING,
  /** Machine is up and running */
  RUNNING,

  /** Machine is not running */
  STOPPED,

  /** Machine failed */
  FAILED
}
