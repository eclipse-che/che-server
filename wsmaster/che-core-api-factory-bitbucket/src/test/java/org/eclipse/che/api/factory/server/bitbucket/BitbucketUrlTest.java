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

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Iterator;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl.DevfileLocation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** Test of {@Link BitbucketUrl} Note: The parser is also testing the object */
@Listeners(MockitoTestNGListener.class)
public class BitbucketUrlTest {

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  /** Parser used to create the url. */
  @InjectMocks private BitbucketURLParser bitbucketURLParser;

  /** Instance of the url created */
  private BitbucketUrl bitbucketUrl;

  /** Setup objects/ */
  @BeforeMethod
  protected void init() {
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(Arrays.asList("devfile.yaml", "foo.bar"));
    this.bitbucketUrl = this.bitbucketURLParser.parse("https://bitbucket.org/eclipse/che");
    assertNotNull(this.bitbucketUrl);
  }

  /** Check when there is devfile in the repository */
  @Test
  public void checkDevfileLocation() {
    lenient()
        .when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(Arrays.asList("devfile.yaml", "foo.bar"));

    assertEquals(bitbucketUrl.devfileFileLocations().size(), 2);
    Iterator<DevfileLocation> iterator = bitbucketUrl.devfileFileLocations().iterator();
    assertEquals(
        iterator.next().location(), "https://bitbucket.org/eclipse/che/raw/HEAD/devfile.yaml");

    assertEquals(iterator.next().location(), "https://bitbucket.org/eclipse/che/raw/HEAD/foo.bar");
  }

  /** Check the original repository */
  @Test
  public void checkRepositoryLocation() {
    assertEquals(bitbucketUrl.repositoryLocation(), "https://bitbucket.org/eclipse/che.git");
  }
}
