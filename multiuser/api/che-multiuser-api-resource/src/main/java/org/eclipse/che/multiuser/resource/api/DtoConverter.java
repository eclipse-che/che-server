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
package org.eclipse.che.multiuser.resource.api;

import java.util.stream.Collectors;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.multiuser.resource.model.FreeResourcesLimit;
import org.eclipse.che.multiuser.resource.model.ProvidedResources;
import org.eclipse.che.multiuser.resource.model.Resource;
import org.eclipse.che.multiuser.resource.model.ResourcesDetails;
import org.eclipse.che.multiuser.resource.shared.dto.FreeResourcesLimitDto;
import org.eclipse.che.multiuser.resource.shared.dto.ProvidedResourcesDto;
import org.eclipse.che.multiuser.resource.shared.dto.ResourceDto;
import org.eclipse.che.multiuser.resource.shared.dto.ResourcesDetailsDto;

/**
 * Helps to convert objects related to resource to DTOs.
 *
 * @author Sergii Leschenko
 */
public final class DtoConverter {
  private DtoConverter() {}

  public static ResourceDto asDto(Resource resource) {
    return DtoFactory.newDto(ResourceDto.class)
        .withAmount(resource.getAmount())
        .withType(resource.getType())
        .withUnit(resource.getUnit());
  }

  public static FreeResourcesLimitDto asDto(FreeResourcesLimit limit) {
    return DtoFactory.newDto(FreeResourcesLimitDto.class)
        .withResources(
            limit.getResources().stream().map(DtoConverter::asDto).collect(Collectors.toList()))
        .withAccountId(limit.getAccountId());
  }

  public static ResourcesDetailsDto asDto(ResourcesDetails resourcesDetails) {
    return DtoFactory.newDto(ResourcesDetailsDto.class)
        .withAccountId(resourcesDetails.getAccountId())
        .withTotalResources(
            resourcesDetails.getTotalResources().stream()
                .map(DtoConverter::asDto)
                .collect(Collectors.toList()))
        .withProvidedResources(
            resourcesDetails.getProvidedResources().stream()
                .map(DtoConverter::asDto)
                .collect(Collectors.toList()));
  }

  private static ProvidedResourcesDto asDto(ProvidedResources providedResources) {
    return DtoFactory.newDto(ProvidedResourcesDto.class)
        .withId(providedResources.getId())
        .withOwner(providedResources.getOwner())
        .withStartTime(providedResources.getStartTime())
        .withEndTime(providedResources.getEndTime())
        .withProviderId(providedResources.getProviderId())
        .withResources(
            providedResources.getResources().stream()
                .map(DtoConverter::asDto)
                .collect(Collectors.toList()));
  }
}
