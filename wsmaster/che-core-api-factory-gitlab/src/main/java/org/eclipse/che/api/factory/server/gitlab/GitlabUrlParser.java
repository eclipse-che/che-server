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
package org.eclipse.che.api.factory.server.gitlab;

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Parser of String Gitlab URLs and provide {@link GitlabUrl} objects.
 *
 * @author Max Shaposhnyk
 */
public class GitlabUrlParser extends AbstractGitlabUrlParser {

  private static final String OAUTH_PROVIDER_NAME = "gitlab";

  @Inject
  public GitlabUrlParser(
      @Nullable @Named("che.integration.gitlab.oauth_endpoint") String serverUrl,
      DevfileFilenamesProvider devfileFilenamesProvider,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(serverUrl, devfileFilenamesProvider, personalAccessTokenManager, OAUTH_PROVIDER_NAME);
  }
}
