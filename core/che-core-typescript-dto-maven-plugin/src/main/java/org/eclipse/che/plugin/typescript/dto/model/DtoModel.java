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
package org.eclipse.che.plugin.typescript.dto.model;

import static org.eclipse.che.plugin.typescript.dto.DTOHelper.*;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.dto.shared.DTO;

/**
 * * Model of the DTO It includes attributes/fields and methods.
 *
 * @author Florent Benoit
 */
public class DtoModel {

  /** DTO instance (interface). */
  private Class dto;

  /** Model of methods for this interface. */
  private List<MethodModel> methods;

  /** Map of all attributes found when scanning methods */
  private Map<String, Type> fieldAttributes = new HashMap<>();

  /** Model for the attributes of this interface (for generating implementation) */
  private List<FieldAttributeModel> fieldAttributeModels;

  private String interfaces;

  private boolean extendsDto;

  /**
   * Build a new model for the given DTO class by scanning it.
   *
   * @param dto the interface with {@link org.eclipse.che.dto.shared.DTO} annotation
   */
  public DtoModel(Class dto) {
    this.dto = dto;
    this.methods = new ArrayList<>();
    this.fieldAttributeModels = new ArrayList<>();
    analyze();
  }

  /** Scan all getter/setter/with methods that are not inherited */
  protected void analyze() {
    Arrays.asList(this.dto.getMethods()).stream()
        .filter(
            method ->
                !method.isBridge()
                    && (isDtoGetter(method) || isDtoSetter(method) || isDtoWith(method)))
        .forEach(
            method -> {
              MethodModel methodModel = new MethodModel(method);

              // check method with same name already exist
              if (!methods.contains(methodModel)) {
                methods.add(methodModel);
                if (isDtoGetter(method)) {
                  analyzeDtoGetterMethod(method, methodModel);
                } else if (isDtoSetter(method)) {
                  analyzeDtoSetterMethod(method, methodModel);
                } else if (isDtoWith(method)) {
                  analyzeDtoWithMethod(method, methodModel);
                }
              }
            });

    // now convert map into list
    fieldAttributes.entrySet().stream()
        .forEach(
            field ->
                fieldAttributeModels.add(
                    new FieldAttributeModel(field.getKey(), field.getValue(), dto)));

    String interfaces =
        Arrays.stream(this.dto.getInterfaces())
            .filter(i -> i.getAnnotation(DTO.class) != null)
            .map(i -> convertTypeForDTS(this.dto, i))
            .collect(Collectors.joining(", "));
    if (!interfaces.isEmpty()) {
      this.interfaces = interfaces;
      this.extendsDto = true;
    }
  }

  /**
   * Populate model from given reflect getter method
   *
   * @param method the method to analyze
   * @param methodModel the model to update
   */
  protected void analyzeDtoGetterMethod(Method method, MethodModel methodModel) {
    methodModel.setGetter(true);
    Type fieldType = method.getGenericReturnType();
    Pair<String, String> names = getGetterFieldName(method);
    fieldAttributes.put(names.first, fieldType);
    methodModel.setFieldName(names.first);
    methodModel.setArgumentName(names.second);
    methodModel.setFieldType(convertType(fieldType));
  }

  /**
   * Populate model from given reflect setter method
   *
   * @param method the method to analyze
   * @param methodModel the model to update
   */
  protected void analyzeDtoSetterMethod(Method method, MethodModel methodModel) {
    methodModel.setSetter(true);
    // add the parameter
    Type fieldType = method.getGenericParameterTypes()[0];
    Pair<String, String> names = getSetterFieldName(method);
    fieldAttributes.put(names.first, fieldType);
    methodModel.setFieldName(names.first);
    methodModel.setArgumentName(names.second);
    methodModel.setFieldType(convertType(fieldType));
  }

  /**
   * Populate model from given reflect with method
   *
   * @param method the method to analyze
   * @param methodModel the model to update
   */
  protected void analyzeDtoWithMethod(Method method, MethodModel methodModel) {
    methodModel.setWith(true);
    // add the parameter
    Type fieldType = method.getGenericParameterTypes()[0];
    Pair<String, String> names = getWithFieldName(method);
    fieldAttributes.put(names.first, fieldType);
    methodModel.setFieldName(names.first);
    methodModel.setArgumentName(names.second);
    methodModel.setFieldType(convertType(fieldType));
  }

  /** @return model of attributes */
  public List<FieldAttributeModel> getFieldAttributeModels() {
    return fieldAttributeModels;
  }

  /**
   * Gets the package name of this interface
   *
   * @return the package name of this interface
   */
  public String getPackageName() {
    return this.dto.getPackage().getName();
  }

  /**
   * Gets the package name of this interface, without 'dto', 'shared', 'api' sections and
   * 'org.eclipse.' prefix
   *
   * @return the package name of this interface
   */
  public String getDTSPackageName() {
    return convertToDTSPackageName(this.dto);
  }

  /**
   * Gets the short (simple) name of the interface. Like HelloWorld if FQN class is
   * foo.bar.HelloWorld
   *
   * @return the name of the interface
   */
  public String getSimpleName() {
    return this.dto.getSimpleName();
  }

  /**
   * Gets the FQN of this interface like foo.bar.HelloWorld
   *
   * @return the FQN name of this DTO interface
   */
  public String getName() {
    return this.dto.getName();
  }

  /**
   * Provides the model for every methods of the DTO that are getter/setter/with methods
   *
   * @return the list
   */
  public List<MethodModel> getMethods() {
    return this.methods;
  }

  /**
   * Gets the FQN of this interface like foo.bar.HelloWorld, but without 'Dto' suffix
   *
   * @return the name of the interface
   */
  public String getDtsName() {
    return convertToDTSName(this.dto);
  }

  public String getInterfaces() {
    return interfaces;
  }

  public boolean isExtendsDto() {
    return extendsDto;
  }
}
