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
package org.eclipse.che.security.oauth;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/**
 * Setup AzureDevOpsOAuthAuthenticator in guice container.
 *
 * @author Anatolii Bazko
 */
public class AzureDevOpsModule extends AbstractModule {
  @Override
  protected void configure() {
    Multibinder<OAuthAuthenticator> oAuthAuthenticators =
        Multibinder.newSetBinder(binder(), OAuthAuthenticator.class);
    oAuthAuthenticators.addBinding().toProvider(AzureDevOpsOAuthAuthenticatorProvider.class);
  }
}
