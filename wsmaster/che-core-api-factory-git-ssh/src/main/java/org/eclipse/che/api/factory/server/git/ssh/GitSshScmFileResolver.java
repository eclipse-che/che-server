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
package org.eclipse.che.api.factory.server.git.ssh;

import jakarta.validation.constraints.NotNull;
import javax.inject.Inject;
import org.eclipse.che.api.factory.server.ScmFileResolver;

/**
 * Git Ssh specific SCM file resolver.
 *
 * @author Anatolii Bazko
 */
public class GitSshScmFileResolver implements ScmFileResolver {

  private final GitSshURLParser gitSshURLParser;

  @Inject
  public GitSshScmFileResolver(GitSshURLParser gitSshURLParser) {
    this.gitSshURLParser = gitSshURLParser;
  }

  @Override
  public boolean accept(@NotNull String repository) {
    return gitSshURLParser.isValid(repository);
  }

  @Override
  public String fileContent(@NotNull String repository, @NotNull String filePath) {
    return "";
  }
}
