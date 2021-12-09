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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_LABEL;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_PATH_ANNOTATION;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_WATCH_SECRET_LABEL;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesObjectUtil.isValidConfigMapKeyName;

import com.google.common.annotations.VisibleForTesting;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.validation.constraints.NotNull;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.ssh.server.SshManager;
import org.eclipse.che.api.ssh.server.model.impl.SshPairImpl;
import org.eclipse.che.api.ssh.shared.model.SshPair;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class mounts existing user SSH Keys into a special Kubernetes Secret on user-s namespace.
 */
public class SshKeysConfigurator implements NamespaceConfigurator {

  public static final String SSH_KEY_SECRET_NAME = "che-git-ssh-key";
  private static final String SSH_KEYS_WILL_NOT_BE_MOUNTED_MESSAGE =
      "Ssh keys %s have invalid names and can't be mounted to namespace %s.";

  private static final String SSH_BASE_CONFIG_PATH = "/etc/ssh/";

  private final SshManager sshManager;

  private final KubernetesClientFactory clientFactory;

  private static final Logger LOG = LoggerFactory.getLogger(SshKeysConfigurator.class);

  public static final Pattern VALID_DOMAIN_PATTERN =
      Pattern.compile("^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$");

  @Inject
  public SshKeysConfigurator(SshManager sshManager, KubernetesClientFactory clientFactory) {
    this.sshManager = sshManager;
    this.clientFactory = clientFactory;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {

    var client = clientFactory.create();
    List<SshPairImpl> vcsSshPairs = getVcsSshPairs(namespaceResolutionContext);

    List<String> invalidSshKeyNames =
        vcsSshPairs
            .stream()
            .filter(keyPair -> !isValidSshKeyPair(keyPair))
            .map(SshPairImpl::getName)
            .collect(toList());

    if (!invalidSshKeyNames.isEmpty()) {
      String message =
          format(
              SSH_KEYS_WILL_NOT_BE_MOUNTED_MESSAGE, invalidSshKeyNames.toString(), namespaceName);
      LOG.warn(message);
      // filter
      vcsSshPairs = vcsSshPairs.stream().filter(this::isValidSshKeyPair).collect(toList());
    }

    if (vcsSshPairs.size() == 0) {
      // nothing to provision
      return;
    }
    doProvisionSshKeys(vcsSshPairs, client, namespaceName);
  }

  /**
   * Return list of keys related to the VCS (Version Control Systems), Git, SVN and etc. Usually
   * managed by user
   *
   * @param context NamespaceResolutionContext
   * @return list of ssh pairs
   */
  private List<SshPairImpl> getVcsSshPairs(NamespaceResolutionContext context)
      throws InfrastructureException {
    List<SshPairImpl> sshPairs;
    try {
      sshPairs = sshManager.getPairs(context.getUserId(), "vcs");
    } catch (ServerException e) {
      String message = format("Unable to get SSH Keys. Cause: %s", e.getMessage());
      LOG.warn(message);
      throw new InfrastructureException(e);
    }
    return sshPairs;
  }

  @VisibleForTesting
  boolean isValidSshKeyPair(SshPairImpl keyPair) {
    return isValidConfigMapKeyName(keyPair.getName())
        && VALID_DOMAIN_PATTERN.matcher(keyPair.getName()).matches();
  }

  private void doProvisionSshKeys(
      List<SshPairImpl> sshPairs, KubernetesClient client, String namespaceName) {

    StringBuilder sshConfigData = new StringBuilder();
    Map<String, String> data = new HashMap<>();

    for (SshPair sshPair : sshPairs) {
      sshConfigData.append(buildConfig(sshPair.getName()));
      if (!isNullOrEmpty(sshPair.getPrivateKey())) {
        data.put(
            sshPair.getName(),
            Base64.getEncoder().encodeToString(sshPair.getPrivateKey().getBytes()));
        data.put(
            sshPair.getName() + ".pub",
            Base64.getEncoder().encodeToString(sshPair.getPublicKey().getBytes()));
      }
    }
    data.put("ssh_config", Base64.getEncoder().encodeToString(sshConfigData.toString().getBytes()));
    Secret secret =
        new SecretBuilder()
            .addToData(data)
            .withType("generic")
            .withMetadata(buildMetadata())
            .build();

    client.secrets().inNamespace(namespaceName).createOrReplace(secret);
  }

  private ObjectMeta buildMetadata() {
    return new ObjectMetaBuilder()
        .withName(SSH_KEY_SECRET_NAME)
        .withLabels(
            Map.of(
                DEV_WORKSPACE_MOUNT_LABEL, "true",
                DEV_WORKSPACE_WATCH_SECRET_LABEL, "true"))
        .withAnnotations(Map.of(DEV_WORKSPACE_MOUNT_PATH_ANNOTATION, SSH_BASE_CONFIG_PATH))
        .build();
  }

  /**
   * Returns the ssh configuration entry which includes host, identity file location and Host Key
   * checking policy
   *
   * <p>Example of provided configuration:
   *
   * <pre>
   * host github.com
   * IdentityFile /.ssh/private/github-com/private
   * StrictHostKeyChecking = no
   * </pre>
   *
   * or
   *
   * <pre>
   * host *
   * IdentityFile /.ssh/private/default-123456/private
   * StrictHostKeyChecking = no
   * </pre>
   *
   * @param name the of key given during generate for vcs service we will consider it as host of
   *     version control service (e.g. github.com, gitlab.com and etc) if name starts from
   *     "default-{anyString}" it will be replaced on wildcard "*" host name. Name with format
   *     "default-{anyString}" will be generated on client side by Theia SSH Plugin, if user doesn't
   *     provide own name. Details see here:
   *     https://github.com/eclipse/che/issues/13494#issuecomment-512761661. Note: behavior can be
   *     improved in 7.x releases after 7.0.0
   * @return the ssh configuration which include host, identity file location and Host Key checking
   *     policy
   */
  private String buildConfig(@NotNull String name) {
    String host = name.startsWith("default-") ? "*" : name;
    return "host "
        + host
        + "\nIdentityFile "
        + SSH_BASE_CONFIG_PATH
        + name
        + "\nStrictHostKeyChecking = no"
        + "\n\n";
  }
}
