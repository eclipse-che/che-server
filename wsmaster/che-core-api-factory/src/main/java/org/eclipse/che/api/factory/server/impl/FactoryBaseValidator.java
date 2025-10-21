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
package org.eclipse.che.api.factory.server.impl;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.factory.server.FactoryConstants;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.factory.shared.dto.IdeActionDto;
import org.eclipse.che.api.factory.shared.dto.IdeDto;
import org.eclipse.che.api.factory.shared.dto.OnAppLoadedDto;
import org.eclipse.che.api.factory.shared.dto.OnProjectsLoadedDto;
import org.eclipse.che.api.factory.shared.dto.PoliciesDto;

/**
 * Validates values of factory parameters.
 *
 * @author Alexander Garagatyi
 * @author Valeriy Svydenko
 */
public abstract class FactoryBaseValidator {
  private static final Pattern PROJECT_NAME_VALIDATOR =
      Pattern.compile("^[\\\\\\w\\\\\\d]+[\\\\\\w\\\\\\d_.-]*$");

  /**
   * Validates that factory can be used at present time (used on accept)
   *
   * @param factory factory to validate
   * @throws BadRequestException if since date greater than current date<br>
   * @throws BadRequestException if until date less than current date<br>
   */
  protected void validateCurrentTimeBetweenSinceUntil(FactoryMetaDto factory)
      throws BadRequestException {
    final PoliciesDto policies = factory.getPolicies();
    if (policies == null) {
      return;
    }

    final Long since = policies.getSince() == null ? 0L : policies.getSince();
    final Long until = policies.getUntil() == null ? 0L : policies.getUntil();

    if (since != 0 && currentTimeMillis() < since) {
      throw new BadRequestException(FactoryConstants.ILLEGAL_FACTORY_BY_SINCE_MESSAGE);
    }

    if (until != 0 && currentTimeMillis() > until) {
      throw new BadRequestException(FactoryConstants.ILLEGAL_FACTORY_BY_UNTIL_MESSAGE);
    }
  }

  /**
   * Validates IDE actions
   *
   * @param factory factory to validate
   * @throws BadRequestException when factory actions is invalid
   */
  protected void validateProjectActions(FactoryMetaDto factory) throws BadRequestException {
    final IdeDto ide = factory.getIde();
    if (ide == null) {
      return;
    }

    final List<IdeActionDto> applicationActions = new ArrayList<>();
    if (ide.getOnAppClosed() != null) {
      applicationActions.addAll(ide.getOnAppClosed().getActions());
    }
    if (ide.getOnAppLoaded() != null) {
      applicationActions.addAll(ide.getOnAppLoaded().getActions());
    }

    for (IdeActionDto applicationAction : applicationActions) {
      String id = applicationAction.getId();
      if ("openFile".equals(id)
          || "findReplace".equals(id)
          || "runCommand".equals(id)
          || "newTerminal".equals(id)) {
        throw new BadRequestException(format(FactoryConstants.INVALID_ACTION_SECTION, id));
      }
    }

    final OnAppLoadedDto onAppLoaded = ide.getOnAppLoaded();
    if (onAppLoaded != null) {
      for (IdeActionDto action : onAppLoaded.getActions()) {
        final Map<String, String> properties = action.getProperties();
        if ("openWelcomePage".equals(action.getId())
            && isNullOrEmpty(properties.get("greetingContentUrl"))) {
          throw new BadRequestException(FactoryConstants.INVALID_WELCOME_PAGE_ACTION);
        }
      }
    }

    final OnProjectsLoadedDto onLoaded = ide.getOnProjectsLoaded();
    if (onLoaded != null) {
      final List<IdeActionDto> onProjectOpenedActions = onLoaded.getActions();
      for (IdeActionDto applicationAction : onProjectOpenedActions) {
        final String id = applicationAction.getId();
        final Map<String, String> properties = applicationAction.getProperties();

        switch (id) {
          case "openFile":
            if (isNullOrEmpty(properties.get("file"))) {
              throw new BadRequestException(FactoryConstants.INVALID_OPENFILE_ACTION);
            }
            break;

          case "runCommand":
            if (isNullOrEmpty(properties.get("name"))) {
              throw new BadRequestException(FactoryConstants.INVALID_RUNCOMMAND_ACTION);
            }
            break;

          case "findReplace":
            if (isNullOrEmpty(properties.get("in"))
                || isNullOrEmpty(properties.get("find"))
                || isNullOrEmpty(properties.get("replace"))) {
              throw new BadRequestException(FactoryConstants.INVALID_FIND_REPLACE_ACTION);
            }
            break;
        }
      }
    }
  }
}
