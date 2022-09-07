/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.security.oauth;

import org.eclipse.che.security.oauth.shared.User;

/** Represents Bitbucket user. */
public class BitbucketUser implements User {

  private String name;
  private String id;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getEmail() {
    return "email";
  }

  @Override
  public void setEmail(String email) {}

  public void setUsername(String name) {
    this.name = name;
  }

  public void setAccount_id(String id) {
    this.id = id;
  }
}
