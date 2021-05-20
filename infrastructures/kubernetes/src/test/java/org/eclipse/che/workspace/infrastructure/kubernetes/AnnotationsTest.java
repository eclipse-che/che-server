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
package org.eclipse.che.workspace.infrastructure.kubernetes;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.testng.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.che.api.workspace.server.model.impl.ServerConfigImpl;
import org.testng.annotations.Test;

/**
 * Test for {@link Annotations}.
 *
 * @author Sergii Leshchenko
 */
public class AnnotationsTest {
  static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  static final Map<String, String> ATTRIBUTES = singletonMap("key", "value");
  static final String stringAttributes = GSON.toJson(ATTRIBUTES);
  static final String stringEmptyAttributes = GSON.toJson(emptyMap());

  @Test
  public void serialization() {
    Map<String, String> serialized =
        Annotations.newSerializer()
            .server(
                "my-server1/http",
                new ServerConfigImpl("8000/tcp", "http", "/api/info", ATTRIBUTES))
            .servers(
                ImmutableMap.of(
                    "my-server2", new ServerConfigImpl("8080/tcp", "ws", "/connect", emptyMap())))
            .machineName("test-machine")
            .annotations();
    Map<String, String> expected =
        ImmutableMap.<String, String>builder()
            .put("org.eclipse.che.server.my-server1/http.port", "8000/tcp")
            .put("org.eclipse.che.server.my-server1/http.protocol", "http")
            .put("org.eclipse.che.server.my-server1/http.path", "/api/info")
            .put("org.eclipse.che.server.my-server1/http.attributes", stringAttributes)
            .put("org.eclipse.che.server.my-server2.port", "8080/tcp")
            .put("org.eclipse.che.server.my-server2.protocol", "ws")
            .put("org.eclipse.che.server.my-server2.path", "/connect")
            .put("org.eclipse.che.server.my-server2.attributes", stringEmptyAttributes)
            .put("org.eclipse.che.machine.name", "test-machine")
            .build();

    assertEquals(serialized, expected);
  }

  @Test
  public void deserialization() {
    ImmutableMap<String, String> annotations =
        ImmutableMap.<String, String>builder()
            .put("custom-label", "value")
            .put("org.eclipse.che.server.my-server1/http.port", "8000/tcp")
            .put("org.eclipse.che.server.my-server1/http.protocol", "http")
            .put("org.eclipse.che.server.my-server1/http.path", "/api/info")
            .put("org.eclipse.che.server.my-server2.port", "8080/tcp")
            .put("org.eclipse.che.server.my-server2.protocol", "ws")
            .put("org.eclipse.che.server.my-server2.path", "/connect")
            .put("org.eclipse.che.server.my-server2.attributes", stringAttributes)
            .put("org.eclipse.che.server.my-server3.port", "7070/tcp")
            .put("org.eclipse.che.server.my-server3.protocol", "http")
            .put("org.eclipse.che.server.my-server3.attributes", stringEmptyAttributes)
            .put("org.eclipse.che.machine.name", "test-machine")
            .build();

    Annotations.Deserializer deserializer = Annotations.newDeserializer(annotations);

    Map<String, ServerConfigImpl> servers = deserializer.servers();

    assertEquals(deserializer.machineName(), "test-machine");

    Map<String, ServerConfigImpl> expected = new HashMap<>();
    expected.put("my-server1/http", new ServerConfigImpl("8000/tcp", "http", "/api/info", null));
    expected.put("my-server2", new ServerConfigImpl("8080/tcp", "ws", "/connect", ATTRIBUTES));
    expected.put("my-server3", new ServerConfigImpl("7070/tcp", "http", null, emptyMap()));
    assertEquals(servers, expected);
  }
}
