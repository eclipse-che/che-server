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
package org.eclipse.che.api.user.server;

import javax.inject.Singleton;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.user.server.model.impl.UserImpl;

/**
 * Dummy implementation of {@link org.eclipse.che.api.user.server.TokenValidator}.
 *
 * @author Ann Shumilova
 * @author Dmitry Shnurenko
 */
@Singleton
public class DummyTokenValidator implements TokenValidator {

  @Override
  public User validateToken(String token) throws ConflictException {
    return new UserImpl("che", "che", "che@eclipse.org");
  }
}
