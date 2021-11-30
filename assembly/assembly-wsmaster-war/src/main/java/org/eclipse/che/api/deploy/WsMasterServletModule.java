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
package org.eclipse.che.api.deploy;

import com.google.common.collect.ImmutableMap;
import com.google.inject.servlet.ServletModule;
import org.eclipse.che.api.core.cors.CheCorsFilter;
import org.eclipse.che.commons.logback.filter.RequestIdLoggerFilter;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.multiuser.keycloak.server.deploy.KeycloakServletModule;
import org.eclipse.che.multiuser.machine.authentication.server.MachineLoginFilter;
import org.eclipse.che.multiuser.oidc.filter.OidcTokenInitializationFilter;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesInfrastructure;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftInfrastructure;
import org.eclipse.che.workspace.infrastructure.openshift.multiuser.oauth.OpenshiftTokenInitializationFilter;
import org.everrest.guice.servlet.GuiceEverrestServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author andrew00x */
@DynaModule
public class WsMasterServletModule extends ServletModule {
  private static final Logger LOG = LoggerFactory.getLogger(WsMasterServletModule.class);

  @Override
  protected void configureServlets() {

    if (Boolean.valueOf(System.getenv("CHE_TRACING_ENABLED"))) {
      install(new org.eclipse.che.core.tracing.web.TracingWebModule());
    }
    if (isCheCorsEnabled()) {
      filter("/*").through(CheCorsFilter.class);
    }

    filter("/*").through(RequestIdLoggerFilter.class);

    // Matching group SHOULD contain forward slash.
    serveRegex("^(?!/websocket.?)(.*)")
        .with(GuiceEverrestServlet.class, ImmutableMap.of("openapi.context.id", "org.eclipse.che"));

    if (Boolean.parseBoolean(System.getenv("CHE_AUTH_NATIVEUSER"))) {
      LOG.info("Running in native-user mode ...");
      configureNativeUserMode();
    } else {
      LOG.info("Running in classic multi-user mode ...");
      configureMultiUserMode();
    }

    if (Boolean.valueOf(System.getenv("CHE_METRICS_ENABLED"))) {
      install(new org.eclipse.che.core.metrics.MetricsServletModule());
    }
  }

  private boolean isCheCorsEnabled() {
    String cheCorsEnabledEnvVar = System.getenv("CHE_CORS_ENABLED");
    if (cheCorsEnabledEnvVar == null) {
      // by default CORS should be disabled
      return false;
    } else {
      return Boolean.valueOf(cheCorsEnabledEnvVar);
    }
  }

  private void configureMultiUserMode() {
    filterRegex(".*").through(MachineLoginFilter.class);
    install(new KeycloakServletModule());
  }

  private void configureNativeUserMode() {
    final String infrastructure = System.getenv("CHE_INFRASTRUCTURE_ACTIVE");
    if (OpenShiftInfrastructure.NAME.equals(infrastructure)) {
      filter("/*").through(OpenshiftTokenInitializationFilter.class);
    } else if (KubernetesInfrastructure.NAME.equals(infrastructure)) {
      filter("/*").through(OidcTokenInitializationFilter.class);
    } else {
      throw new ConfigurationException("Native user mode is currently supported on on OpenShift.");
    }
  }
}
