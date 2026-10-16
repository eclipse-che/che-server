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

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.api.factory.server.ApiExceptionMapper.toApiException;
import static org.eclipse.che.api.factory.server.scm.exception.ExceptionMessages.getDevfileConnectionErrorMessage;
import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.DEFAULT_DEVFILE;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl.DevfileLocation;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.workspace.server.devfile.DevfileParser;
import org.eclipse.che.api.workspace.server.devfile.DevfileVersionDetector;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;
import org.eclipse.che.commons.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle the creation of some elements used inside a {@link FactoryDto}.
 *
 * @author Florent Benoit
 * @author Max Shaposhnyk
 */
@Singleton
public class URLFactoryBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(URLFactoryBuilder.class);

  public static final String DEVFILE_FILENAME = "devfileFilename";

  private static final String[] DEVCONTAINER_LOCATIONS = {
    ".devcontainer/devcontainer.json", ".devcontainer.json"
  };

  private static final String DEVCONTAINER_SCRIPT_B64_PLACEHOLDER = "__START_DEVCONTAINER_B64__";

  // Loaded lazily so a broken or missing template disables devcontainer detection instead of
  // failing class initialization and with it every factory resolution.
  private static final Supplier<Optional<String>> DEVCONTAINER_DEVFILE_TEMPLATE =
      Suppliers.memoize(URLFactoryBuilder::loadDevcontainerDevfileTemplate);

  private final String defaultCheEditor;
  private final String defaultChePlugins;

  private final boolean devWorskspacesEnabled;
  private final DevfileParser devfileParser;
  private final DevfileVersionDetector devfileVersionDetector;

  @Inject
  public URLFactoryBuilder(
      @Named("che.factory.default_editor") String defaultCheEditor,
      @Nullable @Named("che.factory.default_plugins") String defaultChePlugins,
      @Named("che.devworkspaces.enabled") boolean devWorskspacesEnabled,
      DevfileParser devfileParser,
      DevfileVersionDetector devfileVersionDetector) {
    this.defaultCheEditor = defaultCheEditor;
    this.defaultChePlugins = defaultChePlugins;
    this.devWorskspacesEnabled = devWorskspacesEnabled;
    this.devfileParser = devfileParser;
    this.devfileVersionDetector = devfileVersionDetector;
  }

  /**
   * Build a factory using the provided devfile. Allows to override devfile properties using
   * specially constructed map {@see DevfileManager#parseYaml(String, Map)}.
   *
   * <p>We want factory to never fail due to name collision. Taking `generateName` with precedence.
   * <br>
   * If devfile has only `name`, we convert it to `generateName`. <br>
   * If devfile has `name` and `generateName`, we remove `name` and use just `generateName`. <br>
   * If devfile has `generateName`, we use that.
   *
   * @param remoteFactoryUrl parsed factory URL object
   * @param fileContentProvider service-specific devfile related file content provider
   * @param overrideProperties map of overridden properties to apply in devfile
   * @return a factory or null if devfile is not found
   */
  public Optional<FactoryMetaDto> createFactoryFromDevfile(
      RemoteFactoryUrl remoteFactoryUrl,
      FileContentProvider fileContentProvider,
      Map<String, String> overrideProperties,
      boolean skipAuthentication)
      throws ApiException {
    String devfileYamlContent;
    boolean devfileFilenameOverridden = overrideProperties.containsKey(DEVFILE_FILENAME);
    if (devfileFilenameOverridden) {
      remoteFactoryUrl.setDevfileFilename(overrideProperties.get(DEVFILE_FILENAME));
    }

    for (DevfileLocation location : remoteFactoryUrl.devfileFileLocations()) {
      String devfileLocation = location.location();
      try {
        devfileYamlContent =
            fetchContent(
                remoteFactoryUrl, fileContentProvider, devfileLocation, skipAuthentication);
      } catch (IOException ex) {
        // try next location
        LOG.debug(
            "Unreachable devfile location met: {}. Error is: {}", devfileLocation, ex.getMessage());
        continue;
      } catch (DevfileException e) {
        LOG.debug("Unexpected devfile exception: {}", e.getMessage());
        throw e.getCause() instanceof ScmUnauthorizedException
            ? toApiException(e, location)
            : new ApiException(e.getMessage());
      }
      if (isNullOrEmpty(devfileYamlContent)) {
        return Optional.empty();
      }
      try {
        JsonNode parsedDevfile = devfileParser.parseYamlRaw(devfileYamlContent);
        // We might have an html content in the parsed devfile, in case if the access is restricted,
        // or if the URL points to a wrong resource.
        try {
          devfileVersionDetector.devfileVersion(parsedDevfile);
        } catch (DevfileException e) {
          throw new ApiException(getDevfileConnectionErrorMessage(devfileLocation));
        }
        return Optional.of(createFactory(parsedDevfile, location));
      } catch (DevfileException e) {
        throw toApiException(e, location);
      }
    }

    // An explicitly requested devfile that is missing should fall back to the default devfile, not
    // to a devcontainer the user did not ask for.
    if (devfileFilenameOverridden) {
      return Optional.empty();
    }
    return createFactoryFromDevcontainer(remoteFactoryUrl, fileContentProvider, skipAuthentication);
  }

  /**
   * Probes the repository for a devcontainer.json and returns a factory with a devfile that builds
   * and runs it as a nested container. The probe is best effort: any failure other than missing SCM
   * authorization falls through to the default factory.
   */
  private Optional<FactoryMetaDto> createFactoryFromDevcontainer(
      RemoteFactoryUrl remoteFactoryUrl,
      FileContentProvider fileContentProvider,
      boolean skipAuthentication)
      throws ApiException {
    for (String devcontainerPath : DEVCONTAINER_LOCATIONS) {
      String devcontainerLocation = remoteFactoryUrl.rawFileLocation(devcontainerPath);
      // Providers without a raw file endpoint (e.g. git-ssh returns the bare filename) cannot be
      // probed.
      if (!isAbsoluteUri(devcontainerLocation)) {
        return Optional.empty();
      }
      String devcontainerContent;
      try {
        devcontainerContent =
            fetchContent(
                remoteFactoryUrl, fileContentProvider, devcontainerLocation, skipAuthentication);
      } catch (IOException ex) {
        LOG.debug(
            "Factory devcontainer fetch failed: {}. Error is: {}",
            devcontainerLocation,
            ex.getMessage());
        continue;
      } catch (DevfileException e) {
        if (e.getCause() instanceof ScmUnauthorizedException) {
          throw toApiException(e);
        }
        LOG.debug(
            "Factory devcontainer probe failed: {}. Error is: {}",
            devcontainerLocation,
            e.getMessage());
        continue;
      }

      if (!looksLikeJson(devcontainerContent)) {
        continue;
      }

      Optional<String> template = DEVCONTAINER_DEVFILE_TEMPLATE.get();
      if (template.isEmpty()) {
        return Optional.empty();
      }
      try {
        JsonNode additions = devfileParser.parseYamlRaw(template.get());
        Map<String, Object> devfileMap = new HashMap<>(DEFAULT_DEVFILE);
        devfileMap.putAll(devfileParser.convertYamlToMap(additions));
        return Optional.of(
            newDto(FactoryDevfileV2Dto.class)
                .withV(CURRENT_VERSION)
                .withDevfile(devfileMap)
                .withSource(devcontainerPath));
      } catch (DevfileException e) {
        LOG.error("Failed to parse devcontainer devfile template", e);
        return Optional.empty();
      }
    }

    return Optional.empty();
  }

  private static boolean isAbsoluteUri(String location) {
    if (isNullOrEmpty(location)) {
      return false;
    }
    try {
      return new URI(location).isAbsolute();
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static String fetchContent(
      RemoteFactoryUrl remoteFactoryUrl,
      FileContentProvider fileContentProvider,
      String location,
      boolean skipAuthentication)
      throws IOException, DevfileException {
    Optional<String> credentialsOptional = remoteFactoryUrl.getCredentials();
    if (skipAuthentication) {
      return fileContentProvider.fetchContentWithoutAuthentication(location);
    } else if (credentialsOptional.isPresent()) {
      return fileContentProvider.fetchContent(location, credentialsOptional.get());
    }
    return fileContentProvider.fetchContent(location);
  }

  /**
   * Converts given devfile json into factory.
   *
   * @param location devfile's location
   * @return new factory created from the given devfile
   */
  private FactoryMetaDto createFactory(JsonNode devfileJson, DevfileLocation location) {
    return newDto(FactoryDevfileV2Dto.class)
        .withV(CURRENT_VERSION)
        .withDevfile(devfileParser.convertYamlToMap(devfileJson))
        .withSource(location.filename().isPresent() ? location.filename().get() : null);
  }

  /**
   * Cheap probe: returns true when content is non-empty and starts with '{' after skipping
   * whitespace and JSONC comments. Does not parse the file.
   */
  static boolean looksLikeJson(String content) {
    if (isNullOrEmpty(content)) {
      return false;
    }
    int i = 0;
    int n = content.length();
    if (content.charAt(0) == '\uFEFF') {
      i = 1;
    }
    while (i < n) {
      char c = content.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '/' && i + 1 < n) {
        char next = content.charAt(i + 1);
        if (next == '/') {
          int newline = content.indexOf('\n', i);
          if (newline < 0) {
            return false;
          }
          i = newline + 1;
          continue;
        }
        if (next == '*') {
          int end = content.indexOf("*/", i + 2);
          if (end < 0) {
            return false;
          }
          i = end + 2;
          continue;
        }
      }
      return c == '{';
    }
    return false;
  }

  private static Optional<String> loadDevcontainerDevfileTemplate() {
    try {
      String script = loadClasspathResource("/start-devcontainer.sh");
      String encodedScript =
          Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8));
      String template = loadClasspathResource("/devcontainer-devfile-template.yaml");
      if (!template.contains(DEVCONTAINER_SCRIPT_B64_PLACEHOLDER)) {
        throw new IOException(
            "devcontainer-devfile-template.yaml is missing " + DEVCONTAINER_SCRIPT_B64_PLACEHOLDER);
      }
      return Optional.of(template.replace(DEVCONTAINER_SCRIPT_B64_PLACEHOLDER, encodedScript));
    } catch (IOException e) {
      LOG.error("Failed to load devcontainer devfile template; devcontainer detection disabled", e);
      return Optional.empty();
    }
  }

  private static String loadClasspathResource(String name) throws IOException {
    try (InputStream is = URLFactoryBuilder.class.getResourceAsStream(name)) {
      if (is == null) {
        throw new IOException(name + " not found on classpath");
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @VisibleForTesting
  static String getDevcontainerDevfileTemplate() {
    return DEVCONTAINER_DEVFILE_TEMPLATE.get().orElse(null);
  }
}
