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
package org.eclipse.che.api.workspace.shared.dto;

import static org.eclipse.che.api.core.factory.FactoryParameter.Obligation.OPTIONAL;

import java.util.Map;
import org.eclipse.che.api.core.factory.FactoryParameter;
import org.eclipse.che.api.core.model.workspace.config.SourceStorage;
import org.eclipse.che.dto.shared.DTO;

/**
 * TODO Type and location are optional in case it is a subproject, that has an empty source.
 *
 * @author Alexander Garagatyi
 */
@DTO
public interface SourceStorageDto extends SourceStorage {
  @Override
  @FactoryParameter(obligation = OPTIONAL)
  String getType();

  void setType(String type);

  SourceStorageDto withType(String type);

  @Override
  @FactoryParameter(obligation = OPTIONAL)
  String getLocation();

  void setLocation(String location);

  SourceStorageDto withLocation(String location);

  @Override
  @FactoryParameter(obligation = OPTIONAL)
  Map<String, String> getParameters();

  void setParameters(Map<String, String> parameters);

  SourceStorageDto withParameters(Map<String, String> parameters);
}
