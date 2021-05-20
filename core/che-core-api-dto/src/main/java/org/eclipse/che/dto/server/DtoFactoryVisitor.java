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
package org.eclipse.che.dto.server;

/**
 * Visitor pattern. Generally needed to register DtoProviders by generated code in DtoFactory. Class
 * which contains generated code for server side implements this interface. When DtoFactory class is
 * loaded it looks up for all implementation of this interface and calls method {@link
 * #accept(DtoFactory)}.
 *
 * @author andrew00x
 */
public interface DtoFactoryVisitor {
  void accept(DtoFactory dtoFactory);
}
