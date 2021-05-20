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
package org.eclipse.che.commons.json;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class JsonTest {
  public static class Foo {
    private String fooBar;

    public String getFooBar() {
      return fooBar;
    }

    public void setFooBar(String fooBar) {
      this.fooBar = fooBar;
    }
  }

  @Test
  public void testSerializeDefault() throws Exception {
    String expectedJson = "{\"fooBar\":\"test\"}";
    Foo foo = new Foo();
    foo.setFooBar("test");
    assertEquals(expectedJson, JsonHelper.toJson(foo));
  }

  @Test
  public void testSerializeUnderscore() throws Exception {
    String expectedJson = "{\"foo_bar\":\"test\"}";
    Foo foo = new Foo();
    foo.setFooBar("test");
    assertEquals(expectedJson, JsonHelper.toJson(foo, JsonNameConventions.CAMEL_UNDERSCORE));
  }

  @Test
  public void testSerializeDash() throws Exception {
    String expectedJson = "{\"foo-bar\":\"test\"}";
    Foo foo = new Foo();
    foo.setFooBar("test");
    assertEquals(expectedJson, JsonHelper.toJson(foo, JsonNameConventions.CAMEL_DASH));
  }

  @Test
  public void testDeserializeDefault() throws Exception {
    String json = "{\"fooBar\":\"test\"}";
    Foo foo = JsonHelper.fromJson(json, Foo.class, null);
    assertEquals("test", foo.getFooBar());
  }

  @Test
  public void testDeserializeUnderscore() throws Exception {
    String json = "{\"foo_bar\":\"test\"}";
    Foo foo = JsonHelper.fromJson(json, Foo.class, null, JsonNameConventions.CAMEL_UNDERSCORE);
    assertEquals("test", foo.getFooBar());
  }

  @Test
  public void testDeserializeDash() throws Exception {
    String json = "{\"foo-bar\":\"test\"}";
    Foo foo = JsonHelper.fromJson(json, Foo.class, null, JsonNameConventions.CAMEL_DASH);
    assertEquals("test", foo.getFooBar());
  }
}
