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
package org.eclipse.che.security;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.eclipse.che.security.oauth.BitbucketOAuthAuthenticatorProvider;
import org.eclipse.che.security.oauth1.BitbucketServerOAuthAuthenticatorProvider;
import org.eclipse.che.security.oauth1.OAuthAuthenticator;

/**
 * Setup BitbucketServerOAuthAuthenticator in guice container.
 *
 * @author Sergii Kabashniuk
 */
public class BitbucketModule extends AbstractModule {
  @Override
  protected void configure() {
    Multibinder<OAuthAuthenticator> oAuth1Authenticators =
        Multibinder.newSetBinder(binder(), OAuthAuthenticator.class);
    oAuth1Authenticators.addBinding().toProvider(BitbucketServerOAuthAuthenticatorProvider.class);
    Multibinder<org.eclipse.che.security.oauth.OAuthAuthenticator> oAuth2Authenticators =
        Multibinder.newSetBinder(binder(), org.eclipse.che.security.oauth.OAuthAuthenticator.class);
    oAuth2Authenticators.addBinding().toProvider(BitbucketOAuthAuthenticatorProvider.class);
  }
}
