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
package org.eclipse.che.multiuser.organization.api.resource;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.multiuser.organization.api.OrganizationManager;
import org.eclipse.che.multiuser.organization.shared.model.Organization;
import org.eclipse.che.multiuser.organization.spi.impl.OrganizationImpl;
import org.eclipse.che.multiuser.resource.api.ResourceLockKeyProvider;

/**
 * Provides resources lock key for accounts with organizational type.
 *
 * <p>A lock key for any organization is an identifier of the root organization.
 *
 * @author Sergii Leschenko
 */
@Singleton
public class OrganizationResourceLockKeyProvider implements ResourceLockKeyProvider {
  private final OrganizationManager organizationManager;

  @Inject
  public OrganizationResourceLockKeyProvider(OrganizationManager organizationManager) {
    this.organizationManager = organizationManager;
  }

  @Override
  public String getLockKey(String accountId) throws ServerException {
    String currentOrganizationId = accountId;
    try {
      Organization organization = organizationManager.getById(currentOrganizationId);
      while (organization.getParent() != null) {
        currentOrganizationId = organization.getParent();
        organization = organizationManager.getById(currentOrganizationId);
      }
      return organization.getId();
    } catch (NotFoundException e) {
      // should not happen
      throw new ServerException(e.getLocalizedMessage(), e);
    }
  }

  @Override
  public String getAccountType() {
    return OrganizationImpl.ORGANIZATIONAL_ACCOUNT;
  }
}
