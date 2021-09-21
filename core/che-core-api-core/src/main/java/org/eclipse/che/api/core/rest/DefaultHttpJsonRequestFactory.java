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
package org.eclipse.che.api.core.rest;

import jakarta.validation.constraints.NotNull;
import javax.inject.Singleton;
import org.eclipse.che.api.core.rest.shared.dto.Link;

/**
 * Creates {@link DefaultHttpJsonRequest} instances.
 *
 * @author Yevhenii Voevodin
 */
@Singleton
public class DefaultHttpJsonRequestFactory implements HttpJsonRequestFactory {

  @Override
  public HttpJsonRequest fromUrl(@NotNull String url) {
    return new DefaultHttpJsonRequest(url);
  }

  @Override
  public HttpJsonRequest fromLink(@NotNull Link link) {
    return new DefaultHttpJsonRequest(link);
  }
}
