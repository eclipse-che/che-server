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

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.che.multiuser.organization.api.event.BeforeOrganizationRemovedEvent;
import org.eclipse.che.multiuser.organization.spi.OrganizationDistributedResourcesDao;
import org.eclipse.che.multiuser.organization.spi.impl.OrganizationImpl;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests for {@link
 * org.eclipse.che.multiuser.organization.api.resource.OrganizationResourcesDistributor.RemoveOrganizationDistributedResourcesSubscriber}
 *
 * @author Sergii Leschenko
 */
@Listeners(MockitoTestNGListener.class)
public class RemoveOrganizationDistributedResourcesSubscriberTest {
  @Mock private OrganizationImpl organization;
  @Mock private OrganizationDistributedResourcesDao organizationDistributedResourcesDao;
  @InjectMocks private OrganizationResourcesDistributor organizationResourcesDistributor;

  private OrganizationResourcesDistributor.RemoveOrganizationDistributedResourcesSubscriber
      suborganizationsRemover;

  @BeforeMethod
  public void setUp() throws Exception {
    suborganizationsRemover =
        organizationResourcesDistributor.new RemoveOrganizationDistributedResourcesSubscriber();
  }

  @Test
  public void shouldResetResourcesDistributionBeforeSuborganizationRemoving() throws Exception {
    // given
    when(organization.getId()).thenReturn("suborg123");
    when(organization.getParent()).thenReturn("org123");

    // when
    suborganizationsRemover.onEvent(new BeforeOrganizationRemovedEvent(organization));

    // then
    verify(organizationDistributedResourcesDao).remove("suborg123");
  }

  @Test
  public void shouldNotResetResourcesDistributionBeforeRootOrganizationRemoving() throws Exception {
    // given
    lenient().when(organization.getId()).thenReturn("org123");
    when(organization.getParent()).thenReturn(null);

    // when
    suborganizationsRemover.onEvent(new BeforeOrganizationRemovedEvent(organization));

    // then
    verify(organizationDistributedResourcesDao, never()).remove("org123");
  }
}
