/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.ssh.server;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.api.ssh.shared.Constants.LINK_REL_GET_PAIR;
import static org.eclipse.che.api.ssh.shared.Constants.LINK_REL_REMOVE_PAIR;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.fileupload.FileItem;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.core.util.LinksHelper;
import org.eclipse.che.api.ssh.server.model.impl.SshPairImpl;
import org.eclipse.che.api.ssh.shared.dto.GenerateSshPairRequest;
import org.eclipse.che.api.ssh.shared.dto.SshPairDto;
import org.eclipse.che.api.ssh.shared.model.SshPair;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Defines Ssh Rest API.
 *
 * @author Sergii Leschenko
 */
@Deprecated
public class SshService extends Service {
  private final SshManager sshManager;

  @Inject
  public SshService(SshManager sshManager) {
    this.sshManager = sshManager;
  }

  public Response generatePair(GenerateSshPairRequest request)
      throws BadRequestException, ServerException, ConflictException {
    requiredNotNull(request, "Generate ssh pair request required");
    requiredNotNull(request.getService(), "Service name required");
    requiredNotNull(request.getName(), "Name required");
    final SshPairImpl generatedPair =
        sshManager.generatePair(getCurrentUserId(), request.getService(), request.getName());

    return Response.status(Response.Status.CREATED)
        .entity(asDto(injectLinks(asDto(generatedPair))))
        .build();
  }

  public Response createPair(Iterator<FileItem> formData)
      throws BadRequestException, ServerException, ConflictException {
    String service = null;
    String name = null;
    String privateKey = null;
    String publicKey = null;

    while (formData.hasNext()) {
      FileItem item = formData.next();
      String fieldName = item.getFieldName();
      switch (fieldName) {
        case "service":
          service = item.getString();
          break;
        case "name":
          name = item.getString();
          break;
        case "privateKey":
          privateKey = item.getString();
          break;
        case "publicKey":
          publicKey = item.getString();
          break;
        default:
          // do nothing
      }
    }

    requiredNotNull(service, "Service name required");
    requiredNotNull(name, "Name required");
    if (privateKey == null && publicKey == null) {
      throw new BadRequestException("Key content was not provided.");
    }

    sshManager.createPair(
        new SshPairImpl(getCurrentUserId(), service, name, publicKey, privateKey));

    // We should send 200 response code and body with empty line
    // through specific of html form that doesn't invoke complete submit handler
    return Response.ok("", MediaType.TEXT_HTML).build();
  }

  public void createPair(SshPairDto sshPair)
      throws BadRequestException, ServerException, ConflictException {
    requiredNotNull(sshPair, "Ssh pair required");
    requiredNotNull(sshPair.getService(), "Service name required");
    requiredNotNull(sshPair.getName(), "Name required");
    if (sshPair.getPublicKey() == null && sshPair.getPrivateKey() == null) {
      throw new BadRequestException("Key content was not provided.");
    }

    sshManager.createPair(new SshPairImpl(getCurrentUserId(), sshPair));
  }

  public SshPairDto getPair(String service, String name)
      throws NotFoundException, ServerException, BadRequestException {
    requiredNotNull(name, "Name of ssh pair");
    return injectLinks(asDto(sshManager.getPair(getCurrentUserId(), service, name)));
  }

  public void removePair(String service, String name)
      throws ServerException, NotFoundException, BadRequestException {
    requiredNotNull(name, "Name of ssh pair");
    sshManager.removePair(getCurrentUserId(), service, name);
  }

  public List<SshPairDto> getPairs(String service) throws ServerException {
    return sshManager.getPairs(getCurrentUserId(), service).stream()
        .map(sshPair -> injectLinks(asDto(sshPair)))
        .collect(Collectors.toList());
  }

  private static String getCurrentUserId() {
    return EnvironmentContext.getCurrent().getSubject().getUserId();
  }

  private static SshPairDto asDto(SshPair pair) {
    return newDto(SshPairDto.class)
        .withService(pair.getService())
        .withName(pair.getName())
        .withPublicKey(pair.getPublicKey())
        .withPrivateKey(pair.getPrivateKey());
  }

  private SshPairDto injectLinks(SshPairDto sshPairDto) {
    final UriBuilder uriBuilder = getServiceContext().getServiceUriBuilder();
    final Link getPairsLink =
        LinksHelper.createLink(
            "GET",
            uriBuilder
                .clone()
                .path(getClass(), "getPairs")
                .build(sshPairDto.getService())
                .toString(),
            APPLICATION_JSON,
            LINK_REL_GET_PAIR);

    final Link removePairLink =
        LinksHelper.createLink(
            "DELETE",
            uriBuilder
                .clone()
                .path(getClass(), "removePair")
                .build(sshPairDto.getService(), sshPairDto.getName())
                .toString(),
            APPLICATION_JSON,
            LINK_REL_REMOVE_PAIR);

    final Link getPairLink =
        LinksHelper.createLink(
            "GET",
            uriBuilder
                .clone()
                .path(getClass(), "getPair")
                .build(sshPairDto.getService(), sshPairDto.getName())
                .toString(),
            APPLICATION_JSON,
            LINK_REL_GET_PAIR);

    return sshPairDto.withLinks(Arrays.asList(getPairsLink, removePairLink, getPairLink));
  }

  /**
   * Checks object reference is not {@code null}
   *
   * @param object object reference to check
   * @param subject used as subject of exception message "{subject} required"
   * @throws BadRequestException when object reference is {@code null}
   */
  private void requiredNotNull(Object object, String subject) throws BadRequestException {
    if (object == null) {
      throw new BadRequestException(subject + " required");
    }
  }
}
