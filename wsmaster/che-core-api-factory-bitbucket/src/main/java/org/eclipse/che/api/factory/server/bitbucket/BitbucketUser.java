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
package org.eclipse.che.api.factory.server.bitbucket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketUser {
  private String name;
  private String id;

  public String getName() {
    return name;
  }

  @JsonProperty("username")
  public void setName(String name) {
    this.name = name;
  }

  public String getId() {
    return id;
  }

  @JsonProperty("account_id")
  public void setId(String id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BitbucketUser that = (BitbucketUser) o;
    return Objects.equals(name, that.name) && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, id);
  }

  @Override
  public String toString() {
    return "BitbucketUser{" + "name='" + name + '\'' + ", id='" + id + '\'' + '}';
  }
}
