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

import static java.lang.String.format;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.che.api.core.model.workspace.devfile.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Helps to deserialize multi-type preferences into {@link Component} as a {@code Map}.
 *
 * @author Max Shaposhnyk
 */
public class PreferencesDeserializer extends ValueDeserializer<Map<String, Serializable>> {

  @Override
  public Map<String, Serializable> deserialize(JsonParser jsonParser, DeserializationContext ctxt) {
    Map<String, Serializable> result = new HashMap<>();
    jsonParser.nextToken();
    while (!JsonToken.END_OBJECT.equals(jsonParser.currentToken())) {
      JsonToken currentToken = jsonParser.nextValue();
      switch (currentToken) {
        case VALUE_NUMBER_INT:
        case VALUE_NUMBER_FLOAT:
          result.put(jsonParser.currentName(), jsonParser.getNumberValue());
          break;
        case VALUE_FALSE:
        case VALUE_TRUE:
          result.put(jsonParser.currentName(), jsonParser.getValueAsBoolean());
          break;
        case VALUE_STRING:
          result.put(jsonParser.currentName(), jsonParser.getValueAsString());
          break;
        case START_ARRAY:
          String key = jsonParser.currentName();
          ArrayList<Serializable> array = new ArrayList<>();
          // Iterate over nested array
          JsonToken nextValue = jsonParser.nextValue();
          while (!JsonToken.END_ARRAY.equals(nextValue)) {

            switch (nextValue) {
              case VALUE_NUMBER_INT:
              case VALUE_NUMBER_FLOAT:
                array.add(jsonParser.getNumberValue());
                break;
              case VALUE_FALSE:
              case VALUE_TRUE:
                array.add(jsonParser.getValueAsBoolean());
                break;
              case VALUE_STRING:
                array.add(jsonParser.getValueAsString());
                break;
              default:
                throw new StreamReadException(
                    jsonParser,
                    format(
                        "Unexpected value in the nested array of the preference with key '%s'.",
                        key));
            }
            nextValue = jsonParser.nextValue();
          }
          result.put(key, array.toArray());
          break;
        default:
          throw new StreamReadException(
              jsonParser,
              format(
                  "Unexpected value of the preference with key '%s'.", jsonParser.currentName()));
      }
      jsonParser.nextToken();
    }
    return result;
  }
}
