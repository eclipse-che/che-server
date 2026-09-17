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
package org.eclipse.che.api.factory.server.urlfactory;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.workspace.server.devfile.Constants.KUBERNETES_COMPONENT_TYPE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.rest.shared.dto.ExtendedError;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl.DevfileLocation;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.workspace.server.devfile.DevfileParser;
import org.eclipse.che.api.workspace.server.devfile.DevfileVersionDetector;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.RecipeImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.devfile.DevfileImpl;
import org.eclipse.che.api.workspace.server.model.impl.devfile.MetadataImpl;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Testing {@link URLFactoryBuilder}
 *
 * @author Florent Benoit
 * @author Max Shaposhnyk
 */
@Listeners(MockitoTestNGListener.class)
public class URLFactoryBuilderTest {

  private final String defaultEditor = "eclipse/che-theia/1.0.0";
  private final String defaultPlugin = "eclipse/che-machine-exec-plugin/0.0.1";

  /** Grab content of URLs */
  @Mock private URLFetcher urlFetcher;

  @Mock private DevfileParser devfileParser;

  @Mock private DevfileVersionDetector devfileVersionDetector;

  @Mock private FileContentProvider fileContentProvider;

  /** Tested instance. */
  private URLFactoryBuilder urlFactoryBuilder;

  @BeforeMethod
  public void setUp() throws IOException, DevfileException {
    this.urlFactoryBuilder =
        new URLFactoryBuilder(
            defaultEditor, defaultPlugin, true, devfileParser, devfileVersionDetector);
  }

  @Test
  public void checkWithCustomDevfileAndRecipe() throws Exception {

    DevfileImpl devfile = new DevfileImpl();
    WorkspaceConfigImpl workspaceConfigImpl = new WorkspaceConfigImpl();
    String myLocation = "http://foo-location/";
    RecipeImpl expectedRecipe =
        new RecipeImpl(KUBERNETES_COMPONENT_TYPE, "application/x-yaml", "content", "");
    EnvironmentImpl expectedEnv = new EnvironmentImpl(expectedRecipe, emptyMap());
    workspaceConfigImpl.setEnvironments(singletonMap("name", expectedEnv));
    workspaceConfigImpl.setDefaultEnv("name");

    when(devfileParser.parseYamlRaw(anyString()))
        .thenReturn(new ObjectNode(JsonNodeFactory.instance));
    when(fileContentProvider.fetchContent(anyString())).thenReturn("content");

    FactoryMetaDto factory =
        urlFactoryBuilder
            .createFactoryFromDevfile(
                new DefaultFactoryUrl().withDevfileFileLocation(myLocation).withUrl("url"),
                fileContentProvider,
                emptyMap(),
                false)
            .get();

    assertNotNull(factory);
    assertNull(factory.getSource());
    assertTrue(factory instanceof FactoryDevfileV2Dto);
  }

  @Test
  public void testDevfileV2() throws ApiException, DevfileException, IOException {
    String myLocation = "http://foo-location/";
    Map<String, Object> devfileAsMap = Map.of("hello", "there", "how", "are", "you", "?");

    JsonNode devfile = new ObjectNode(JsonNodeFactory.instance);
    when(devfileParser.parseYamlRaw(anyString())).thenReturn(devfile);
    when(devfileParser.convertYamlToMap(devfile)).thenReturn(devfileAsMap);
    when(fileContentProvider.fetchContent(anyString())).thenReturn("content");

    FactoryMetaDto factory =
        urlFactoryBuilder
            .createFactoryFromDevfile(
                new DefaultFactoryUrl().withDevfileFileLocation(myLocation).withUrl("url"),
                fileContentProvider,
                emptyMap(),
                false)
            .get();

    assertNotNull(factory);
    assertNull(factory.getSource());
    assertTrue(factory instanceof FactoryDevfileV2Dto);
    assertEquals(((FactoryDevfileV2Dto) factory).getDevfile(), devfileAsMap);
  }

  @Test
  public void testDevfileV2WithFilename() throws ApiException, DevfileException, IOException {
    String myLocation = "http://foo-location/";
    Map<String, Object> devfileAsMap = Map.of("hello", "there", "how", "are", "you", "?");

    JsonNode devfile = new ObjectNode(JsonNodeFactory.instance);
    when(devfileParser.parseYamlRaw(anyString())).thenReturn(devfile);
    when(devfileParser.convertYamlToMap(devfile)).thenReturn(devfileAsMap);
    when(fileContentProvider.fetchContent(anyString())).thenReturn("content");

    RemoteFactoryUrl githubLikeRemoteUrl =
        new RemoteFactoryUrl() {
          @Override
          public String getProviderName() {
            return null;
          }

          @Override
          public List<DevfileLocation> devfileFileLocations() {
            return Collections.singletonList(
                new DevfileLocation() {
                  @Override
                  public Optional<String> filename() {
                    return Optional.of("devfile.yaml");
                  }

                  @Override
                  public String location() {
                    return myLocation;
                  }
                });
          }

          @Override
          public String rawFileLocation(String filename) {
            return null;
          }

          @Override
          public String getHostName() {
            return null;
          }

          @Override
          public String getProviderUrl() {
            return null;
          }

          @Override
          public String getBranch() {
            return null;
          }

          @Override
          public Optional<String> getCredentials() {
            return Optional.empty();
          }

          @Override
          public void setDevfileFilename(String devfileName) {}
        };

    FactoryMetaDto factory =
        urlFactoryBuilder
            .createFactoryFromDevfile(githubLikeRemoteUrl, fileContentProvider, emptyMap(), false)
            .get();

    assertNotNull(factory);
    assertEquals(factory.getSource(), "devfile.yaml");
    assertTrue(factory instanceof FactoryDevfileV2Dto);
    assertEquals(((FactoryDevfileV2Dto) factory).getDevfile(), devfileAsMap);
  }

  @Test
  public void testDevfileSpecifyingFilename() throws ApiException, DevfileException, IOException {
    String myLocation = "http://foo-location/";
    Map<String, Object> devfileAsMap = Map.of("hello", "there", "how", "are", "you", "?");

    JsonNode devfile = new ObjectNode(JsonNodeFactory.instance);
    when(devfileParser.parseYamlRaw(anyString())).thenReturn(devfile);
    when(devfileParser.convertYamlToMap(devfile)).thenReturn(devfileAsMap);
    when(fileContentProvider.fetchContent(anyString())).thenReturn("content");

    RemoteFactoryUrl githubLikeRemoteUrl =
        new RemoteFactoryUrl() {

          private String devfileName = "default-devfile.yaml";

          @Override
          public String getProviderName() {
            return null;
          }

          @Override
          public List<DevfileLocation> devfileFileLocations() {
            return Collections.singletonList(
                new DevfileLocation() {
                  @Override
                  public Optional<String> filename() {
                    return Optional.of(devfileName);
                  }

                  @Override
                  public String location() {
                    return myLocation;
                  }
                });
          }

          @Override
          public String rawFileLocation(String filename) {
            return null;
          }

          @Override
          public String getHostName() {
            return null;
          }

          @Override
          public String getProviderUrl() {
            return null;
          }

          @Override
          public String getBranch() {
            return null;
          }

          @Override
          public Optional<String> getCredentials() {
            return Optional.empty();
          }

          @Override
          public void setDevfileFilename(String devfileName) {
            this.devfileName = devfileName;
          }
        };

    String pathToDevfile = "my-custom-devfile-path.yaml";
    Map<String, String> propertiesMap =
        singletonMap(URLFactoryBuilder.DEVFILE_FILENAME, pathToDevfile);
    FactoryMetaDto factory =
        urlFactoryBuilder
            .createFactoryFromDevfile(
                githubLikeRemoteUrl, fileContentProvider, propertiesMap, false)
            .get();

    assertNotNull(factory);
    // Check that we didn't fetch from default files but from the parameter
    assertEquals(factory.getSource(), pathToDevfile);
    assertTrue(factory instanceof FactoryDevfileV2Dto);
    assertEquals(((FactoryDevfileV2Dto) factory).getDevfile(), devfileAsMap);
  }

  @Test
  public void testShouldReturnV2WithDevworkspacesDisabled()
      throws ApiException, DevfileException, IOException {
    String myLocation = "http://foo-location/";
    Map<String, Object> devfileAsMap = Map.of("hello", "there", "how", "are", "you", "?");

    JsonNode devfile = new ObjectNode(JsonNodeFactory.instance);
    when(devfileParser.parseYamlRaw(anyString())).thenReturn(devfile);
    when(devfileParser.convertYamlToMap(devfile)).thenReturn(devfileAsMap);
    when(fileContentProvider.fetchContent(anyString())).thenReturn("content");

    URLFactoryBuilder localUrlFactoryBuilder =
        new URLFactoryBuilder(
            defaultEditor, defaultPlugin, false, devfileParser, devfileVersionDetector);

    FactoryMetaDto factory =
        localUrlFactoryBuilder
            .createFactoryFromDevfile(
                new DefaultFactoryUrl().withDevfileFileLocation(myLocation).withUrl("url"),
                fileContentProvider,
                emptyMap(),
                false)
            .get();
    assertNotNull(factory);
    assertTrue(factory instanceof FactoryDevfileV2Dto);
    assertEquals(((FactoryDevfileV2Dto) factory).getDevfile(), devfileAsMap);
  }

  @DataProvider
  public Object[][] devfiles() {
    final String NAME = "name";
    final String GEN_NAME = "genName";

    DevfileImpl devfileTemplate = new DevfileImpl();
    devfileTemplate.setApiVersion("1.0.0");
    MetadataImpl metadataTemplate = new MetadataImpl();

    metadataTemplate.setName(NAME);
    devfileTemplate.setMetadata(metadataTemplate);
    DevfileImpl justName = new DevfileImpl(devfileTemplate);

    metadataTemplate.setName(null);
    metadataTemplate.setGenerateName(GEN_NAME);
    devfileTemplate.setMetadata(metadataTemplate);
    DevfileImpl justGenerateName = new DevfileImpl(devfileTemplate);

    metadataTemplate.setName(NAME);
    metadataTemplate.setGenerateName(GEN_NAME);
    devfileTemplate.setMetadata(metadataTemplate);
    DevfileImpl bothNames = new DevfileImpl(devfileTemplate);

    return new Object[][] {{justName, NAME}, {justGenerateName, GEN_NAME}, {bothNames, GEN_NAME}};
  }

  @Test(dataProvider = "devfileExceptions")
  public void checkCorrectExceptionThrownDependingOnCause(
      Throwable cause,
      Class expectedClass,
      String expectedMessage,
      Map<String, String> expectedAttributes)
      throws IOException, DevfileException {
    DefaultFactoryUrl defaultFactoryUrl = mock(DefaultFactoryUrl.class);
    FileContentProvider fileContentProvider = mock(FileContentProvider.class);
    when(defaultFactoryUrl.devfileFileLocations())
        .thenReturn(
            singletonList(
                new DevfileLocation() {
                  @Override
                  public Optional<String> filename() {
                    return Optional.empty();
                  }

                  @Override
                  public String location() {
                    return "http://foo.bar/anything";
                  }
                }));

    when(fileContentProvider.fetchContent(anyString()))
        .thenThrow(new DevfileException(expectedMessage, cause));

    // when
    try {
      urlFactoryBuilder.createFactoryFromDevfile(
          defaultFactoryUrl, fileContentProvider, emptyMap(), false);
    } catch (ApiException e) {
      assertTrue(e.getClass().isAssignableFrom(expectedClass));
      assertEquals(e.getMessage(), expectedMessage);
      if ("SCM Authentication required".equals(e.getMessage()))
        assertEquals(((ExtendedError) e.getServiceError()).getAttributes(), expectedAttributes);
    }
  }

  @Test(
      expectedExceptions = ApiException.class,
      expectedExceptionsMessageRegExp = "Could not reach devfile at location")
  public void shouldThrowErrorOnUnsupportedDevfileContent()
      throws ApiException, DevfileException, IOException {
    JsonNode jsonNode = mock(JsonNode.class);
    when(fileContentProvider.fetchContent(eq("location"))).thenReturn("unsupported content");
    when(devfileParser.parseYamlRaw(eq("unsupported content"))).thenReturn(jsonNode);
    when(devfileVersionDetector.devfileVersion(eq(jsonNode))).thenThrow(new DevfileException(""));
    urlFactoryBuilder.createFactoryFromDevfile(
        new DefaultFactoryUrl().withDevfileFileLocation("location"),
        fileContentProvider,
        emptyMap(),
        false);
  }

  private static final String TEST_DEVFILE_LOCATION = "http://repo/raw/devfile.yaml";
  private static final String TEST_DEVCONTAINER_LOCATION =
      "http://repo/raw/.devcontainer/devcontainer.json";
  private static final String TEST_DEVCONTAINER_LOCATION_ROOT =
      "http://repo/raw/.devcontainer.json";

  private static RemoteFactoryUrl testRemoteUrl() {
    return testRemoteUrl(Optional.empty());
  }

  private static RemoteFactoryUrl testRemoteUrl(Optional<String> credentials) {
    return new RemoteFactoryUrl() {
      @Override
      public String getProviderName() {
        return "test";
      }

      @Override
      public List<DevfileLocation> devfileFileLocations() {
        return singletonList(
            new DevfileLocation() {
              @Override
              public Optional<String> filename() {
                return Optional.of("devfile.yaml");
              }

              @Override
              public String location() {
                return TEST_DEVFILE_LOCATION;
              }
            });
      }

      @Override
      public String rawFileLocation(String filename) {
        return "http://repo/raw/" + filename;
      }

      @Override
      public String getHostName() {
        return "repo";
      }

      @Override
      public String getProviderUrl() {
        return "http://repo";
      }

      @Override
      public String getBranch() {
        return null;
      }

      @Override
      public Optional<String> getCredentials() {
        return credentials;
      }

      @Override
      public void setDevfileFilename(String devfileName) {}
    };
  }

  private void stubDevcontainerTemplateParse() throws DevfileException {
    Map<String, Object> templateAdditions =
        Map.of("commands", List.of(Map.of("id", "start-devcontainer")));
    JsonNode templateNode = new ObjectNode(JsonNodeFactory.instance);
    when(devfileParser.parseYamlRaw(anyString())).thenReturn(templateNode);
    when(devfileParser.convertYamlToMap(templateNode)).thenReturn(templateAdditions);
  }

  @Test
  public void testDevfileFoundSoDevcontainerProbeNeverRuns() throws Exception {
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION))).thenReturn("devfile content");
    when(devfileParser.parseYamlRaw(eq("devfile content")))
        .thenReturn(new ObjectNode(JsonNodeFactory.instance));
    when(devfileParser.convertYamlToMap(org.mockito.ArgumentMatchers.<JsonNode>any()))
        .thenReturn(Map.of("schemaVersion", "2.2.0"));

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), false);

    assertTrue(result.isPresent());
    assertEquals(result.get().getSource(), "devfile.yaml");
    verify(fileContentProvider, never()).fetchContent(eq(TEST_DEVCONTAINER_LOCATION));
  }

  @Test
  public void testDevcontainerDetectedWhenNoDevfile() throws Exception {
    stubDevcontainerTemplateParse();
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION)))
        .thenReturn("{\"name\": \"test\"}");

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), false);

    assertTrue(result.isPresent());
    assertEquals(result.get().getSource(), ".devcontainer/devcontainer.json");
    assertTrue(result.get() instanceof FactoryDevfileV2Dto);
    Map<String, Object> devfile = ((FactoryDevfileV2Dto) result.get()).getDevfile();
    assertEquals(devfile.get("schemaVersion"), "2.3.0");
    assertEquals(devfile.get("commands"), List.of(Map.of("id", "start-devcontainer")));
  }

  @Test
  public void testDevcontainerDetectedAtRootWhenNestedMissing() throws Exception {
    stubDevcontainerTemplateParse();
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION_ROOT)))
        .thenReturn("{\"name\": \"test\"}");

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), false);

    assertTrue(result.isPresent());
    assertEquals(result.get().getSource(), ".devcontainer.json");
  }

  @Test
  public void testDevcontainerDetectedWithoutAuthentication() throws Exception {
    stubDevcontainerTemplateParse();
    when(fileContentProvider.fetchContentWithoutAuthentication(eq(TEST_DEVFILE_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContentWithoutAuthentication(eq(TEST_DEVCONTAINER_LOCATION)))
        .thenReturn("{\"name\": \"test\"}");

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), true);

    assertTrue(result.isPresent());
    assertEquals(result.get().getSource(), ".devcontainer/devcontainer.json");
    verify(fileContentProvider, never()).fetchContent(anyString());
  }

  @Test
  public void testDevcontainerDetectedWithCredentials() throws Exception {
    stubDevcontainerTemplateParse();
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION), eq("user:token")))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION), eq("user:token")))
        .thenReturn("{\"name\": \"test\"}");

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(Optional.of("user:token")), fileContentProvider, emptyMap(), false);

    assertTrue(result.isPresent());
    assertEquals(result.get().getSource(), ".devcontainer/devcontainer.json");
  }

  @Test(
      expectedExceptions = UnauthorizedException.class,
      expectedExceptionsMessageRegExp = "SCM Authentication required")
  public void testDevcontainerProbeRethrowsUnauthorized() throws Exception {
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION)))
        .thenThrow(
            new DevfileException(
                "auth required",
                new ScmUnauthorizedException("foo", "github", "2.0", "http://oauth.example")));

    urlFactoryBuilder.createFactoryFromDevfile(
        testRemoteUrl(), fileContentProvider, emptyMap(), false);
  }

  @Test
  public void testNoDevfileNoDevcontainerReturnsEmpty() throws Exception {
    when(fileContentProvider.fetchContent(anyString())).thenThrow(new IOException("not found"));

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), false);

    assertFalse(result.isPresent());
  }

  @Test
  public void testDevcontainerFetchIOExceptionReturnsEmpty() throws Exception {
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION_ROOT)))
        .thenThrow(new IOException("not found"));

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), false);

    assertFalse(result.isPresent());
  }

  @Test
  public void testDevcontainerEmptyContentReturnsEmpty() throws Exception {
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION))).thenReturn("");
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION_ROOT))).thenReturn("");

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), false);

    assertFalse(result.isPresent());
  }

  @Test
  public void testDevcontainerHtmlContentReturnsEmpty() throws Exception {
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION)))
        .thenReturn("<!DOCTYPE html><html><body>Access denied</body></html>");
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION_ROOT)))
        .thenReturn("<!DOCTYPE html><html><body>Access denied</body></html>");

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), false);

    assertFalse(result.isPresent());
  }

  @Test
  public void testDevcontainerJsoncWithLeadingCommentDetected() throws Exception {
    stubDevcontainerTemplateParse();
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION)))
        .thenReturn("// This is a JSONC comment\n{\"name\": \"test\"}");

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), false);

    assertTrue(result.isPresent());
    assertEquals(result.get().getSource(), ".devcontainer/devcontainer.json");
    assertTrue(result.get() instanceof FactoryDevfileV2Dto);
  }

  @Test
  public void testDevcontainerJsoncWithBlockCommentDetected() throws Exception {
    stubDevcontainerTemplateParse();
    when(fileContentProvider.fetchContent(eq(TEST_DEVFILE_LOCATION)))
        .thenThrow(new IOException("not found"));
    when(fileContentProvider.fetchContent(eq(TEST_DEVCONTAINER_LOCATION)))
        .thenReturn("/*\n * Generated config\n */\n{\"name\": \"test\"}");

    Optional<FactoryMetaDto> result =
        urlFactoryBuilder.createFactoryFromDevfile(
            testRemoteUrl(), fileContentProvider, emptyMap(), false);

    assertTrue(result.isPresent());
    assertEquals(result.get().getSource(), ".devcontainer/devcontainer.json");
  }

  @Test
  public void testLooksLikeJson() {
    assertFalse(URLFactoryBuilder.looksLikeJson(null));
    assertFalse(URLFactoryBuilder.looksLikeJson(""));
    assertFalse(URLFactoryBuilder.looksLikeJson("   "));
    assertFalse(URLFactoryBuilder.looksLikeJson("<!DOCTYPE html>"));
    assertTrue(URLFactoryBuilder.looksLikeJson("{\"name\": \"test\"}"));
    assertTrue(URLFactoryBuilder.looksLikeJson("\uFEFF{\"name\": \"test\"}"));
    assertTrue(URLFactoryBuilder.looksLikeJson("// comment\n{\"name\": \"test\"}"));
    assertTrue(URLFactoryBuilder.looksLikeJson("/* comment */ {\"name\": \"test\"}"));
    assertTrue(URLFactoryBuilder.looksLikeJson("/*\n * generated\n */\n{\"name\": \"test\"}"));
    assertFalse(URLFactoryBuilder.looksLikeJson("/* unterminated"));
  }

  @Test
  public void testDevcontainerTemplateEncodesScriptWithoutDwoBashDefaults() {
    String template = URLFactoryBuilder.getDevcontainerDevfileTemplate();
    assertFalse(template.contains("__START_DEVCONTAINER_B64__"));
    assertFalse(template.contains("${PROJECTS_ROOT:-"));
    assertFalse(template.contains("${PROJECT_SOURCE"));
    assertTrue(template.contains("id: start-devcontainer"));
    assertTrue(template.contains("id: rebuild-devcontainer"));
    assertTrue(template.contains("id: rebuild-devcontainer-no-cache"));
    assertTrue(template.contains("id: show-devcontainer-log"));
    assertTrue(template.contains("id: clean-devcontainer-images"));
    assertTrue(template.contains("workingDir: ${PROJECTS_ROOT}"));

    String marker = "base64 -d >/tmp/start-devcontainer.sh <<'DEVCONTAINER_SCRIPT_B64'\n";
    int encodedAt = template.indexOf(marker);
    assertTrue(encodedAt >= 0);
    int encodedStart = encodedAt + marker.length();
    int encodedEnd = template.indexOf("DEVCONTAINER_SCRIPT_B64", encodedStart);
    assertTrue(encodedEnd > encodedStart);
    String encoded = template.substring(encodedStart, encodedEnd).trim();
    String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    assertTrue(decoded.startsWith("#!/usr/bin/env bash"));
    assertTrue(decoded.contains("${PROJECTS_ROOT:-/projects}"));
    assertTrue(decoded.contains("${DEVCONTAINER_CLI_VERSION:-0.89.0}"));
  }

  @DataProvider
  public static Object[][] devfileExceptions() {
    return new Object[][] {
      {
        new ScmCommunicationException("foo"),
        ServerException.class,
        "There is an error happened when communicate with SCM server. Error message: foo",
        null
      },
      {
        new UnknownScmProviderException("foo", "bar"),
        ServerException.class,
        "Provided location is unknown or misconfigured on the server side. Error message: foo",
        null
      },
      {
        new ScmUnauthorizedException("foo", "bitbucket", "1.0", "http://foo.bar"),
        UnauthorizedException.class,
        "SCM Authentication required",
        Map.of(
            "oauth_version",
            "1.0",
            "oauth_authentication_url",
            "http://foo.bar",
            "oauth_provider",
            "bitbucket")
      }
    };
  }
}
