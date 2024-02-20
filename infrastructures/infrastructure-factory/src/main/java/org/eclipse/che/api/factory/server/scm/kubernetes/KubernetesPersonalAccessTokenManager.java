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
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.ScmPersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages personal access token secrets used for private repositories authentication. */
@Singleton
public class KubernetesPersonalAccessTokenManager implements PersonalAccessTokenManager {
  public static final Map<String, String> SECRET_LABELS =
      ImmutableMap.of(
          "app.kubernetes.io/part-of", "che.eclipse.org",
          "app.kubernetes.io/component", "scm-personal-access-token");
  public static final LabelSelector KUBERNETES_PERSONAL_ACCESS_TOKEN_LABEL_SELECTOR =
      new LabelSelectorBuilder().withMatchLabels(SECRET_LABELS).build();

  public static final String NAME_PATTERN = "personal-access-token-";

  public static final String ANNOTATION_CHE_USERID = "che.eclipse.org/che-userid";
  public static final String ANNOTATION_SCM_ORGANIZATION = "che.eclipse.org/scm-organization";
  public static final String ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID =
      "che.eclipse.org/scm-personal-access-token-id";
  public static final String ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME =
      "che.eclipse.org/scm-personal-access-token-name";
  public static final String ANNOTATION_SCM_URL = "che.eclipse.org/scm-url";
  public static final String TOKEN_DATA_FIELD = "token";

  private final KubernetesNamespaceFactory namespaceFactory;
  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  private final ScmPersonalAccessTokenFetcher scmPersonalAccessTokenFetcher;
  private final GitCredentialManager gitCredentialManager;

  private static final Logger LOG =
      LoggerFactory.getLogger(KubernetesPersonalAccessTokenManager.class);

  @Inject
  public KubernetesPersonalAccessTokenManager(
      KubernetesNamespaceFactory namespaceFactory,
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory,
      ScmPersonalAccessTokenFetcher scmPersonalAccessTokenFetcher,
      GitCredentialManager gitCredentialManager) {
    this.namespaceFactory = namespaceFactory;
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
    this.scmPersonalAccessTokenFetcher = scmPersonalAccessTokenFetcher;
    this.gitCredentialManager = gitCredentialManager;
  }

  @Override
  public void store(PersonalAccessToken personalAccessToken)
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException {
    try {
      String namespace = getFirstNamespace();
      ObjectMeta meta =
          new ObjectMetaBuilder()
              .withName(NameGenerator.generate(NAME_PATTERN, 5))
              .withAnnotations(
                  new ImmutableMap.Builder<String, String>()
                      .put(ANNOTATION_CHE_USERID, personalAccessToken.getCheUserId())
                      .put(ANNOTATION_SCM_URL, personalAccessToken.getScmProviderUrl())
                      .put(
                          ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID,
                          personalAccessToken.getScmTokenId())
                      .put(
                          ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME,
                          personalAccessToken.getScmTokenName())
                      .build())
              .withLabels(SECRET_LABELS)
              .build();

      Secret secret =
          new SecretBuilder()
              .withMetadata(meta)
              .withData(
                  Map.of(
                      TOKEN_DATA_FIELD,
                      Base64.getEncoder()
                          .encodeToString(
                              personalAccessToken.getToken().getBytes(StandardCharsets.UTF_8))))
              .build();

      cheServerKubernetesClientFactory
          .create()
          .secrets()
          .inNamespace(namespace)
          .createOrReplace(secret);
    } catch (KubernetesClientException | InfrastructureException e) {
      throw new ScmConfigurationPersistenceException(e.getMessage(), e);
    }
  }

  @Override
  public PersonalAccessToken fetchAndSave(Subject cheUser, String scmServerUrl)
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException,
          ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    PersonalAccessToken personalAccessToken =
        scmPersonalAccessTokenFetcher.fetchPersonalAccessToken(cheUser, scmServerUrl);
    store(personalAccessToken);
    return personalAccessToken;
  }

  @Override
  public Optional<PersonalAccessToken> get(Subject cheUser, String scmServerUrl)
      throws ScmConfigurationPersistenceException {
    return doGetPersonalAccessToken(cheUser, null, scmServerUrl);
  }

  @Override
  public PersonalAccessToken get(String scmServerUrl)
      throws ScmConfigurationPersistenceException, ScmUnauthorizedException,
          ScmCommunicationException, UnknownScmProviderException,
          UnsatisfiedScmPreconditionException {
    Subject subject = EnvironmentContext.getCurrent().getSubject();
    Optional<PersonalAccessToken> tokenOptional = get(subject, scmServerUrl);
    if (tokenOptional.isPresent()) {
      return tokenOptional.get();
    } else {
      // try to authenticate for the given URL
      return fetchAndSave(subject, scmServerUrl);
    }
  }

  @Override
  public Optional<PersonalAccessToken> get(
      Subject cheUser, String oAuthProviderName, @Nullable String scmServerUrl)
      throws ScmConfigurationPersistenceException {
    return doGetPersonalAccessToken(cheUser, oAuthProviderName, scmServerUrl);
  }

  private Optional<PersonalAccessToken> doGetPersonalAccessToken(
      Subject cheUser, @Nullable String oAuthProviderName, @Nullable String scmServerUrl)
      throws ScmConfigurationPersistenceException {
    try {
      LOG.debug(
          "Fetching personal access token for user {} and OAuth provider {}",
          cheUser.getUserId(),
          oAuthProviderName);
      for (KubernetesNamespaceMeta namespaceMeta : namespaceFactory.list()) {
        List<Secret> secrets =
            namespaceFactory
                .access(null, namespaceMeta.getName())
                .secrets()
                .get(KUBERNETES_PERSONAL_ACCESS_TOKEN_LABEL_SELECTOR);
        for (Secret secret : secrets) {
          LOG.debug("Checking secret {}", secret.getMetadata().getName());
          if (deleteSecretIfMisconfigured(secret)) {
            LOG.debug("Secret {} is misconfigured and was deleted", secret.getMetadata().getName());
            continue;
          }

          if (isSecretMatchesSearchCriteria(cheUser, oAuthProviderName, scmServerUrl, secret)) {
            LOG.debug("Iterating over secret {}", secret.getMetadata().getName());
            PersonalAccessTokenParams personalAccessTokenParams =
                this.secret2PersonalAccessTokenParams(secret);
            Optional<String> scmUsername =
                scmPersonalAccessTokenFetcher.getScmUsername(personalAccessTokenParams);

            if (scmUsername.isPresent()) {
              LOG.debug(
                  "Creating personal access token for user {} and OAuth provider {}",
                  cheUser.getUserId(),
                  oAuthProviderName);
              Map<String, String> secretAnnotations = secret.getMetadata().getAnnotations();

              PersonalAccessToken personalAccessToken =
                  new PersonalAccessToken(
                      personalAccessTokenParams.getScmProviderUrl(),
                      secretAnnotations.get(ANNOTATION_CHE_USERID),
                      personalAccessTokenParams.getOrganization(),
                      scmUsername.get(),
                      secretAnnotations.get(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME),
                      personalAccessTokenParams.getScmTokenId(),
                      personalAccessTokenParams.getToken());
              return Optional.of(personalAccessToken);
            }

            // Removing token that is no longer valid. If several tokens exist the next one could
            // be valid. If no valid token can be found, the caller should react in the same way
            // as it reacts if no token exists. Usually, that means that process of new token
            // retrieval would be initiated.
            cheServerKubernetesClientFactory
                .create()
                .secrets()
                .inNamespace(namespaceMeta.getName())
                .delete(secret);
            LOG.debug("Secret {} is misconfigured and was deleted", secret.getMetadata().getName());
          }
        }
      }
    } catch (InfrastructureException | UnknownScmProviderException e) {
      LOG.debug("Failed to get personal access token", e);
      throw new ScmConfigurationPersistenceException(e.getMessage(), e);
    }
    return Optional.empty();
  }

  private boolean deleteSecretIfMisconfigured(Secret secret) throws InfrastructureException {
    Map<String, String> secretAnnotations = secret.getMetadata().getAnnotations();

    String configuredScmServerUrl = secretAnnotations.get(ANNOTATION_SCM_URL);
    String configuredCheUserId = secretAnnotations.get(ANNOTATION_CHE_USERID);
    String configuredOAuthProviderName =
        secretAnnotations.get(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME);

    // if any of the required annotations is missing, the secret is not valid
    if (isNullOrEmpty(configuredScmServerUrl)
        || isNullOrEmpty(configuredCheUserId)
        || isNullOrEmpty(configuredOAuthProviderName)) {
      cheServerKubernetesClientFactory
          .create()
          .secrets()
          .inNamespace(secret.getMetadata().getNamespace())
          .delete(secret);
      return true;
    }

    return false;
  }

  private PersonalAccessTokenParams secret2PersonalAccessTokenParams(Secret secret) {
    Map<String, String> secretAnnotations = secret.getMetadata().getAnnotations();

    String token = new String(Base64.getDecoder().decode(secret.getData().get("token"))).trim();
    String configuredOAuthProviderName =
        secretAnnotations.get(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME);
    String configuredTokenId = secretAnnotations.get(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_ID);
    String configuredScmOrganization = secretAnnotations.get(ANNOTATION_SCM_ORGANIZATION);
    String configuredScmServerUrl = secretAnnotations.get(ANNOTATION_SCM_URL);

    return new PersonalAccessTokenParams(
        trimEnd(configuredScmServerUrl, '/'),
        configuredOAuthProviderName,
        configuredTokenId,
        token,
        configuredScmOrganization);
  }

  private boolean isSecretMatchesSearchCriteria(
      Subject cheUser,
      @Nullable String oAuthProviderName,
      @Nullable String scmServerUrl,
      Secret secret) {
    Map<String, String> secretAnnotations = secret.getMetadata().getAnnotations();
    String configuredScmServerUrl = secretAnnotations.get(ANNOTATION_SCM_URL);
    String configuredCheUserId = secretAnnotations.get(ANNOTATION_CHE_USERID);
    String configuredOAuthProviderName =
        secretAnnotations.get(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME);

    return (configuredCheUserId.equals(cheUser.getUserId()))
        && (oAuthProviderName == null || oAuthProviderName.equals(configuredOAuthProviderName))
        && (scmServerUrl == null
            || trimEnd(configuredScmServerUrl, '/').equals(trimEnd(scmServerUrl, '/')));
  }

  @Override
  public PersonalAccessToken getAndStore(String scmServerUrl)
      throws ScmCommunicationException, ScmConfigurationPersistenceException,
          UnknownScmProviderException, UnsatisfiedScmPreconditionException,
          ScmUnauthorizedException {
    PersonalAccessToken personalAccessToken = get(scmServerUrl);
    gitCredentialManager.createOrReplace(personalAccessToken);
    return personalAccessToken;
  }

  @Override
  public void storeGitCredentials(String scmServerUrl)
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException,
          ScmCommunicationException, ScmUnauthorizedException {
    Subject subject = EnvironmentContext.getCurrent().getSubject();
    Optional<PersonalAccessToken> tokenOptional = get(subject, scmServerUrl);
    if (tokenOptional.isPresent()) {
      PersonalAccessToken personalAccessToken = tokenOptional.get();
      gitCredentialManager.createOrReplace(personalAccessToken);
    }
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
