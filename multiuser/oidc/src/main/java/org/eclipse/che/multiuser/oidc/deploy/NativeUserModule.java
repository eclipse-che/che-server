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
package org.eclipse.che.multiuser.oidc.deploy;

import com.auth0.jwk.JwkProvider;
import com.google.inject.AbstractModule;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.SigningKeyResolver;
import org.eclipse.che.multiuser.api.authentication.commons.token.HeaderRequestTokenExtractor;
import org.eclipse.che.multiuser.api.authentication.commons.token.RequestTokenExtractor;
import org.eclipse.che.multiuser.oidc.OIDCInfo;
import org.eclipse.che.multiuser.oidc.OIDCInfoProvider;
import org.eclipse.che.multiuser.oidc.OIDCJwkProvider;
import org.eclipse.che.multiuser.oidc.OIDCJwtParserProvider;
import org.eclipse.che.multiuser.oidc.OIDCSigningKeyResolver;

public class NativeUserModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(SigningKeyResolver.class).to(OIDCSigningKeyResolver.class);
    bind(JwtParser.class).toProvider(OIDCJwtParserProvider.class);
    bind(JwkProvider.class).toProvider(OIDCJwkProvider.class);
    bind(OIDCInfo.class).toProvider(OIDCInfoProvider.class).asEagerSingleton();
    bind(RequestTokenExtractor.class).to(HeaderRequestTokenExtractor.class);
  }
}
