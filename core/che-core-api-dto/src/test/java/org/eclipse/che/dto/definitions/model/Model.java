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
package org.eclipse.che.dto.definitions.model;

import java.util.List;

/**
 * Test interface serves as base model, should be extended with dto interface
 *
 * @author Eugene Voevodin
 */
public interface Model {

  List<? extends ModelComponent> getComponents();

  ModelComponent getPrimary();
}
