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
package org.eclipse.che.api.core.model.factory;

import java.util.Map;

/**
 * Defines the contract for the factory action instance.
 *
 * @author Anton Korneta
 */
public interface Action {

  /** Returns the IDE specific identifier of action e.g. ('openFile', 'editFile') */
  String getId();

  /** Returns properties of this action instance */
  Map<String, String> getProperties();
}
