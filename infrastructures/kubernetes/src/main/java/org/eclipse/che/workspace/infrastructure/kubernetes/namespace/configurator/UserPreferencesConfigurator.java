package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import com.google.common.annotations.VisibleForTesting;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_AS_ANNOTATION;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_LABEL;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_PATH_ANNOTATION;

public class UserPreferencesConfigurator implements NamespaceConfigurator {
    private static final String USER_PREFERENCES_SECRET_NAME = "user-preferences";
    private static final String USER_PREFERENCES_SECRET_MOUNT_PATH = "/config/user/preferences";


    @Override
    public void configure(NamespaceResolutionContext namespaceResolutionContext) {

    }

    private Optional<Secret> preparePreferencesSecret(User user) {
        Base64.Encoder enc = Base64.getEncoder();
        Map<String, String> preferences;
        try {
            preferences = preferenceManager.find(user.getId());
        } catch (ServerException e) {
            LOG.error(
                    "Could not find user preferences. Skipping creation of user preferences secrets.", e);
            return Optional.empty();
        }
        if (preferences == null || preferences.isEmpty()) {
            LOG.error("User preferences are empty. Skipping creation of user preferences secrets.");
            return Optional.empty();
        }

        Map<String, String> preferencesEncoded = new HashMap<>();
        preferences.forEach(
                (key, value) ->
                        preferencesEncoded.put(
                                normalizePreferenceName(key), enc.encodeToString(value.getBytes())));

        return Optional.of(
                new SecretBuilder()
                        .addToData(preferencesEncoded)
                        .withNewMetadata()
                        .withName(USER_PREFERENCES_SECRET_NAME)
                        .addToLabels(DEV_WORKSPACE_MOUNT_LABEL, "true")
                        .addToAnnotations(DEV_WORKSPACE_MOUNT_AS_ANNOTATION, "file")
                        .addToAnnotations(
                                DEV_WORKSPACE_MOUNT_PATH_ANNOTATION, USER_PREFERENCES_SECRET_MOUNT_PATH)
                        .endMetadata()
                        .build());
    }

    /**
     * Some preferences names are not compatible with k8s restrictions on key field in secret. The
     * keys of data must consist of alphanumeric characters, -, _ or . This method replaces illegal
     * characters with -
     *
     * @param name original preference name
     * @return k8s compatible preference name used as a key field in Secret
     */
    @VisibleForTesting
    String normalizePreferenceName(String name) {
        return name.replaceAll("[^-._a-zA-Z0-9]+", "-").replaceAll("-+", "-");
    }
}
