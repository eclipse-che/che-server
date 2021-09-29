/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.List;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests for {@link UserProfileConfigurator}.
 *
 * @author Pavol Baran
 */
@Listeners(MockitoTestNGListener.class)
public class UserProfileConfiguratorTest {
  private static final String USER_ID = "user-id";
  private static final String USER_NAME = "user-name";
  private static final String USER_EMAIL = "user-email";
  private static final String USER_NAMESPACE = "user-namespace";

  @Mock private KubernetesNamespaceFactory namespaceFactory;
  @Mock private KubernetesClientFactory clientFactory;
  @Mock private UserManager userManager;

  @InjectMocks private UserProfileConfigurator userProfileConfigurator;

  private KubernetesServer kubernetesServer;
  private NamespaceResolutionContext context;

  @BeforeMethod
  public void setUp() throws InfrastructureException, NotFoundException, ServerException {
    context = new NamespaceResolutionContext(null, USER_ID, USER_NAME);
    kubernetesServer = new KubernetesServer(true, true);
    kubernetesServer.before();

    when(userManager.getById(USER_ID)).thenReturn(new UserImpl(USER_ID, USER_EMAIL, USER_NAME));
    when(namespaceFactory.evaluateNamespaceName(any())).thenReturn(USER_NAMESPACE);
    when(clientFactory.create()).thenReturn(kubernetesServer.getClient());
  }

  @AfterMethod
  public void cleanUp() {
    kubernetesServer.after();
  }

  @Test
  public void shouldCreateProfileSecret() throws InfrastructureException {
    userProfileConfigurator.configure(context);
    List<Secret> secrets =
        kubernetesServer.getClient().secrets().inNamespace(USER_NAMESPACE).list().getItems();
    assertEquals(secrets.size(), 1);
    assertEquals(secrets.get(0).getMetadata().getName(), "user-profile");
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp = "Could not find current user with id:" + USER_ID + ".")
  public void shouldNotCreateSecretOnException()
      throws NotFoundException, ServerException, InfrastructureException {
    when(userManager.getById(USER_ID)).thenThrow(new ServerException("test exception"));
    userProfileConfigurator.configure(context);
    fail("InfrastructureException should have been thrown.");
  }
}
