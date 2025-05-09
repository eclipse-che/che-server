/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.resource.api.type;

import java.util.Set;
import org.eclipse.che.multiuser.resource.api.exception.NoEnoughResourcesException;
import org.eclipse.che.multiuser.resource.model.Resource;

/**
 * Represents some kind of resources which can be used by account.
 *
 * @author Sergii Leschenko
 */
public interface ResourceType {
  /** Returns id of resource type. */
  String getId();

  /** Returns description of resource type. */
  String getDescription();

  /** Returns supported units. */
  Set<String> getSupportedUnits();

  /** Returns default unit. */
  String getDefaultUnit();

  /**
   * Defines function for aggregating two resources of this type.
   *
   * @param resourceA resources A
   * @param resourceB resource B
   * @throws IllegalArgumentException if one of resources has unsupported type or unit
   */
  Resource aggregate(Resource resourceA, Resource resourceB);

  /**
   * Defines function for subtraction two resources of this type.
   *
   * @param total total resource
   * @param deduction resource that should be deducted from {@code total}
   * @throws IllegalArgumentException if one of resources has unsupported type or unit
   * @throws NoEnoughResourcesException when {@code total}'s amount is less than {@code deduction}'s
   *     amount
   */
  Resource deduct(Resource total, Resource deduction) throws NoEnoughResourcesException;
}
