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
package org.eclipse.che.api.factory.server.impl;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.factory.server.FactoryCreateValidator;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;

/** Factory creation stage validator. */
@Singleton
public class FactoryCreateValidatorImpl extends FactoryBaseValidator
    implements FactoryCreateValidator {

  @Inject
  public FactoryCreateValidatorImpl() {}

  @Override
  public void validateOnCreate(FactoryDto factory)
      throws BadRequestException, ServerException, ForbiddenException, NotFoundException {
    validateProjects(factory);
    validateCurrentTimeAfterSinceUntil(factory);
    validateProjectActions(factory);
  }
}
