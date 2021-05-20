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
package org.eclipse.che.commons.xml;

/**
 * Describes new attribute. Should be used to insert new attribute into existing tree element or may
 * be a part of {@link NewElement}.
 *
 * @author Eugene Voevodin
 */
public final class NewAttribute extends QName {

  private String value;

  public NewAttribute(String qName, String value) {
    super(qName);
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public String asString() {
    return getName() + '=' + '"' + value + '"';
  }

  @Override
  public String toString() {
    return asString();
  }
}
