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

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.ProjectConfigDtoMerger;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.security.oauth.AuthorisationRequestManager;

/**
 * Provides Factory Parameters resolver for github repositories.
 *
 * @author Florent Benoit
 */
@Singleton
public class GithubFactoryParametersResolverSecond extends AbstractGithubFactoryParametersResolver {

  private static final String PROVIDER_NAME = "github_2";

  @Inject
  public GithubFactoryParametersResolverSecond(
      GithubURLParserSecond githubUrlParser,
      URLFetcher urlFetcher,
      GithubSourceStorageBuilder githubSourceStorageBuilder,
      AuthorisationRequestManager authorisationRequestManager,
      URLFactoryBuilder urlFactoryBuilder,
      ProjectConfigDtoMerger projectConfigDtoMerger,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(
        githubUrlParser,
        urlFetcher,
        githubSourceStorageBuilder,
        authorisationRequestManager,
        urlFactoryBuilder,
        projectConfigDtoMerger,
        personalAccessTokenManager,
        PROVIDER_NAME);
  }
}
