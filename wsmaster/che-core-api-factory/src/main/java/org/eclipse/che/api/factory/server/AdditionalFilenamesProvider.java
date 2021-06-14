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

import com.google.common.base.Splitter;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Reads, parses, and provide handful access to property which describes a list of additional files
 * required for devfile v2.
 */
public class AdditionalFilenamesProvider {

  private final List<String> filenames;

  @Inject
  public AdditionalFilenamesProvider(
      @Named("che.factory.devfile2_files_resolution_list") String additionalFilenamesString) {
    this.filenames = Splitter.on(",").splitToList(additionalFilenamesString);
  }

  public List<String> get() {
    return filenames;
  }
}
