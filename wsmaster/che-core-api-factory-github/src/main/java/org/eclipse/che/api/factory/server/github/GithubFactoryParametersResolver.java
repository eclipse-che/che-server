/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
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

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/**
 * Provides Factory Parameters resolver for github repositories.
 *
 * @author Florent Benoit
 */
@Singleton
public class GithubFactoryParametersResolver extends AbstractGithubFactoryParametersResolver {

  private static final String PROVIDER_NAME = "github";

  @Inject
  public GithubFactoryParametersResolver(
      GithubURLParser githubUrlParser,
      URLFetcher urlFetcher,
      GithubSourceStorageBuilder githubSourceStorageBuilder,
      AuthorisationRequestManager authorisationRequestManager,
      URLFactoryBuilder urlFactoryBuilder,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(
        githubUrlParser,
        urlFetcher,
        githubSourceStorageBuilder,
        authorisationRequestManager,
        urlFactoryBuilder,
        personalAccessTokenManager,
        PROVIDER_NAME);
  }
}
