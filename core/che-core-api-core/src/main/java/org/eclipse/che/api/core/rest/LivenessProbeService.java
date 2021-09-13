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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import javax.inject.Singleton;

/**
 * Endpoint for the liveness checks.
 *
 * @author Max Shaposhnik (mshaposh@redhat.com)
 */
@Singleton
@Path("/liveness")
public class LivenessProbeService {
  @GET
  public Response checkAlive() {
    return Response.ok().build();
  }
}
