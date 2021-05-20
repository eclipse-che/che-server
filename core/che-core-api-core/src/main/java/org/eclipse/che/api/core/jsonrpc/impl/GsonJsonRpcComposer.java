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
package org.eclipse.che.api.core.jsonrpc.impl;

import static java.util.Collections.emptyList;
import static org.eclipse.che.api.core.jsonrpc.commons.JsonRpcUtils.cast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.inject.Singleton;
import java.util.List;
import org.eclipse.che.api.core.jsonrpc.commons.JsonRpcComposer;
import org.eclipse.che.api.core.jsonrpc.commons.JsonRpcParams;
import org.eclipse.che.api.core.jsonrpc.commons.JsonRpcResult;
import org.eclipse.che.dto.server.DtoFactory;

@Singleton
public class GsonJsonRpcComposer implements JsonRpcComposer {
  @Override
  public <T> T composeOne(JsonRpcParams params, Class<T> type) {
    return composeOne(type, params.getOne());
  }

  @Override
  public <T> List<T> composeMany(JsonRpcParams params, Class<T> type) {
    return composeMany(type, params.getMany());
  }

  @Override
  public <T> T composeOne(JsonRpcResult result, Class<T> type) {
    return composeOne(type, result.getOne());
  }

  @Override
  public <T> List<T> composeMany(JsonRpcResult result, Class<T> type) {
    return composeMany(type, result.getMany());
  }

  private <T> T composeOne(Class<T> type, Object paramObject) {
    if (paramObject instanceof JsonElement) {
      JsonElement jsonElement = (JsonElement) paramObject;
      return DtoFactory.getInstance().createDtoFromJson(jsonElement.toString(), type);
    }

    return cast(paramObject);
  }

  private <T> List<T> composeMany(Class<T> type, List<?> paramsList) {
    if (paramsList.isEmpty()) {
      return emptyList();
    }

    if (paramsList.get(0) instanceof JsonElement) {
      JsonArray jsonArray = new JsonArray();
      for (int i = 0; i < paramsList.size(); i++) {
        JsonElement jsonElement = (JsonElement) paramsList.get(i);
        jsonArray.set(i, jsonElement);
      }
      return DtoFactory.getInstance().createListDtoFromJson(jsonArray.toString(), type);
    }

    return cast(paramsList);
  }
}
