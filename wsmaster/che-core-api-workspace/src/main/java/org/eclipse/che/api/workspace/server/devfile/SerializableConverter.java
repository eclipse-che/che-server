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
package org.eclipse.che.api.workspace.server.devfile;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import org.eclipse.che.api.core.model.workspace.devfile.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Helps to store and read serializable values of the preferences map in {@link Component} to/from
 * database.
 *
 * @author Max Shaposhnyk
 */
@Converter(autoApply = true)
public class SerializableConverter implements AttributeConverter<Serializable, String> {

  private ObjectMapper objectMapper;

  public SerializableConverter() {
    this.objectMapper =
        JsonMapper.builderWithJackson2Defaults()
            .activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                DefaultTyping.OBJECT_AND_NON_CONCRETE)
            .build();
  }

  @Override
  public String convertToDatabaseColumn(Serializable value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new RuntimeException("Unable to serialize preference value:" + e.getMessage(), e);
    }
  }

  @Override
  public Serializable convertToEntityAttribute(String dbData) {
    try {
      JsonNode node = objectMapper.readTree(dbData);
      if (node.isValueNode()) {
        return serializableNodeValue(node);
      } else if (node.isArray()) {
        List<Serializable> values = new ArrayList<>();
        node.values().forEach(n -> values.add(serializableNodeValue(n)));
        return values.toArray();
      } else {
        throw new RuntimeException("Unable to deserialize preference value:" + dbData);
      }
    } catch (JacksonException e) {
      throw new RuntimeException("Unable to deserialize preference value:" + e.getMessage(), e);
    }
  }

  private Serializable serializableNodeValue(JsonNode node) {
    if (node.isNumber()) {
      return node.numberValue();
    } else if (node.isBoolean()) {
      return node.booleanValue();
    } else if (node.isTextual()) {
      return node.textValue();
    } else {
      throw new RuntimeException("Unable to deserialize preference value:" + node.asText());
    }
  }
}
