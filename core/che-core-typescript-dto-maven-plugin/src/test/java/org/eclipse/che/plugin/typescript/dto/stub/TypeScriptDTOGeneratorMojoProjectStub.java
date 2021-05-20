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
package org.eclipse.che.plugin.typescript.dto.stub;

import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.codehaus.plexus.util.ReaderFactory;
import org.mockito.Mockito;

/** @author Florent Benoit */
public class TypeScriptDTOGeneratorMojoProjectStub extends MavenProjectStub {

  /** {@inheritDoc} */
  @Override
  public File getBasedir() {
    return new File(super.getBasedir() + "/src/test/projects/project");
  }

  /** Default constructor */
  public TypeScriptDTOGeneratorMojoProjectStub() {
    MavenXpp3Reader pomReader = new MavenXpp3Reader();
    Model model;
    try {
      model = pomReader.read(ReaderFactory.newXmlReader(new File(getBasedir(), "pom.xml")));
      setModel(model);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    setGroupId(model.getGroupId());
    setArtifactId(model.getArtifactId());
    setVersion(model.getVersion());
    setName(model.getName());
    setUrl(model.getUrl());
    setPackaging(model.getPackaging());

    Build build = new Build();
    build.setFinalName(model.getArtifactId());
    build.setDirectory(getBasedir() + "/target");
    build.setSourceDirectory(getBasedir() + "/src/main/java");
    build.setOutputDirectory(getBasedir() + "/target/classes");
    build.setTestSourceDirectory(getBasedir() + "/src/test/java");
    build.setTestOutputDirectory(getBasedir() + "/target/test-classes");
    setBuild(build);

    List compileSourceRoots = new ArrayList();
    compileSourceRoots.add(getBasedir() + "/src/main/java");
    setCompileSourceRoots(compileSourceRoots);

    List testCompileSourceRoots = new ArrayList();
    testCompileSourceRoots.add(getBasedir() + "/src/test/java");
    setTestCompileSourceRoots(testCompileSourceRoots);
  }

  /** Use of mockito artifact */
  @Override
  public Artifact getArtifact() {
    Artifact artifact = Mockito.mock(Artifact.class);
    when(artifact.getArtifactId()).thenReturn(getModel().getArtifactId());
    when(artifact.getGroupId()).thenReturn(getModel().getGroupId());
    when(artifact.getVersion()).thenReturn(getModel().getVersion());
    when(artifact.getVersionRange())
        .thenReturn(VersionRange.createFromVersion(getModel().getVersion()));
    return artifact;
  }
}
