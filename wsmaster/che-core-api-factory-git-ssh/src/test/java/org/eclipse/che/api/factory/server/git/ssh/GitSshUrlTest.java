/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.git.ssh;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl.DevfileLocation;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitSshUrlTest {

  @Test
  public void shouldReturnDevfileLocations() throws Exception {
    String[] devfileNames = {"devfile.yaml", ".devfile.yaml"};
    GitSshUrl sshUrl =
        new GitSshUrl()
            .withRepository("repository")
            .withHostName("hostname")
            .withDevfileFilenames(Arrays.asList(devfileNames));
    List<DevfileLocation> devfileLocations = sshUrl.devfileFileLocations();
    assertEquals(devfileLocations.size(), 2);
    assertEquals(devfileLocations.get(0).location(), "https://hostname/repository/devfile.yaml");
    assertEquals(devfileLocations.get(1).location(), "https://hostname/repository/.devfile.yaml");
  }
}
