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
package org.eclipse.che.api.core.rest;

import static org.everrest.core.ApplicationContext.anApplicationContext;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.che.api.core.rest.annotations.Description;
import org.eclipse.che.api.core.rest.annotations.GenerateLink;
import org.eclipse.che.api.core.rest.annotations.Required;
import org.eclipse.che.api.core.rest.annotations.Valid;
import org.eclipse.che.api.core.rest.shared.ParameterType;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.core.rest.shared.dto.LinkParameter;
import org.eclipse.che.api.core.rest.shared.dto.ServiceDescriptor;
import org.everrest.core.ApplicationContext;
import org.everrest.core.ResourceBinder;
import org.everrest.core.impl.ApplicationProviderBinder;
import org.everrest.core.impl.ContainerResponse;
import org.everrest.core.impl.EverrestConfiguration;
import org.everrest.core.impl.EverrestProcessor;
import org.everrest.core.impl.ProviderBinder;
import org.everrest.core.impl.RequestDispatcher;
import org.everrest.core.impl.RequestHandlerImpl;
import org.everrest.core.impl.ResourceBinderImpl;
import org.everrest.core.tools.DependencySupplierImpl;
import org.everrest.core.tools.ResourceLauncher;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/** @author <a href="mailto:andrew00x@gmail.com">Andrey Parfonov</a> */
public class ServiceDescriptorTest {
  final String BASE_URI = "http://localhost/service";
  final String SERVICE_URI = BASE_URI + "/test";

  @Description("test service")
  @Path("test")
  public static class EchoService extends Service {
    @GET
    @Path("my_method")
    @GenerateLink(rel = "echo")
    @Produces(MediaType.TEXT_PLAIN)
    public String echo(
        @Description("some text")
            @Required
            @Valid({"a", "b"})
            @DefaultValue("a")
            @QueryParam("text")
            String test) {
      return test;
    }
  }

  public static class Deployer extends Application {
    private final Set<Object> singletons;
    private final Set<Class<?>> classes;

    public Deployer() {
      classes = new HashSet<>(1);
      classes.add(EchoService.class);
      singletons = Collections.emptySet();
    }

    @Override
    public Set<Class<?>> getClasses() {
      return classes;
    }

    @Override
    public Set<Object> getSingletons() {
      return singletons;
    }
  }

  ResourceLauncher launcher;

  @BeforeTest
  public void setUp() throws Exception {
    DependencySupplierImpl dependencies = new DependencySupplierImpl();
    ResourceBinder resources = new ResourceBinderImpl();
    ProviderBinder providers = new ApplicationProviderBinder();
    EverrestProcessor processor =
        new EverrestProcessor(
            new EverrestConfiguration(),
            dependencies,
            new RequestHandlerImpl(new RequestDispatcher(resources), providers),
            null);
    launcher = new ResourceLauncher(processor);
    processor.addApplication(new Deployer());
    ApplicationContext.setCurrent(anApplicationContext().withProviders(providers).build());
    System.out.println("initialized");
  }

  @Test
  public void testDescription() throws Exception {
    Assert.assertEquals(getDescriptor().getDescription(), "test service");
  }

  @Test
  public void testServiceLocation() throws Exception {
    Assert.assertEquals(getDescriptor().getHref(), SERVICE_URI);
  }

  @Test
  public void testLinkAvailable() throws Exception {
    Assert.assertEquals(getDescriptor().getLinks().size(), 1);
  }

  @Test
  public void testLinkInfo() throws Exception {
    Link link = getLink("echo");
    Assert.assertEquals(link.getMethod(), HttpMethod.GET);
    Assert.assertEquals(link.getHref(), SERVICE_URI + "/my_method");
    Assert.assertEquals(link.getProduces(), MediaType.TEXT_PLAIN);
  }

  @Test
  public void testLinkParameters() throws Exception {
    Link link = getLink("echo");
    List<LinkParameter> parameters = link.getParameters();
    Assert.assertEquals(parameters.size(), 1);
    LinkParameter linkParameter = parameters.get(0);
    Assert.assertEquals(linkParameter.getDefaultValue(), "a");
    Assert.assertEquals(linkParameter.getDescription(), "some text");
    Assert.assertEquals(linkParameter.getName(), "text");
    Assert.assertEquals(linkParameter.getType(), ParameterType.String);
    Assert.assertTrue(linkParameter.isRequired());
    List<String> valid = linkParameter.getValid();
    Assert.assertEquals(valid.size(), 2);
    Assert.assertTrue(valid.contains("a"));
    Assert.assertTrue(valid.contains("b"));
  }

  private Link getLink(String rel) throws Exception {
    List<Link> links = getDescriptor().getLinks();
    for (Link link : links) {
      if (link.getRel().equals(rel)) {
        return link;
      }
    }
    return null;
  }

  private ServiceDescriptor getDescriptor() throws Exception {
    String path = SERVICE_URI;
    ContainerResponse response =
        launcher.service(HttpMethod.OPTIONS, path, BASE_URI, null, null, null, null);
    Assert.assertEquals(response.getStatus(), 200);
    return (ServiceDescriptor) response.getEntity();
  }
}
