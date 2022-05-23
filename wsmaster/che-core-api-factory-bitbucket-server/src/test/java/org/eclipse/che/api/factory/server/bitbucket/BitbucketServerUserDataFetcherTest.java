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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.net.MalformedURLException;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketServerApiClient;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketUser;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class BitbucketServerUserDataFetcherTest {
  String someBitbucketURL = "https://some.bitbucketserver.com";
  Subject subject;
  @Mock BitbucketServerApiClient bitbucketServerApiClient;
  BitbucketUser bitbucketUser;
  BitbucketServerUserDataFetcher fetcher;

  @BeforeMethod
  public void setup() throws MalformedURLException {
    subject = new SubjectImpl("another_user", "user987", "token111", false);
    bitbucketUser =
        new BitbucketUser("User", "user", 32423523, "NORMAL", true, "user", "user@users.com");
    fetcher = new BitbucketServerUserDataFetcher(bitbucketServerApiClient, someBitbucketURL);
    EnvironmentContext context = new EnvironmentContext();
    context.setSubject(subject);
    EnvironmentContext.setCurrent(context);
  }

  @Test
  public void shouldBeAbleToFetchPersonalAccessToken()
      throws ScmUnauthorizedException, ScmCommunicationException, ScmItemNotFoundException,
          ScmBadRequestException {
    // given
    when(bitbucketServerApiClient.isConnected(eq(someBitbucketURL))).thenReturn(true);
    when(bitbucketServerApiClient.getUser(eq(subject))).thenReturn(bitbucketUser);
    // when
    GitUserData gitUserData = fetcher.fetchGitUserData();
    // then
    assertEquals(gitUserData.getScmUsername(), "user");
    assertEquals(gitUserData.getScmUserEmail(), "user@users.com");
  }
}
