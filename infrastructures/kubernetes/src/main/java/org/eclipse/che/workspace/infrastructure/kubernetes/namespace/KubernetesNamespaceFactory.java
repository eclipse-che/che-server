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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.eclipse.che.api.workspace.shared.Constants.WORKSPACE_INFRASTRUCTURE_NAMESPACE_ATTRIBUTE;
import static org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta.DEFAULT_ATTRIBUTE;
import static org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta.PHASE_ATTRIBUTE;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.NamespaceNameValidator.METADATA_NAME_MAX_LENGTH;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.ValidationException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.server.impls.KubernetesNamespaceMetaImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator.NamespaceConfigurator;
import org.eclipse.che.workspace.infrastructure.kubernetes.util.KubernetesSharedPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helps to create {@link KubernetesNamespace} instances.
 *
 * @author Anton Korneta
 */
@Singleton
public class KubernetesNamespaceFactory {

  private static final Logger LOG = LoggerFactory.getLogger(KubernetesNamespaceFactory.class);

  private static final Map<String, Function<NamespaceResolutionContext, String>>
      NAMESPACE_NAME_PLACEHOLDERS = new HashMap<>();
  private static final Set<String> REQUIRED_NAMESPACE_NAME_PLACEHOLDERS = new HashSet<>();
  private static final String USERNAME_PLACEHOLDER = "<username>";
  private static final String USERID_PLACEHOLDER = "<userid>";

  static final String NAMESPACE_TEMPLATE_ATTRIBUTE = "infrastructureNamespaceTemplate";

  static {
    NAMESPACE_NAME_PLACEHOLDERS.put(USERNAME_PLACEHOLDER, NamespaceResolutionContext::getUserName);
    NAMESPACE_NAME_PLACEHOLDERS.put(USERID_PLACEHOLDER, NamespaceResolutionContext::getUserId);
    REQUIRED_NAMESPACE_NAME_PLACEHOLDERS.add(USERNAME_PLACEHOLDER);
    REQUIRED_NAMESPACE_NAME_PLACEHOLDERS.add(USERID_PLACEHOLDER);
  }

  private final String defaultNamespaceName;
  protected final boolean labelNamespaces;
  protected final boolean annotateNamespaces;
  protected final Map<String, String> namespaceLabels;
  protected final Map<String, String> namespaceAnnotations;

  private final KubernetesClientFactory clientFactory;
  private final KubernetesClientFactory cheClientFactory;
  private final boolean namespaceCreationAllowed;
  private final UserManager userManager;
  private final PreferenceManager preferenceManager;
  protected final Set<NamespaceConfigurator> namespaceConfigurators;
  protected final KubernetesSharedPool sharedPool;

  @Inject
  public KubernetesNamespaceFactory(
      @Nullable @Named("che.infra.kubernetes.namespace.default") String defaultNamespaceName,
      @Named("che.infra.kubernetes.namespace.creation_allowed") boolean namespaceCreationAllowed,
      @Named("che.infra.kubernetes.namespace.label") boolean labelNamespaces,
      @Named("che.infra.kubernetes.namespace.annotate") boolean annotateNamespaces,
      @Named("che.infra.kubernetes.namespace.labels") String namespaceLabels,
      @Named("che.infra.kubernetes.namespace.annotations") String namespaceAnnotations,
      Set<NamespaceConfigurator> namespaceConfigurators,
      KubernetesClientFactory clientFactory,
      CheServerKubernetesClientFactory cheClientFactory,
      UserManager userManager,
      PreferenceManager preferenceManager,
      KubernetesSharedPool sharedPool)
      throws ConfigurationException {
    this.namespaceCreationAllowed = namespaceCreationAllowed;
    this.userManager = userManager;
    this.clientFactory = clientFactory;
    this.cheClientFactory = cheClientFactory;
    this.defaultNamespaceName = defaultNamespaceName;
    this.preferenceManager = preferenceManager;
    this.sharedPool = sharedPool;
    this.labelNamespaces = labelNamespaces;
    this.annotateNamespaces = annotateNamespaces;
    this.namespaceConfigurators = ImmutableSet.copyOf(namespaceConfigurators);

    //noinspection UnstableApiUsage
    Splitter.MapSplitter csvMapSplitter = Splitter.on(",").withKeyValueSeparator("=");
    //noinspection UnstableApiUsage
    this.namespaceLabels =
        isNullOrEmpty(namespaceLabels) ? emptyMap() : csvMapSplitter.split(namespaceLabels);
    //noinspection UnstableApiUsage
    this.namespaceAnnotations =
        isNullOrEmpty(namespaceAnnotations)
            ? emptyMap()
            : csvMapSplitter.split(namespaceAnnotations);

    if (isNullOrEmpty(defaultNamespaceName)) {
      throw new ConfigurationException("che.infra.kubernetes.namespace.default must be configured");
    } else if (REQUIRED_NAMESPACE_NAME_PLACEHOLDERS.stream()
        .noneMatch(defaultNamespaceName::contains)) {
      throw new ConfigurationException(
          format(
              "Only 'per user' is allowed."
                  + "Using the %s placeholder is required in the 'che.infra.kubernetes.namespace.default' parameter."
                  + " The current value is: `%s`.",
              Joiner.on(" or ").join(REQUIRED_NAMESPACE_NAME_PLACEHOLDERS), defaultNamespaceName));
    }
  }

  /**
   * Creates a Kubernetes namespace for the specified workspace.
   *
   * <p>Namespace won't be prepared. This method should be used only in case workspace recovering.
   *
   * @param workspaceId identifier of the workspace
   * @return created namespace
   */
  public KubernetesNamespace access(String workspaceId, String namespace) {
    return doCreateNamespaceAccess(workspaceId, namespace);
  }

  @VisibleForTesting
  KubernetesNamespace doCreateNamespaceAccess(String workspaceId, String name) {
    return new KubernetesNamespace(
        clientFactory, cheClientFactory, sharedPool.getExecutor(), name, workspaceId);
  }

  /**
   * Checks if the current user is able to use the specified namespace for their new workspaces.
   *
   * @param namespaceName namespace name to check
   * @throws ValidationException if the specified namespace is not permitted for the current user
   */
  public void checkIfNamespaceIsAllowed(String namespaceName) throws ValidationException {
    NamespaceResolutionContext context =
        new NamespaceResolutionContext(EnvironmentContext.getCurrent().getSubject());
    final String defaultNamespace =
        findStoredNamespace(context).orElse(evalPlaceholders(defaultNamespaceName, context));
    if (!namespaceName.equals(defaultNamespace)) {
      try {
        List<KubernetesNamespaceMeta> labeledNamespaces = findPreparedNamespaces(context);
        if (labeledNamespaces.stream().noneMatch(n -> n.getName().equals(namespaceName))) {
          throw new ValidationException(
              format(
                  "User defined namespaces are not allowed. Only the default namespace '%s' is available.",
                  defaultNamespace));
        }
      } catch (InfrastructureException e) {
        throw new ValidationException("Some infrastructure failure caused failed validation.", e);
      }
    }
  }

  /** Returns list of k8s namespaces names where a user is able to run workspaces. */
  public List<KubernetesNamespaceMeta> list() throws InfrastructureException {

    NamespaceResolutionContext resolutionCtx =
        new NamespaceResolutionContext(EnvironmentContext.getCurrent().getSubject());

    List<KubernetesNamespaceMeta> labeledNamespaces = findPreparedNamespaces(resolutionCtx);
    if (!labeledNamespaces.isEmpty()) {
      return labeledNamespaces;
    } else {
      return singletonList(getDefaultNamespace(resolutionCtx));
    }
  }

  /**
   * Returns default namespace, it's based on existing namespace if there is such or just object
   * holder if there is no such namespace on cluster.
   */
  private KubernetesNamespaceMeta getDefaultNamespace(NamespaceResolutionContext resolutionCtx)
      throws InfrastructureException {
    String evaluatedName = evaluateNamespaceName(resolutionCtx);

    Optional<KubernetesNamespaceMeta> defaultNamespaceOpt = fetchNamespace(evaluatedName);

    KubernetesNamespaceMeta defaultNamespace =
        defaultNamespaceOpt
            // if the predefined namespace does not exist - return dummy info and it will be created
            // during the first workspace start
            .orElseGet(() -> new KubernetesNamespaceMetaImpl(evaluatedName));

    defaultNamespace.getAttributes().put(DEFAULT_ATTRIBUTE, "true");
    return defaultNamespace;
  }

  /**
   * Fetches the specified namespace from a cluster.
   *
   * @param name name of namespace that should be fetched.
   * @return optional with kubernetes namespace meta
   * @throws InfrastructureException when any error occurs during namespace fetching
   */
  public Optional<KubernetesNamespaceMeta> fetchNamespace(String name)
      throws InfrastructureException {
    try {
      Namespace namespace = cheClientFactory.create().namespaces().withName(name).get();
      if (namespace == null) {
        return Optional.empty();
      } else {
        return Optional.of(asNamespaceMeta(namespace));
      }
    } catch (KubernetesClientException e) {
      throw new InfrastructureException(
          "Error occurred when tried to fetch default namespace. Cause: " + e.getMessage(), e);
    }
  }

  private KubernetesNamespaceMeta asNamespaceMeta(Namespace namespace) {
    Map<String, String> attributes = new HashMap<>(2);
    if (namespace.getStatus() != null && namespace.getStatus().getPhase() != null) {
      attributes.put(PHASE_ATTRIBUTE, namespace.getStatus().getPhase());
    }
    return new KubernetesNamespaceMetaImpl(namespace.getMetadata().getName(), attributes);
  }

  /**
   * Tells the caller whether the namespace that is being prepared for the provided workspace
   * runtime identity can be created or is expected to already be present.
   *
   * <p>Note that this method cannot be reduced to merely checking if user-defined namespaces are
   * allowed or not (and depending on prior validation using the {@link
   * #checkIfNamespaceIsAllowed(String)} method during the workspace creation) because workspace
   * start is a) async from workspace creation and the underlying namespaces might have disappeared
   * and b) can be called during workspace recovery, where we don't even have the current user in
   * the context.
   *
   * @param identity the identity of the workspace runtime
   * @return true if the namespace can be created, false if the namespace is expected to already
   *     exist
   * @throws InfrastructureException on failure
   */
  protected boolean canCreateNamespace(RuntimeIdentity identity) throws InfrastructureException {
    if (!namespaceCreationAllowed) {
      return false;
    }

    // we need to make sure that the provided namespace is indeed the one provided by our
    // configuration
    User owner;
    try {
      owner = userManager.getById(identity.getOwnerId());
    } catch (NotFoundException | ServerException e) {
      throw new InfrastructureException(
          "Failed to resolve workspace owner. Cause: " + e.getMessage(), e);
    }

    String requiredNamespace = identity.getInfrastructureNamespace();

    NamespaceResolutionContext resolutionContext =
        new NamespaceResolutionContext(
            identity.getWorkspaceId(), identity.getOwnerId(), owner.getName());

    String resolvedDefaultNamespace = evaluateNamespaceName(resolutionContext);

    return resolvedDefaultNamespace.equals(requiredNamespace);
  }

  /**
   * A managed namespace of a workspace is a namespace that is fully controlled by Che. Such
   * namespaces are deleted when the workspace is deleted.
   *
   * @param namespaceName the name of the namespace the workspace is stored in
   * @param workspace the workspace
   */
  protected boolean isWorkspaceNamespaceManaged(String namespaceName, Workspace workspace) {
    return namespaceName != null && namespaceName.contains(workspace.getId());
  }

  public KubernetesNamespace getOrCreate(RuntimeIdentity identity) throws InfrastructureException {
    KubernetesNamespace namespace = get(identity);

    var subject = EnvironmentContext.getCurrent().getSubject();
    NamespaceResolutionContext resolutionCtx =
        new NamespaceResolutionContext(
            identity.getWorkspaceId(), subject.getUserId(), subject.getUserName());
    Map<String, String> namespaceAnnotationsEvaluated =
        evaluateAnnotationPlaceholders(resolutionCtx);

    namespace.prepare(
        canCreateNamespace(identity),
        labelNamespaces ? namespaceLabels : emptyMap(),
        annotateNamespaces ? namespaceAnnotationsEvaluated : emptyMap());

    configureNamespace(resolutionCtx, namespace.getName());

    return namespace;
  }

  public KubernetesNamespace get(RuntimeIdentity identity) throws InfrastructureException {
    String workspaceId = identity.getWorkspaceId();
    String namespaceName = identity.getInfrastructureNamespace();
    return doCreateNamespaceAccess(workspaceId, namespaceName);
  }

  /** Gets a namespace the workspace is deployed to. */
  public KubernetesNamespace get(Workspace workspace) throws InfrastructureException {
    return doCreateNamespaceAccess(workspace.getId(), getNamespaceName(workspace));
  }

  /** Returns a namespace name where workspace is assigned to. */
  protected String getNamespaceName(Workspace workspace) throws InfrastructureException {
    String namespace = workspace.getAttributes().get(WORKSPACE_INFRASTRUCTURE_NAMESPACE_ATTRIBUTE);
    if (namespace == null) {
      // it seems to be legacy workspace since the namespace is not stored in workspace attributes
      // it's needed to evaluate that with current user and workspace id
      NamespaceResolutionContext resolutionCtx =
          new NamespaceResolutionContext(
              workspace.getId(),
              EnvironmentContext.getCurrent().getSubject().getUserId(),
              EnvironmentContext.getCurrent().getSubject().getUserName());
      namespace = evaluateNamespaceName(resolutionCtx);

      LOG.warn(
          "Workspace '{}' doesn't have an explicit namespace assigned."
              + " The legacy namespace resolution resolved it to '{}'.",
          workspace.getId(),
          namespace);
    }

    if (!NamespaceNameValidator.isValid(namespace)) {
      // At a certain unfortunate past version of Che, we stored invalid namespace names.
      // At this point in time, we're trying to work with an existing workspace that never could
      // started OR has been running since before that unfortunate version. In both cases, going
      // back to the default namespace name is the most safe bet we can make.

      // but of course, our attempt will be futile if we're running in a context that doesn't know
      // the current user.
      Subject subj = EnvironmentContext.getCurrent().getSubject();
      if (!subj.isAnonymous()) {
        NamespaceResolutionContext resolutionCtx =
            new NamespaceResolutionContext(workspace.getId(), subj.getUserId(), subj.getUserName());

        String defaultNamespace = evaluateNamespaceName(resolutionCtx);

        LOG.warn(
            "The namespace '{}' of the workspace '{}' is not valid. Trying to recover"
                + " from this situation using a default namespace which resolved to '{}'.",
            namespace,
            workspace.getId(),
            defaultNamespace);

        namespace = defaultNamespace;
      } else {
        // log a warning including a stacktrace to be able to figure out from where we got here...
        LOG.warn(
            "The namespace '{}' of the workspace '{}' is not valid but we currently don't have"
                + " an active user to try an recover from this situation. We're letting the parent"
                + " workflow continue, but it may fail at some later point in time because of"
                + " the incorrect namespace name in use.",
            namespace,
            workspace.getId(),
            new Throwable());
      }

      // ok, we tried to recover the namespace but nothing helped.
    }

    return namespace;
  }

  /**
   * Evaluates namespace according to the specified context.
   *
   * <p>First we try to find namespace with labels set in `che.infra.kubernetes.namespace.labels`
   * property. If any found, we take the first one and use it. See: {@link
   * KubernetesNamespaceFactory#findFirstLabeledNamespace(NamespaceResolutionContext)}
   *
   * <p>Then we try to find namespace stored in persisted user's preferences and use it if found.
   * See: {@link KubernetesNamespaceFactory#findStoredNamespace(NamespaceResolutionContext)}
   *
   * <p>As a last option, we construct namespace name from `che.infra.kubernetes.namespace.default`
   * property. See: {@link
   * KubernetesNamespaceFactory#evalDefaultNamespace(NamespaceResolutionContext)}
   *
   * @param resolutionCtx context for namespace evaluation
   * @return evaluated namespace name
   * @throws InfrastructureException when there legacy namespace doesn't exist and default namespace
   *     is not configured
   * @throws InfrastructureException when any exception occurs during evaluation
   */
  public String evaluateNamespaceName(NamespaceResolutionContext resolutionCtx)
      throws InfrastructureException {
    Optional<String> namespace =
        findFirstLabeledNamespace(resolutionCtx).or(() -> findStoredNamespace(resolutionCtx));
    if (namespace.isPresent()) {
      return namespace.get();
    } else {
      return evalDefaultNamespace(resolutionCtx);
    }
  }

  /**
   * Finds first namespace matches labels set by `che.infra.kubernetes.namespace.labels` property.
   *
   * @return first found labeled namespace if such namespace exists
   */
  private Optional<String> findFirstLabeledNamespace(NamespaceResolutionContext resolutionCtx)
      throws InfrastructureException {
    List<KubernetesNamespaceMeta> labeledNamespaces = findPreparedNamespaces(resolutionCtx);
    if (!labeledNamespaces.isEmpty()) {
      String foundNamespace =
          labeledNamespaces.stream()
              .findFirst()
              .map(KubernetesNamespaceMeta::getName)
              .orElseThrow(
                  () ->
                      new InfrastructureException(
                          "Failed when fetching labeled namespaces. It should not happen. Please report a bug if you see this!"));
      if (labeledNamespaces.size() > 1) {
        LOG.warn(
            "found '{}' matching labeled namespaces {}. Using '{}'.",
            labeledNamespaces.size(),
            labeledNamespaces.toString(),
            foundNamespace);
      }
      return Optional.of(foundNamespace);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Finds namespace name stored in User's preferences and ensures it is still valid.
   *
   * @return user's stored namespace if exists
   */
  private Optional<String> findStoredNamespace(NamespaceResolutionContext resolutionCtx) {
    Optional<Pair<String, String>> storedNamespace = getPreferencesNamespaceName(resolutionCtx);
    if (storedNamespace.isPresent() && storedNamespace.get().second.equals(defaultNamespaceName)) {
      return Optional.of(storedNamespace.get().first);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Constructs the namespace name from `che.infra.kubernetes.namespace.default` property. Ensures
   * that all placeholders are evaluated and final namespace name is in valid format.
   *
   * @return ready-to-use namespace name
   */
  private String evalDefaultNamespace(NamespaceResolutionContext resolutionCtx)
      throws InfrastructureException {
    String namespace = evalPlaceholders(defaultNamespaceName, resolutionCtx);

    if (!NamespaceNameValidator.isValid(namespace)) {
      Optional<KubernetesNamespaceMeta> namespaceMetaOptional;
      String normalizedNamespace = normalizeNamespaceName(namespace);
      if (normalizedNamespace.isEmpty()) {
        throw new InfrastructureException(
            format(
                "Evaluated empty namespace name for workspace %s", resolutionCtx.getWorkspaceId()));
      }
      do {
        normalizedNamespace =
            normalizedNamespace
                .substring(0, Math.min(55, normalizedNamespace.length()))
                .concat(NameGenerator.generate("-", 6));
        namespaceMetaOptional = fetchNamespace(normalizedNamespace);
      } while (namespaceMetaOptional.isPresent());
      namespace = normalizedNamespace;
    }

    LOG.debug(
        "Evaluated the namespace for workspace {} using the namespace default to {}",
        resolutionCtx.getWorkspaceId(),
        namespace);

    recordEvaluatedNamespaceName(namespace, resolutionCtx);

    return namespace;
  }

  /**
   * Finds all namespaces that matches the labels configured in
   * `che.infra.kubernetes.namespace.labels` and annotations in
   * `che.infra.kubernetes.namespace.annotations` properties. Makes sure that placeholder in the
   * annotations property are correctly evaluated.
   *
   * <p>If used ServiceAccount does not have permissions to list the namespaces, returns the empty
   * list.
   *
   * @return namespaces that matches the configured labels and annotations
   * @throws InfrastructureException in case of any Kubernetes request failure
   */
  protected List<KubernetesNamespaceMeta> findPreparedNamespaces(
      NamespaceResolutionContext namespaceCtx) throws InfrastructureException {
    try {
      List<Namespace> workspaceNamespaces =
          cheClientFactory.create().namespaces().withLabels(namespaceLabels).list().getItems();
      if (!workspaceNamespaces.isEmpty()) {
        Map<String, String> evaluatedAnnotations = evaluateAnnotationPlaceholders(namespaceCtx);
        return workspaceNamespaces.stream()
            .filter(p -> matchesAnnotations(p, evaluatedAnnotations))
            .map(this::asNamespaceMeta)
            .collect(Collectors.toList());
      } else {
        return emptyList();
      }
    } catch (KubernetesClientException kce) {
      if (kce.getCode() == 403) {
        LOG.warn(
            "Trying to fetch namespaces with labels '{}', but failed for lack of permissions. Cause: '{}'",
            namespaceLabels,
            kce.getMessage());
        return emptyList();
      } else {
        throw new InfrastructureException(
            "Error occurred when tried to list all available namespaces. Cause: "
                + kce.getMessage(),
            kce);
      }
    }
  }

  protected void configureNamespace(
      NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    for (NamespaceConfigurator configurator : namespaceConfigurators) {
      configurator.configure(namespaceResolutionContext, namespaceName);
    }
  }

  /**
   * Evaluate placeholder in `che.infra.kubernetes.namespace.annotations` property with given {@link
   * NamespaceResolutionContext}.
   *
   * @return evaluated labels
   */
  protected Map<String, String> evaluateAnnotationPlaceholders(
      NamespaceResolutionContext namespaceCtx) {
    Map<String, String> evaluatedAnnotations = new HashMap<>();
    for (String annotationName : namespaceAnnotations.keySet()) {
      String evaluatedAnnotationValue =
          namespaceAnnotations
              .get(annotationName)
              .replace(USERNAME_PLACEHOLDER, namespaceCtx.getUserName());
      evaluatedAnnotations.put(annotationName, evaluatedAnnotationValue);
    }
    return evaluatedAnnotations;
  }

  /**
   * Checks if given `object` contains all given `annotations` with exact values.
   *
   * @param object to check
   * @param annotations that given `object` has to contain
   * @return true if `object` contains all `annotations`. False otherwise.
   */
  protected boolean matchesAnnotations(HasMetadata object, Map<String, String> annotations) {
    if (object.getMetadata().getAnnotations() == null) {
      return false;
    }
    return object.getMetadata().getAnnotations().entrySet().containsAll(annotations.entrySet());
  }

  public void deleteIfManaged(Workspace workspace) throws InfrastructureException {
    KubernetesNamespace namespace = get(workspace);
    if (isWorkspaceNamespaceManaged(namespace.getName(), workspace)) {
      namespace.delete();
    }
  }

  protected String evalPlaceholders(String namespace, NamespaceResolutionContext ctx) {
    checkArgument(!isNullOrEmpty(namespace));
    String evaluated = namespace;
    for (Entry<String, Function<NamespaceResolutionContext, String>> placeHolder :
        NAMESPACE_NAME_PLACEHOLDERS.entrySet()) {

      String key = placeHolder.getKey();
      String value = placeHolder.getValue().apply(ctx);

      if (value != null) {
        evaluated = evaluated.replaceAll(key, value);
      }
    }
    return evaluated;
  }

  /**
   * Stores computed namespace name and it's template into user preferences. Template is required to
   * track its changes and re-generate namespace in case it didn't matches.
   */
  private void recordEvaluatedNamespaceName(String namespace, NamespaceResolutionContext context) {
    try {
      final String owner = context.getUserId();
      Map<String, String> preferences = preferenceManager.find(owner);
      preferences.put(WORKSPACE_INFRASTRUCTURE_NAMESPACE_ATTRIBUTE, namespace);
      preferences.put(NAMESPACE_TEMPLATE_ATTRIBUTE, defaultNamespaceName);
      preferenceManager.update(owner, preferences);
    } catch (ServerException e) {
      LOG.error("Failed storing namespace name in user properties.", e);
    }
  }

  /** Returns stored namespace if any, and its default template. */
  private Optional<Pair<String, String>> getPreferencesNamespaceName(
      NamespaceResolutionContext context) {
    try {
      String owner = context.getUserId();
      Map<String, String> preferences = preferenceManager.find(owner);
      if (preferences.containsKey(WORKSPACE_INFRASTRUCTURE_NAMESPACE_ATTRIBUTE)
          && preferences.containsKey(NAMESPACE_TEMPLATE_ATTRIBUTE)) {
        return Optional.of(
            Pair.of(
                preferences.get(WORKSPACE_INFRASTRUCTURE_NAMESPACE_ATTRIBUTE),
                preferences.get(NAMESPACE_TEMPLATE_ATTRIBUTE)));
      }
    } catch (ServerException e) {
      LOG.error(e.getMessage(), e);
    }
    return Optional.empty();
  }

  /**
   * Normalizes input namespace name to K8S accepted format
   *
   * @param namespaceName input namespace name
   * @return normalized namespace name
   */
  @VisibleForTesting
  String normalizeNamespaceName(String namespaceName) {
    namespaceName =
        namespaceName
            .toLowerCase()
            .replaceAll("[^-a-zA-Z0-9]", "-") // replace invalid chars with '-'
            .replaceAll("-+", "-") // replace multiple '-' with single ones
            .replaceAll("^-|-$", ""); // trim dashes at beginning/end of the string
    if (namespaceName.startsWith("kube-")) {
      namespaceName = "che-" + namespaceName;
    }
    return namespaceName.substring(
        0,
        Math.min(
            namespaceName.length(),
            METADATA_NAME_MAX_LENGTH)); // limit length to METADATA_NAME_MAX_LENGTH
  }
}
