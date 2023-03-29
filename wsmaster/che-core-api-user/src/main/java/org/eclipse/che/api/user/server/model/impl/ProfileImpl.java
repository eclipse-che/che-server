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
package org.eclipse.che.api.user.server.model.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import org.eclipse.che.api.core.model.user.Profile;

/**
 * Data object for the {@link Profile}.
 *
 * @author Yevhenii Voevodin
 */
@Entity(name = "Profile")
@Table(name = "profile")
public class ProfileImpl implements Profile {

  @Id
  @Column(name = "userid")
  private String userId;

  @PrimaryKeyJoinColumn private UserImpl user;

  @ElementCollection
  @MapKeyColumn(name = "name")
  @Column(name = "value_param", nullable = false)
  @CollectionTable(name = "profile_attributes", joinColumns = @JoinColumn(name = "user_id"))
  private Map<String, String> attributes;

  public ProfileImpl() {}

  public ProfileImpl(String userId) {
    this.userId = userId;
  }

  public ProfileImpl(String userId, Map<String, String> attributes) {
    this.userId = userId;
    if (attributes != null) {
      this.attributes = new HashMap<>(attributes);
    }
  }

  public ProfileImpl(Profile profile) {
    this(profile.getUserId(), profile.getAttributes());
  }

  public ProfileImpl(ProfileImpl profile) {
    this(profile.getUserId(), profile.getAttributes());
    this.user = profile.user;
  }

  @Override
  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  @Override
  public Map<String, String> getAttributes() {
    if (attributes == null) {
      this.attributes = new HashMap<>();
    }
    return attributes;
  }

  public UserImpl getUser() {
    return user;
  }

  public void setAttributes(Map<String, String> attributes) {
    this.attributes = attributes;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ProfileImpl)) {
      return false;
    }
    final ProfileImpl that = (ProfileImpl) obj;
    return Objects.equals(userId, that.userId) && getAttributes().equals(that.getAttributes());
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 31 * hash + Objects.hashCode(userId);
    hash = 31 * hash + getAttributes().hashCode();
    return hash;
  }

  @Override
  public String toString() {
    return "ProfileImpl{" + "userId='" + userId + '\'' + ", attributes=" + attributes + '}';
  }
}
