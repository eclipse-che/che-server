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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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

  private static final String DEVCONTAINER_DEVFILE_TEMPLATE;

  static {
    try (InputStream is =
        URLFactoryBuilder.class.getResourceAsStream("/devcontainer-devfile-template.yaml")) {
      if (is == null) {
        throw new IOException("devcontainer-devfile-template.yaml not found on classpath");
      }
      DEVCONTAINER_DEVFILE_TEMPLATE = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load devcontainer devfile template", e);
    }
  }

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

    // Apply the new devfile name to look for
    if (overrideProperties.containsKey(DEVFILE_FILENAME)) {
      remoteFactoryUrl.setDevfileFilename(overrideProperties.get(DEVFILE_FILENAME));
    }

    for (DevfileLocation location : remoteFactoryUrl.devfileFileLocations()) {
      String devfileLocation = location.location();
      try {
        Optional<String> credentialsOptional = remoteFactoryUrl.getCredentials();
        if (skipAuthentication) {
          devfileYamlContent =
              fileContentProvider.fetchContentWithoutAuthentication(devfileLocation);
        } else if (credentialsOptional.isPresent()) {
          devfileYamlContent =
              fileContentProvider.fetchContent(devfileLocation, credentialsOptional.get());
        } else {
          devfileYamlContent = fileContentProvider.fetchContent(devfileLocation);
        }
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

    // No devfile found — probe for devcontainer.json
    for (String devcontainerPath : DEVCONTAINER_LOCATIONS) {
      String devcontainerLocation = remoteFactoryUrl.rawFileLocation(devcontainerPath);
      if (devcontainerLocation == null) {
        break;
      }
      try {
        Optional<String> credentialsOptional = remoteFactoryUrl.getCredentials();
        if (skipAuthentication) {
          fileContentProvider.fetchContentWithoutAuthentication(devcontainerLocation);
        } else if (credentialsOptional.isPresent()) {
          fileContentProvider.fetchContent(devcontainerLocation, credentialsOptional.get());
        } else {
          fileContentProvider.fetchContent(devcontainerLocation);
        }
      } catch (IOException ex) {
        LOG.debug("No devcontainer at: {}. Error: {}", devcontainerLocation, ex.getMessage());
        continue;
      } catch (DevfileException e) {
        LOG.debug("Unexpected exception probing devcontainer: {}", e.getMessage());
        continue;
      }

      LOG.info("Devcontainer detected at {}; generating devfile", devcontainerLocation);
      try {
        JsonNode additions = devfileParser.parseYamlRaw(DEVCONTAINER_DEVFILE_TEMPLATE);
        Map<String, Object> devfileMap = new HashMap<>(DEFAULT_DEVFILE);
        devfileMap.putAll(devfileParser.convertYamlToMap(additions));
        return Optional.of(
            newDto(FactoryDevfileV2Dto.class)
                .withV(CURRENT_VERSION)
                .withDevfile(devfileMap)
                .withSource(devcontainerPath));
      } catch (DevfileException e) {
        LOG.error("Failed to parse devcontainer devfile template", e);
        break;
      }
    }

    return Optional.empty();
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
}
