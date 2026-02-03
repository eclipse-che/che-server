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
package org.eclipse.che.api.deploy;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.collect.ImmutableMap;
import com.google.inject.servlet.ServletModule;
import org.eclipse.che.api.core.cors.CheCorsFilter;
import org.eclipse.che.commons.logback.filter.RequestIdLoggerFilter;
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.multiuser.oidc.filter.OidcTokenInitializationFilter;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftInfrastructure;
import org.eclipse.che.workspace.infrastructure.openshift.multiuser.oauth.OpenshiftTokenInitializationFilter;
import org.everrest.guice.servlet.GuiceEverrestServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author andrew00x
 */
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

    LOG.info("Running in native-user mode ...");
    configureNativeUserMode();

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

  private void configureNativeUserMode() {
    if (isOpenShiftOAuthEnabled()) {
      filter("/*").through(OpenshiftTokenInitializationFilter.class);
    } else {
      filter("/*").through(OidcTokenInitializationFilter.class);
    }
  }

  private boolean isOpenShiftOAuthEnabled() {
    String openShiftOAuthEnabled = System.getenv("CHE_INFRA_OPENSHIFT_OAUTH__ENABLED");

    if (!isNullOrEmpty(openShiftOAuthEnabled)) {
      return Boolean.valueOf(openShiftOAuthEnabled);
    }

    String infrastructure = System.getenv("CHE_INFRASTRUCTURE_ACTIVE");
    return OpenShiftInfrastructure.NAME.equals(infrastructure);
  }
}
