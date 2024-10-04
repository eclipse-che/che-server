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
package org.eclipse.che.api.factory.server.scm.kubernetes;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.commons.lang.UrlUtils.getParameter;
import static org.eclipse.che.commons.lang.UrlUtils.getQueryParametersFromState;
import static org.eclipse.che.commons.lang.UrlUtils.getRequestUrl;
import static org.eclipse.che.commons.lang.UrlUtils.getState;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.PREFERENCES_CONFIGMAP_NAME;

import com.google.gson.Gson;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import jakarta.ws.rs.core.UriInfo;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;

/** Store and retrieve rejected authorisation requests in the Kubernetes ConfigMap. */
@Singleton
public class KubernetesAuthorisationRequestManager implements AuthorisationRequestManager {
  private final KubernetesNamespaceFactory namespaceFactory;
  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  private static final String SKIP_AUTHORISATION_MAP_KEY = "skip-authorisation";

  @Inject
  public KubernetesAuthorisationRequestManager(
      KubernetesNamespaceFactory namespaceFactory,
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory) {
    this.namespaceFactory = namespaceFactory;
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
  }

  @Override
  public void store(String scmProviderName) {
    if (isStored(scmProviderName)) {
      return;
    }
    ConfigMap configMap = getConfigMap();
    HashSet<String> fromJson = getSkipAuthorisationValues();
    fromJson.add(scmProviderName);

    configMap.setData(Map.of(SKIP_AUTHORISATION_MAP_KEY, fromJson.toString()));

    patchConfigMap(configMap);
  }

  @Override
  public void remove(String scmProviderName) {
    if (!isStored(scmProviderName)) {
      return;
    }
    ConfigMap configMap = getConfigMap();
    HashSet<String> fromJson = getSkipAuthorisationValues();
    fromJson.remove(scmProviderName);

    configMap.setData(Map.of(SKIP_AUTHORISATION_MAP_KEY, fromJson.toString()));

    patchConfigMap(configMap);
  }

  @Override
  public boolean isStored(String scmProviderName) {
    return getSkipAuthorisationValues().contains(scmProviderName);
  }

  @Override
  public void callback(UriInfo uriInfo, List<String> errorValues) {
    URL requestUrl = getRequestUrl(uriInfo);
    Map<String, List<String>> params = getQueryParametersFromState(getState(requestUrl));
    errorValues = errorValues == null ? uriInfo.getQueryParameters().get("error") : errorValues;
    if (errorValues != null && errorValues.contains("access_denied")) {
      String oauthProvider = getParameter(params, "oauth_provider");
      if (!isNullOrEmpty(oauthProvider)) {
        store(oauthProvider);
      }
    }
  }

  private ConfigMap getConfigMap() {
    try (KubernetesClient kubernetesClient = cheServerKubernetesClientFactory.create()) {
      String namespace = getFirstNamespace();
      return kubernetesClient
          .configMaps()
          .inNamespace(namespace)
          .withName(PREFERENCES_CONFIGMAP_NAME)
          .get();
    } catch (UnsatisfiedScmPreconditionException
        | ScmConfigurationPersistenceException
        | InfrastructureException e) {
      throw new RuntimeException(e);
    }
  }

  private void patchConfigMap(ConfigMap configMap) {
    try (KubernetesClient kubernetesClient = cheServerKubernetesClientFactory.create()) {
      kubernetesClient
          .configMaps()
          .inNamespace(getFirstNamespace())
          .withName(PREFERENCES_CONFIGMAP_NAME)
          .patch(PatchContext.of(PatchType.STRATEGIC_MERGE), configMap);
    } catch (UnsatisfiedScmPreconditionException
        | ScmConfigurationPersistenceException
        | InfrastructureException e) {
      throw new RuntimeException(e);
    }
  }

  private HashSet<String> getSkipAuthorisationValues() {
    String data = getConfigMap().getData().get(SKIP_AUTHORISATION_MAP_KEY);
    return new Gson().fromJson(isNullOrEmpty(data) ? "[]" : data, HashSet.class);
  }

  private String getFirstNamespace()
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException {
    try {
      return namespaceFactory.list().stream()
          .map(KubernetesNamespaceMeta::getName)
          .findFirst()
          .orElseThrow(
              () ->
                  new UnsatisfiedScmPreconditionException(
                      "No user namespace found. Cannot read SCM credentials."));
    } catch (InfrastructureException e) {
      throw new ScmConfigurationPersistenceException(e.getMessage(), e);
    }
  }
}
