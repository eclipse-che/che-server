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
import static java.util.Collections.emptySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class OAuthIdeRedirectManagerTest {

  private static final String CHE_HOST = "https://che.apps.cluster.example.com";

  /** Main URL of the workspace of the authenticated user, as published by the DevWorkspace. */
  private static final String OWN_WORKSPACE_URL = CHE_HOST + "/alice/nodejs-angular/3100/";

  /** Main URL of a workspace of a different user on the same Che host. */
  private static final String FOREIGN_WORKSPACE_URL = CHE_HOST + "/mallory/collector/3100/";

  /** Callback the browser IDE of the authenticated user actually listens on. */
  private static final String OWN_CALLBACK_URL =
      CHE_HOST
          + "/alice/nodejs-angular/3100/callback?vscode-reqid=1&vscode-scheme=code-oss"
          + "&vscode-authority=gitlab.gitlab-workflow&vscode-path=%2Fauthentication";

  private UserWorkspaceUrlProvider workspaceUrlProvider;
  private OAuthIdeRedirectManager manager;

  @BeforeMethod
  public void setUp() throws Exception {
    workspaceUrlProvider = mock(UserWorkspaceUrlProvider.class);
    when(workspaceUrlProvider.getWorkspaceUrls()).thenReturn(ImmutableSet.of(OWN_WORKSPACE_URL));
    manager = new OAuthIdeRedirectManager(workspaceUrlProvider);
    setSubject(new SubjectImpl("alice", emptyList(), "alice-id", "token", false));
  }

  @AfterMethod
  public void tearDown() {
    EnvironmentContext.reset();
  }

  @Test
  public void shouldRedirectToOwnWorkspaceWithCodeAndState() throws Exception {
    UriInfo uriInfo =
        mockUriInfo("code", "auth-code-abc", "state", compositeState("csrf-123", OWN_CALLBACK_URL));

    Response response = manager.ideRedirect(uriInfo);

    assertEquals(response.getStatus(), 307);
    String location = location(response);
    assertTrue(location.startsWith(CHE_HOST + "/alice/nodejs-angular/3100/callback?"), location);
    assertTrue(location.contains("code=auth-code-abc"), location);
    assertTrue(location.contains("state=csrf-123"), location);
    // the query the IDE put into the callback URL must survive
    assertTrue(location.contains("vscode-reqid=1"), location);
    assertTrue(location.contains("vscode-path=%2Fauthentication"), location);
    assertEquals(response.getMetadata().getFirst("Cache-Control"), "no-store");
    assertEquals(response.getMetadata().getFirst("Referrer-Policy"), "no-referrer");
  }

  @Test
  public void shouldForwardErrorParams() throws Exception {
    UriInfo uriInfo =
        mockUriInfo(
            "state", compositeState("csrf", OWN_CALLBACK_URL),
            "error", "access_denied",
            "error_description", "User denied access");

    String location = location(manager.ideRedirect(uriInfo));

    assertTrue(location.contains("error=access_denied"), location);
    assertTrue(location.contains("error_description=User"), location);
    assertFalse(location.contains("code="), location);
  }

  @Test
  public void shouldAcceptAnyOfTheWorkspacesOfTheUser() throws Exception {
    when(workspaceUrlProvider.getWorkspaceUrls())
        .thenReturn(ImmutableSet.of(CHE_HOST + "/alice/other/3100/", OWN_WORKSPACE_URL));

    Response response =
        manager.ideRedirect(
            mockUriInfo("code", "c", "state", compositeState("csrf", OWN_CALLBACK_URL)));

    assertEquals(response.getStatus(), 307);
  }

  // --- the callback URL must belong to the authenticated user -------------------------------

  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRejectCallbackToAnotherUsersWorkspace() throws Exception {
    String foreignCallback = CHE_HOST + "/mallory/collector/3100/callback?vscode-reqid=1";

    manager.ideRedirect(mockUriInfo("code", "c", "state", compositeState("csrf", foreignCallback)));
  }

  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRejectCallbackWhenUserHasNoWorkspaces() throws Exception {
    when(workspaceUrlProvider.getWorkspaceUrls()).thenReturn(emptySet());

    manager.ideRedirect(
        mockUriInfo("code", "c", "state", compositeState("csrf", OWN_CALLBACK_URL)));
  }

  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRejectCallbackOutsideOfTheWorkspacePath() throws Exception {
    manager.ideRedirect(
        mockUriInfo("code", "c", "state", compositeState("csrf", CHE_HOST + "/callback")));
  }

  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRejectPathTraversalOutOfTheWorkspacePath() throws Exception {
    String traversal = CHE_HOST + "/alice/nodejs-angular/3100/../../../mallory/collector/3100/cb";

    manager.ideRedirect(mockUriInfo("code", "c", "state", compositeState("csrf", traversal)));
  }

  @Test(expectedExceptions = ForbiddenException.class)
  public void shouldRejectAnonymousUser() throws Exception {
    setSubject(Subject.ANONYMOUS);

    manager.ideRedirect(
        mockUriInfo("code", "c", "state", compositeState("csrf", OWN_CALLBACK_URL)));
  }

  // --- callback URL syntax ------------------------------------------------------------------

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectCallbackWithUserInfo() throws Exception {
    String withUserInfo = "https://alice@che.apps.cluster.example.com/alice/nodejs-angular/3100/cb";

    manager.ideRedirect(mockUriInfo("code", "c", "state", compositeState("csrf", withUserInfo)));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectCallbackWithFragment() throws Exception {
    String withFragment = OWN_WORKSPACE_URL + "callback#fragment";

    manager.ideRedirect(mockUriInfo("code", "c", "state", compositeState("csrf", withFragment)));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectCallbackWithEncodedPathSeparator() throws Exception {
    String encodedSlash = CHE_HOST + "/alice%2Fnodejs-angular/3100/callback";

    manager.ideRedirect(mockUriInfo("code", "c", "state", compositeState("csrf", encodedSlash)));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectCallbackWithNonHttpScheme() throws Exception {
    manager.ideRedirect(
        mockUriInfo("code", "c", "state", compositeState("csrf", "ftp://che.example.com/cb")));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectRelativeCallbackUrl() throws Exception {
    manager.ideRedirect(
        mockUriInfo("code", "c", "state", compositeState("csrf", "/alice/ws/3100/cb")));
  }

  // --- state and code parameters --------------------------------------------------------------

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectMissingState() throws Exception {
    manager.ideRedirect(mockUriInfo("code", "auth-code"));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectNonBase64State() throws Exception {
    manager.ideRedirect(mockUriInfo("code", "auth-code", "state", "not!base64!json"));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectStateWithoutCallbackUrl() throws Exception {
    JsonObject json = new JsonObject();
    json.addProperty("s", "csrf");

    manager.ideRedirect(mockUriInfo("code", "auth-code", "state", encode(json)));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectStateWithoutCsrfToken() throws Exception {
    JsonObject json = new JsonObject();
    json.addProperty("c", OWN_CALLBACK_URL);

    manager.ideRedirect(mockUriInfo("code", "auth-code", "state", encode(json)));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectStateWithNonStringCallbackUrl() throws Exception {
    JsonObject json = new JsonObject();
    json.addProperty("s", "csrf");
    json.add("c", new JsonObject());

    manager.ideRedirect(mockUriInfo("code", "auth-code", "state", encode(json)));
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldRejectMissingCodeAndError() throws Exception {
    manager.ideRedirect(mockUriInfo("state", compositeState("csrf", OWN_CALLBACK_URL)));
  }

  // --- isLocatedUnder -------------------------------------------------------------------------

  @Test
  public void isLocatedUnderMatchesTheWorkspaceRootItself() {
    assertTrue(isLocatedUnder(CHE_HOST + "/alice/ws/3100", CHE_HOST + "/alice/ws/3100/"));
    assertTrue(isLocatedUnder(CHE_HOST + "/alice/ws/3100/", CHE_HOST + "/alice/ws/3100"));
  }

  @Test
  public void isLocatedUnderComparesWholePathSegments() {
    assertFalse(isLocatedUnder(CHE_HOST + "/alice/ws/31000/cb", CHE_HOST + "/alice/ws/3100/"));
    assertFalse(isLocatedUnder(CHE_HOST + "/alice/wsp/3100/cb", CHE_HOST + "/alice/ws/3100/"));
  }

  @Test
  public void isLocatedUnderIgnoresTheQueryOfTheWorkspaceUrl() {
    assertTrue(
        isLocatedUnder(
            CHE_HOST + "/alice/ws/3100/cb", CHE_HOST + "/alice/ws/3100/?tkn=eclipse-che"));
  }

  @Test
  public void isLocatedUnderRequiresTheSameOrigin() {
    assertFalse(
        isLocatedUnder("https://evil.example.com/alice/nodejs-angular/3100/cb", OWN_WORKSPACE_URL));
    assertFalse(
        isLocatedUnder(
            "http://che.apps.cluster.example.com/alice/nodejs-angular/3100/cb", OWN_WORKSPACE_URL));
    assertFalse(isLocatedUnder(CHE_HOST + ":8443/alice/nodejs-angular/3100/cb", OWN_WORKSPACE_URL));
  }

  @Test
  public void isLocatedUnderTreatsTheDefaultPortAsEqualToTheImplicitOne() {
    assertTrue(isLocatedUnder(CHE_HOST + ":443/alice/nodejs-angular/3100/cb", OWN_WORKSPACE_URL));
  }

  @Test
  public void isLocatedUnderNeverMatchesABlankOrRootWorkspaceUrl() {
    assertFalse(isLocatedUnder(CHE_HOST + "/anything", null));
    assertFalse(isLocatedUnder(CHE_HOST + "/anything", ""));
    assertFalse(isLocatedUnder(CHE_HOST + "/anything", CHE_HOST));
    assertFalse(isLocatedUnder(CHE_HOST + "/anything", CHE_HOST + "/"));
  }

  @Test
  public void isLocatedUnderIgnoresUnparseableWorkspaceUrls() {
    assertFalse(isLocatedUnder(CHE_HOST + "/alice/ws/3100/cb", "not a url"));
    assertFalse(isLocatedUnder(CHE_HOST + "/alice/ws/3100/cb", "/alice/ws/3100/"));
  }

  @Test
  public void isLocatedUnderDoesNotMatchAForeignWorkspaceOnTheSameHost() {
    assertFalse(isLocatedUnder(CHE_HOST + "/alice/ws/3100/cb", FOREIGN_WORKSPACE_URL));
  }

  // --- helpers ---------------------------------------------------------------------------------

  private static boolean isLocatedUnder(String callbackUrl, String workspaceUrl) {
    return OAuthIdeRedirectManager.isLocatedUnder(URI.create(callbackUrl), workspaceUrl);
  }

  private static void setSubject(Subject subject) {
    EnvironmentContext context = new EnvironmentContext();
    context.setSubject(subject);
    EnvironmentContext.setCurrent(context);
  }

  private static String location(Response response) {
    return ((URI) response.getMetadata().getFirst("Location")).toString();
  }

  private static String compositeState(String csrfState, String callbackUrl) {
    JsonObject json = new JsonObject();
    json.addProperty("s", csrfState);
    json.addProperty("c", callbackUrl);
    return encode(json);
  }

  private static String encode(JsonObject json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static UriInfo mockUriInfo(String... keyValues) {
    MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      params.putSingle(keyValues[i], keyValues[i + 1]);
    }
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getQueryParameters()).thenReturn(params);
    return uriInfo;
  }
}
