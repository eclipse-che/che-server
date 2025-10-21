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
package org.eclipse.che.api.factory.server.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class GithubRepo {
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public GithubRepo withName(String name) {
    this.name = name;
    return this;
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class GithubHead {
  private String ref;
  private GithubUser user;
  private GithubRepo repo;

  public String getRef() {
    return ref;
  }

  public void setRef(String ref) {
    this.ref = ref;
  }

  public GithubHead withRef(String ref) {
    this.ref = ref;
    return this;
  }

  public GithubUser getUser() {
    return user;
  }

  public void setUser(GithubUser user) {
    this.user = user;
  }

  public GithubHead withUser(GithubUser user) {
    this.user = user;
    return this;
  }

  public GithubRepo getRepo() {
    return repo;
  }

  public void setRepo(GithubRepo repo) {
    this.repo = repo;
  }

  public GithubHead withRepo(GithubRepo repo) {
    this.repo = repo;
    return this;
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubPullRequest {
  private String state;
  private GithubHead head;

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public GithubPullRequest withState(String state) {
    this.state = state;
    return this;
  }

  public GithubHead getHead() {
    return head;
  }

  public void setHead(GithubHead head) {
    this.head = head;
  }

  public GithubPullRequest withHead(GithubHead head) {
    this.head = head;
    return this;
  }
}
