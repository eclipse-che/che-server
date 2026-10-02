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
package org.eclipse.che.security.oauth;

import static java.util.Collections.emptyList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.fail;

import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class OAuthCsrfNonceStoreTest {

  private static final Subject ALICE =
      new SubjectImpl("alice", emptyList(), "alice-id", "token", false);

  /** The attacker, who can start a flow of their own and so get a nonce of their own. */
  private static final Subject MALLORY =
      new SubjectImpl("mallory", emptyList(), "mallory-id", "token", false);

  private OAuthCsrfNonceStore store;

  @BeforeMethod
  public void setUp() {
    store = new OAuthCsrfNonceStore();
  }

  @Test
  public void shouldAcceptANonceIssuedToTheSameUser() throws Exception {
    store.verify(store.issue(ALICE), ALICE);
  }

  /** A user id is all that is compared, so the subject of the callback is a different instance. */
  @Test
  public void shouldAcceptANonceOfTheSameUserInAnotherRequest() throws Exception {
    String nonce = store.issue(ALICE);

    store.verify(nonce, new SubjectImpl("alice", emptyList(), "alice-id", "other-token", false));
  }

  @Test
  public void shouldRefuseANonceIssuedToAnotherUser() {
    String mallorysNonce = store.issue(MALLORY);

    assertRefused(mallorysNonce, ALICE);
  }

  /** A nonce answers one callback, so the same callback URL cannot be opened twice. */
  @Test
  public void shouldRefuseANonceThatHasAlreadyBeenUsed() throws Exception {
    String nonce = store.issue(ALICE);
    store.verify(nonce, ALICE);

    assertRefused(nonce, ALICE);
  }

  @Test(dataProvider = "unusableNonces")
  public void shouldRefuseANonceItDidNotIssue(String nonce) {
    store.issue(ALICE);

    assertRefused(nonce, ALICE);
  }

  @DataProvider
  public Object[][] unusableNonces() {
    return new Object[][] {{null}, {""}, {"made-up-nonce"}};
  }

  /** A nonce is unguessable, so an attacker cannot build a callback URL that passes the check. */
  @Test
  public void shouldIssueAnUnpredictableNonceEveryTime() {
    String first = store.issue(ALICE);
    String second = store.issue(ALICE);

    assertNotEquals(first, second);
    // 32 random bytes, base64url encoded without padding
    assertEquals(first.length(), 43);
    assertEquals(first.replaceAll("[A-Za-z0-9_-]", ""), "");
  }

  private void assertRefused(String nonce, Subject subject) {
    try {
      store.verify(nonce, subject);
      fail("Expected the callback with nonce '" + nonce + "' to be refused");
    } catch (ForbiddenException e) {
      assertEquals(
          e.getMessage(),
          "The OAuth callback does not answer an authorization request of the current user");
    }
  }
}
