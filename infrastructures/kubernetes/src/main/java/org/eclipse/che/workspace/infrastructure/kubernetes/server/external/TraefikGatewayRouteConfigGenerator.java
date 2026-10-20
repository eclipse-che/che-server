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
package org.eclipse.che.workspace.infrastructure.kubernetes.server.external;

import static org.eclipse.che.api.core.model.workspace.config.ServerConfig.SERVICE_NAME_ATTRIBUTE;
import static org.eclipse.che.api.core.model.workspace.config.ServerConfig.SERVICE_PORT_ATTRIBUTE;
import static tools.jackson.dataformat.yaml.YAMLWriteFeature.WRITE_DOC_START_MARKER;

import com.google.common.base.Strings;
import io.fabric8.kubernetes.api.model.ConfigMap;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.che.api.workspace.server.model.impl.ServerConfigImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.workspace.infrastructure.kubernetes.Annotations;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.dataformat.yaml.YAMLFactory;

/**
 * Config generator for Traefik Gateway.
 *
 * <p>Content of single service configuration looks like this:
 *
 * <pre>
 * http:
 *   routers:
 *     {name}:
 *       rule: "PathPrefix(`{path}`)"
 *       service: {name}
 *       middlewares:
 *       - "{name}"
 *       priority: 100
 *   services:
 *     {name}:
 *       loadBalancer:
 *         servers:
 *         - url: '{serviceUrl}'
 *   middlewares:
 *     {name}:
 *       stripPrefix:
 *         prefixes:
 *         - "{GatewayRouteConfig#routePath}"
 * </pre>
 */
public class TraefikGatewayRouteConfigGenerator implements GatewayRouteConfigGenerator {

  private static final String SERVICE_URL_FORMAT_WITH_CLUSTER_DOMAIN = "http://%s.%s.svc.%s:%s";
  private static final String SERVICE_URL_FORMAT_WITHOUT_CLUSTER_DOMAIN = "http://%s.%s.svc:%s";

  private final String clusterDomain;

  public TraefikGatewayRouteConfigGenerator(@Nullable String clusterDomain) {
    this.clusterDomain = clusterDomain;
  }

  private final Map<String, ConfigMap> routeConfigs = new HashMap<>();

  @Override
  public void addRouteConfig(String name, ConfigMap routeConfig) {
    this.routeConfigs.put(name, routeConfig);
  }

  /**
   * Generates configuration for all configs added by {@link
   * TraefikGatewayRouteConfigGenerator#addRouteConfig(String, ConfigMap)} so far. It does not
   * change them, so this method can be used repeatedly.
   *
   * <p>Returned {@code Map<String, String>} has keys created from {@code name} parameter of {@link
   * TraefikGatewayRouteConfigGenerator#addRouteConfig(String, ConfigMap)} + '.yml' suffix. Values
   * are full configuration for single gateway route. This map is suppose to be directly used as
   * {@link ConfigMap}'s data.
   *
   * @return map with added routes configurations
   */
  @Override
  public Map<String, String> generate(String namespace) throws InfrastructureException {
    Map<String, String> cmData = new HashMap<>();
    for (Entry<String, ConfigMap> routeConfig : routeConfigs.entrySet()) {
      Map<String, ServerConfigImpl> servers =
          new Annotations.Deserializer(routeConfig.getValue().getMetadata().getAnnotations())
              .servers();
      if (servers.size() != 1) {
        throw new InfrastructureException(
            "Expected exactly 1 server [" + routeConfig.getValue().toString() + "]");
      }
      ServerConfigImpl server = servers.get(servers.keySet().iterator().next());
      String serviceName = server.getAttributes().get(SERVICE_NAME_ATTRIBUTE);
      String servicePort = server.getAttributes().get(SERVICE_PORT_ATTRIBUTE);

      String traefikRouteConfig =
          generate(
              routeConfig.getKey(),
              createServiceUrl(serviceName, servicePort, namespace),
              server.getEndpointOrigin());
      cmData.put(routeConfig.getKey() + ".yml", traefikRouteConfig);
    }
    return cmData;
  }

  /**
   * Generates Traefik specific configuration for single service.
   *
   * @param name name of the service
   * @param serviceUrl url of service we want to route to
   * @param path path to route and strip
   * @return traefik service route config
   */
  private String generate(String name, String serviceUrl, String path)
      throws InfrastructureException {
    StringWriter sw = new StringWriter();
    try {
      JsonGenerator generator =
          YAMLFactory.builder().disable(WRITE_DOC_START_MARKER).build().createGenerator(sw);

      generator.writeStartObject();
      generator.writeName("http");
      generator.writeStartObject();

      generator.writeName("routers");
      generateRouters(generator, name, path);

      generator.writeName("services");
      generateServices(generator, name, serviceUrl);

      generator.writeName("middlewares");
      generateMiddlewares(generator, name, path);

      generator.writeEndObject();
      generator.writeEndObject();

      generator.flush();

      return sw.toString();
    } catch (JacksonException e) {
      throw new InfrastructureException(e);
    }
  }

  /**
   * generates Routers part of Traefik config
   *
   * <pre>
   * {name}:
   *   rule: "PathPrefix(`{path}`)"
   *   service: "{name}"
   *   middlewares:
   *   - "{name}"
   *   priority: 100
   * </pre>
   */
  private void generateRouters(JsonGenerator generator, String name, String path) {
    generator.writeStartObject();
    generator.writeName(name);
    generator.writeStartObject();
    generator.writeName("rule");
    generator.writeString("PathPrefix(`" + path + "`)");
    generator.writeName("service");
    generator.writeString(name);
    generator.writeName("middlewares");
    generator.writeStartArray();
    generator.writeString(name);
    generator.writeEndArray();
    generator.writeName("priority");
    generator.writeNumber(100);
    generator.writeEndObject();
    generator.writeEndObject();
  }

  /**
   * generates Services part of Traefik config
   *
   * <pre>
   * {name}:
   *   loadBalancer:
   *     servers:
   *     - url: "{serviceUrl}"
   * </pre>
   */
  private void generateServices(JsonGenerator generator, String name, String serviceUrl) {
    generator.writeStartObject();
    generator.writeName(name);
    generator.writeStartObject();
    generator.writeName("loadBalancer");
    generator.writeStartObject();
    generator.writeName("servers");
    generator.writeStartArray();
    generator.writeStartObject();
    generator.writeName("url");
    generator.writeString(serviceUrl);
    generator.writeEndObject();
    generator.writeEndArray();
    generator.writeEndObject();
    generator.writeEndObject();
    generator.writeEndObject();
  }

  /**
   * generates Middlewares part of Traefik config
   *
   * <pre>
   * {name}:
   *   stripPrefix:
   *     prefixes:
   *     - "{path}"
   * </pre>
   */
  private void generateMiddlewares(JsonGenerator generator, String name, String path) {
    generator.writeStartObject();
    generator.writeName(name);
    generator.writeStartObject();
    generator.writeName("stripPrefix");
    generator.writeStartObject();
    generator.writeName("prefixes");
    generator.writeStartArray();
    generator.writeString(path);
    generator.writeEndArray();
    generator.writeEndObject();
    generator.writeEndObject();
    generator.writeEndObject();
  }

  private String createServiceUrl(String serviceName, String servicePort, String serviceNamespace) {
    if (Strings.isNullOrEmpty(clusterDomain)) {
      return String.format(
          SERVICE_URL_FORMAT_WITHOUT_CLUSTER_DOMAIN, serviceName, serviceNamespace, servicePort);
    } else {
      return String.format(
          SERVICE_URL_FORMAT_WITH_CLUSTER_DOMAIN,
          serviceName,
          serviceNamespace,
          clusterDomain,
          servicePort);
    }
  }
}
