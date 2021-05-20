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
package org.eclipse.che.api.factory.server.impl;

import static java.lang.String.format;

import javax.inject.Singleton;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.factory.Author;
import org.eclipse.che.api.core.model.factory.Factory;
import org.eclipse.che.api.factory.server.FactoryEditValidator;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * This validator ensures that a factory can be edited by a user that has the associated rights
 * (author or account owner)
 *
 * @author Florent Benoit
 */
@Singleton
public class FactoryEditValidatorImpl implements FactoryEditValidator {

  /**
   * Validates given factory by checking the current user is granted to edit the factory
   *
   * @param factory factory object to validate
   * @throws ForbiddenException occurs if the current user is not granted to edit the factory
   * @throws ServerException when any server error occurs
   */
  @Override
  public void validate(Factory factory) throws ForbiddenException, ServerException {
    // Checks if there is an author from the factory (It may be missing for some old factories)
    final Author author = factory.getCreator();
    if (author == null || author.getUserId() == null) {
      throw new ServerException(
          format(
              "Invalid factory without author stored. Please contact the support about the factory ID '%s'",
              factory.getId()));
    }
    // ensure user has the correct permissions
    final String userId = EnvironmentContext.getCurrent().getSubject().getUserId();
    if (!author.getUserId().equals(userId)) {
      throw new ForbiddenException(
          format("You are not authorized for the factory '%s'", factory.getId()));
    }
  }
}
