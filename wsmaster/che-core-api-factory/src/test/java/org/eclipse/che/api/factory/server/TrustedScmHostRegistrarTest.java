/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.eclipse.che.commons.lang.UrlTargetValidator;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/** Tests of {@link TrustedScmHostRegistrar}. */
public class TrustedScmHostRegistrarTest {

  @AfterMethod
  public void restoreTrustedHosts() {
    UrlTargetValidator.setTrustedHosts(null);
  }

  @Test
  public void shouldTrustConfiguredInternalGitEndpoints() {
    TrustedScmHostRegistrar.register(
        "https://github.internal.example",
        "NULL",
        null,
        " https://bitbucket.internal.example , https://gitlab.internal.example/ ");

    assertTrue(
        UrlTargetValidator.isAllowed("https://github.internal.example/org/repo"));
    assertTrue(
        UrlTargetValidator.isAllowed(
            "https://raw.github.internal.example/org/repo/HEAD/devfile.yaml"));
    assertTrue(UrlTargetValidator.isAllowed("https://bitbucket.internal.example/projects/repo"));
    assertTrue(UrlTargetValidator.isAllowed("https://gitlab.internal.example/group/repo"));
  }

  @Test
  public void shouldNotTrustOtherPrivateTargets() {
    TrustedScmHostRegistrar.register("https://github.internal.example");

    assertFalse(UrlTargetValidator.isAllowed("http://169.254.169.254/latest/meta-data/"));
    assertFalse(UrlTargetValidator.isAllowed("http://10.0.0.1/"));
    assertFalse(UrlTargetValidator.isAllowed("http://127.0.0.1/"));
    assertFalse(UrlTargetValidator.isAllowed("file:///etc/passwd"));
  }
}
