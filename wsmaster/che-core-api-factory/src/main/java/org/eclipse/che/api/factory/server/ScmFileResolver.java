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
package org.eclipse.che.api.factory.server;

import org.eclipse.che.api.core.ApiException;

/** Defines a resolver that will resolve particular file content in specified SCM repository. */
public interface ScmFileResolver {

  /**
   * Resolver acceptance based on the given repository URL.
   *
   * @param repository repository URL to resolve file
   * @return true if it will be accepted by the resolver implementation or false if it is not
   *     accepted
   */
  boolean accept(String repository);

  /**
   * Resolves particular file in the given repository.
   *
   * @param repository repository URL to resolve file
   * @param filePath path to the desired file
   * @return content of the file if it is present in repository
   * @throws ApiException if the given file is absent or other error occurs
   */
  String fileContent(String repository, String filePath) throws ApiException;
}
