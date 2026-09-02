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
package org.eclipse.che.security.oauth.shared.dto;

import java.util.List;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.dto.shared.DTO;

/**
 * @author Max Shaposhnik (mshaposhnik@codenvy.com)
 */
@DTO
public interface OAuthAuthenticatorDescriptor {

  String getName();

  void setName(String name);

  OAuthAuthenticatorDescriptor withName(String name);

  String getEndpointUrl();

  void setEndpointUrl(String endpointUrl);

  OAuthAuthenticatorDescriptor withEndpointUrl(String endpointUrl);

  List<Link> getLinks();

  void setLinks(List<Link> links);

  OAuthAuthenticatorDescriptor withLinks(List<Link> links);

  /**
   * The OAuth App client ID. Exposed so clients can initiate flows that require the client ID
   * directly (e.g. GitHub Device Authorization Flow / RFC 8628) without needing access to the raw
   * Kubernetes Secret.
   */
  String getClientId();

  void setClientId(String clientId);

  OAuthAuthenticatorDescriptor withClientId(String clientId);
}
