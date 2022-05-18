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
package org.eclipse.che.api.factory.server.bitbucket;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Splitter;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketServerApiClient;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketUser;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.GitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.StringUtils;
import org.eclipse.che.commons.subject.Subject;

/** Bitbucket git user data retriever. */
public class BitbucketServerUserDataFetcher implements GitUserDataFetcher {

  /** Bitbucket API client. */
  private final BitbucketServerApiClient bitbucketServerApiClient;

  private final List<String> registeredBitbucketEndpoints;

  @Inject
  public BitbucketServerUserDataFetcher(
      BitbucketServerApiClient bitbucketServerApiClient,
      @Nullable @Named("che.integration.bitbucket.server_endpoints") String bitbucketEndpoints) {
    if (bitbucketEndpoints != null) {
      this.registeredBitbucketEndpoints =
          Splitter.on(",")
              .splitToStream(bitbucketEndpoints)
              .map(e -> StringUtils.trimEnd(e, '/'))
              .collect(toList());
    } else {
      this.registeredBitbucketEndpoints = Collections.emptyList();
    }
    this.bitbucketServerApiClient = bitbucketServerApiClient;
  }

  @Override
  public GitUserData fetchGitUserData() throws ScmUnauthorizedException, ScmCommunicationException {
    GitUserData gitUserData = null;
    for (String bitbucketServerEndpoint : this.registeredBitbucketEndpoints) {
      if (bitbucketServerApiClient.isConnected(bitbucketServerEndpoint)) {
        Subject cheSubject = EnvironmentContext.getCurrent().getSubject();
        try {
          BitbucketUser user = bitbucketServerApiClient.getUser(cheSubject);
          gitUserData = new GitUserData(user.getName(), user.getEmailAddress());
        } catch (ScmItemNotFoundException e) {
          throw new ScmCommunicationException(e.getMessage(), e);
        }
        break;
      }
    }
    if (gitUserData == null) {
      throw new ScmCommunicationException("Failed to retrieve git user data from Bitbucket");
    }
    return gitUserData;
  }
}
