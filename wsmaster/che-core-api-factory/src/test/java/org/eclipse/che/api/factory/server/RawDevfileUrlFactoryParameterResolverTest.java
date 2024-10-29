/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
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

import static java.lang.String.format;
import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.workspace.server.devfile.DevfileParser;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.URLFileContentProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(value = {MockitoTestNGListener.class})
public class RawDevfileUrlFactoryParameterResolverTest {

  private static final String DEVFILE =
      "" + "schemaVersion: 2.3.0\n" + "metadata:\n" + "  name: test\n";

  @Mock private URLFetcher urlFetcher;
  @Mock private DevfileParser devfileParser;

  @InjectMocks private RawDevfileUrlFactoryParameterResolver rawDevfileUrlFactoryParameterResolver;

  @Test
  @SuppressWarnings("unchecked")
  public void shouldFilterAndProvideOverrideParameters() throws Exception {
    URLFactoryBuilder urlFactoryBuilder = mock(URLFactoryBuilder.class);
    URLFetcher urlFetcher = mock(URLFetcher.class);

    RawDevfileUrlFactoryParameterResolver res =
        new RawDevfileUrlFactoryParameterResolver(urlFactoryBuilder, urlFetcher, devfileParser);

    Map<String, String> factoryParameters = new HashMap<>();
    factoryParameters.put(URL_PARAMETER_NAME, "http://myloc/devfile");
    factoryParameters.put("override.param.foo", "bar");
    factoryParameters.put("override.param.bar", "foo");
    factoryParameters.put("ignored.non-override.property", "baz");

    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    // when
    res.createFactory(factoryParameters);

    verify(urlFactoryBuilder)
        .createFactoryFromDevfile(
            any(RemoteFactoryUrl.class),
            any(URLFileContentProvider.class),
            captor.capture(),
            anyBoolean());
    Map<String, String> filteredOverrides = captor.getValue();
    assertEquals(2, filteredOverrides.size());
    assertEquals("bar", filteredOverrides.get("param.foo"));
    assertEquals("foo", filteredOverrides.get("param.bar"));
    assertFalse(filteredOverrides.containsKey("ignored.non-override.property"));
  }

  @Test(dataProvider = "invalidURLsProvider")
  public void shouldThrowExceptionOnInvalidURL(String url, String message) throws Exception {
    URLFactoryBuilder urlFactoryBuilder = mock(URLFactoryBuilder.class);
    URLFetcher urlFetcher = mock(URLFetcher.class);

    RawDevfileUrlFactoryParameterResolver res =
        new RawDevfileUrlFactoryParameterResolver(urlFactoryBuilder, urlFetcher, devfileParser);

    Map<String, String> factoryParameters = new HashMap<>();
    factoryParameters.put(URL_PARAMETER_NAME, url);

    // when
    try {
      res.createFactory(factoryParameters);
      fail("Exception is expected");
    } catch (BadRequestException e) {
      assertEquals(
          e.getMessage(),
          format(
              "Unable to process provided factory URL. Please check its validity and try again. Parser message: %s",
              message));
    }
  }

  @Test(dataProvider = "devfileUrls")
  public void shouldAcceptRawDevfileUrl(String url) {
    // when
    boolean result =
        rawDevfileUrlFactoryParameterResolver.accept(singletonMap(URL_PARAMETER_NAME, url));

    // then
    assertTrue(result);
  }

  @Test(dataProvider = "devfileUrlsWithoutExtension")
  public void shouldAcceptRawDevfileUrlWithoutExtension(String url) throws Exception {
    // given
    JsonNode jsonNode = mock(JsonNode.class);
    when(urlFetcher.fetch(eq(url))).thenReturn(DEVFILE);
    when(devfileParser.parseYamlRaw(eq(DEVFILE))).thenReturn(jsonNode);
    when(jsonNode.isEmpty()).thenReturn(false);

    // when
    boolean result =
        rawDevfileUrlFactoryParameterResolver.accept(singletonMap(URL_PARAMETER_NAME, url));

    // then
    assertTrue(result);
  }

  @Test
  public void shouldAcceptRawDevfileUrlWithYaml() throws Exception {
    // given
    JsonNode jsonNode = mock(JsonNode.class);
    String url = "https://host/path/devfile";
    when(urlFetcher.fetch(eq(url))).thenReturn(DEVFILE);
    when(devfileParser.parseYamlRaw(eq(DEVFILE))).thenReturn(jsonNode);
    when(jsonNode.isEmpty()).thenReturn(false);

    // when
    boolean result =
        rawDevfileUrlFactoryParameterResolver.accept(singletonMap(URL_PARAMETER_NAME, url));

    // then
    assertTrue(result);
  }

  @Test
  public void shouldNotAcceptPublicGitRepositoryUrl() throws Exception {
    // given
    JsonNode jsonNode = mock(JsonNode.class);
    String gitRepositoryUrl = "https://host/user/repo.git";
    when(urlFetcher.fetch(eq(gitRepositoryUrl))).thenReturn("unsupported content");
    when(devfileParser.parseYamlRaw(eq("unsupported content"))).thenReturn(jsonNode);
    when(jsonNode.isEmpty()).thenReturn(true);

    // when
    boolean result =
        rawDevfileUrlFactoryParameterResolver.accept(
            singletonMap(URL_PARAMETER_NAME, gitRepositoryUrl));

    // then
    assertFalse(result);
  }

  @Test
  public void shouldNotAcceptPrivateGitRepositoryUrl() throws Exception {
    // given
    String gitRepositoryUrl = "https://host/user/private-repo.git";
    when(urlFetcher.fetch(eq(gitRepositoryUrl))).thenThrow(new FileNotFoundException());

    // when
    boolean result =
        rawDevfileUrlFactoryParameterResolver.accept(
            singletonMap(URL_PARAMETER_NAME, gitRepositoryUrl));

    // then
    assertFalse(result);
  }

  @DataProvider(name = "invalidURLsProvider")
  private Object[][] invalidUrlsProvider() {
    return new Object[][] {
      {"C:\\Users\\aa\\bb\\XX\\", "unknown protocol: c"},
      {
        "https://github.com/ .git",
        "Illegal character in path at index 19: https://github.com/ .git"
      },
      {"unknown:///abc.dce", "unknown protocol: unknown"}
    };
  }

  @DataProvider(name = "devfileUrls")
  private Object[] devfileUrls() {
    return new String[] {
      "https://host/path/devfile.yaml",
      "https://host/path/.devfile.yaml",
      "https://host/path/any-name.yaml",
      "https://host/path/any-name.yml",
      "https://host/path/devfile.yaml?token=TOKEN123",
      "https://host/path/.devfile.yaml?token=TOKEN123",
      "https://host/path/any-name.yaml?token=TOKEN123",
      "https://host/path/any-name.yml?token=TOKEN123",
      "https://host/path/devfile.yaml?at=refs/heads/branch",
      "https://host/path/.devfile.yaml?at=refs/heads/branch",
      "https://host/path/any-name.yaml?at=refs/heads/branch",
      "https://host/path/any-name.yml?at=refs/heads/branch"
    };
  }

  @DataProvider(name = "devfileUrlsWithoutExtension")
  private Object[] devfileUrlsWithoutExtension() {
    return new String[] {"https://host/path/any-name", "https://host/path/any-name?token=TOKEN123"};
  }
}
