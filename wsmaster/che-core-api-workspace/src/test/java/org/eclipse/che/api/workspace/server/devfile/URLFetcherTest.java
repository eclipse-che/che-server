/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.che.api.workspace.server.devfile.URLFetcher.CONNECTION_READ_TIMEOUT;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.google.common.base.Strings;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Testing {@link org.eclipse.che.api.workspace.server.devfile.URLFetcher}
 *
 * @author Florent Benoit
 */
@Listeners(MockitoTestNGListener.class)
public class URLFetcherTest {

  /** Instance to test. */
  private URLFetcher urlFetcher = new URLFetcher(1024);

  /** Check that when url is null, NPE is thrown */
  @Test(expectedExceptions = NullPointerException.class)
  public void checkNullURL() {
    urlFetcher.fetchSafely(null);
  }

  /** Check that when url exists the content is retrieved */
  @Test
  public void checkGetContent() throws IOException {
    URLConnection urlConnection = Mockito.mock(URLConnection.class);
    when(urlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream("Hello".getBytes(UTF_8)));
    String content = urlFetcher.fetch(urlConnection);
    assertEquals(content, "Hello");
  }

  /** Check when url is invalid */
  @Test
  public void checkUrlFileIsInvalid() {
    String result = urlFetcher.fetchSafely("hello world");
    assertNull(result);
  }

  /** Check when url is invalid */
  @Test(
      expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = "no protocol: hello_world")
  public void checkUnsafeGetUrlFileIsInvalid() throws Exception {
    String result = urlFetcher.fetch("hello_world");
    assertNull(result);
  }

  /** Check that non-http schemes are rejected */
  @Test(
      expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = "Only http and https URLs are allowed.*")
  public void checkFileSchemeIsRejected() throws Exception {
    urlFetcher.fetch("file:///etc/passwd");
  }

  /** Check that non-http schemes are rejected via fetchSafely */
  @Test
  public void checkFileSchemeIsRejectedSafely() {
    String result = urlFetcher.fetchSafely("file:///etc/passwd");
    assertNull(result);
  }

  /** Check that non-http schemes are rejected */
  @Test(
      expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = "Only http and https URLs are allowed.*")
  public void checkFtpSchemeIsRejected() throws Exception {
    urlFetcher.fetch("ftp://evil.com/file");
  }

  /** Check that non-http schemes are rejected */
  @Test(
      expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = "Only http and https URLs are allowed.*")
  public void checkJarSchemeIsRejected() throws Exception {
    urlFetcher.fetch("jar:file:///tmp/evil.jar!/payload");
  }

  /** Check that http scheme is allowed */
  @Test
  public void checkHttpSchemeIsAllowed() throws IOException {
    URLFetcher fetcher =
        new TimeoutCheckURLFetcher(
            timeout -> assertEquals(timeout.intValue(), CONNECTION_READ_TIMEOUT));
    fetcher.fetch("http://example.com/devfile.yaml");
  }

  /** Check that https scheme is allowed */
  @Test
  public void checkHttpsSchemeIsAllowed() throws IOException {
    URLFetcher fetcher =
        new TimeoutCheckURLFetcher(
            timeout -> assertEquals(timeout.intValue(), CONNECTION_READ_TIMEOUT));
    fetcher.fetch("https://example.com/devfile.yaml");
  }

  /** Check Sanitizing of Git URL works */
  @Test
  public void checkDotGitRemovedFromURL() {
    String result = urlFetcher.sanitized("https://github.com/acme/demo.git");
    assertEquals("https://github.com/acme/demo", result);

    result = urlFetcher.sanitized("http://github.com/acme/demo.git");
    assertEquals("http://github.com/acme/demo", result);
  }

  /** Check when we reach custom limit */
  @Test
  public void checkPartialContent() throws IOException {
    URLConnection urlConnection = Mockito.mock(URLConnection.class);
    when(urlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream("Hello".getBytes(UTF_8)));
    String content = new OneByteURLFetcher(1).fetch(urlConnection);
    assertEquals(content, "H");
  }

  /** Check when we reach custom limit */
  @Test
  public void checkDefaultPartialContent() throws IOException {
    URLConnection urlConnection = Mockito.mock(URLConnection.class);
    String originalContent = Strings.padEnd("", 1024, 'a');
    String extraContent = originalContent + "----";
    when(urlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream(extraContent.getBytes(UTF_8)));
    String readcontent = urlFetcher.fetch(urlConnection);
    // check extra content has been removed as we keep only first values
    assertEquals(readcontent, originalContent);
  }

  @Test
  public void testDefaultFetchTimeoutIsSet() throws IOException {
    URLFetcher fetcher =
        new TimeoutCheckURLFetcher(
            timeout -> assertEquals(timeout.intValue(), CONNECTION_READ_TIMEOUT));

    fetcher.fetch("http://eclipse.org/che");
  }

  @Test
  public void testFetchTimeoutIsSet() throws IOException {
    URLFetcher fetcher =
        new TimeoutCheckURLFetcher(timeout -> assertEquals(timeout.intValue(), 123));

    fetcher.fetch("http://eclipse.org/che", 123);
  }

  @Test(expectedExceptions = IOException.class)
  public void testExceptionIsThrownOnTimeout() throws IOException {
    URLFetcher fetcher = new URLFetcher(1024);
    URLConnection connection =
        new URLConnection(new URL("http://eclipse.org/che")) {
          @Override
          public void connect() throws IOException {
            // noop
          }

          @Override
          public InputStream getInputStream() throws IOException {
            throw new SocketTimeoutException("yes");
          }
        };

    fetcher.fetch(connection);
  }

  /**
   * A redirect is as much under the control of whoever supplied the URL as the URL itself, so the
   * target check has to be re-run on every hop rather than on the first one only.
   */
  @Test
  public void checkEveryRedirectHopIsValidated() throws Exception {
    try (LocalServers servers = new LocalServers()) {
      String target = servers.serveContent("target", "content");
      String entry = servers.redirectTo("entry", target);

      LoopbackURLFetcher fetcher = new LoopbackURLFetcher();
      assertEquals(fetcher.fetch(entry), "content");
      assertEquals(fetcher.validated, List.of(entry, target));
    }
  }

  /** The credentials of the caller must not be handed to whoever a redirect points at. */
  @Test
  public void checkAuthorizationIsDroppedOnCrossOriginRedirect() throws Exception {
    try (LocalServers servers = new LocalServers()) {
      String target = servers.echoAuthorization("target");
      String entry = servers.redirectTo("entry", target);

      assertEquals(new LoopbackURLFetcher().fetch(entry, "Bearer secret"), "authorization=null");
    }
  }

  /** Within a single origin there is nobody new to disclose the credentials to. */
  @Test
  public void checkAuthorizationIsKeptOnSameOriginRedirect() throws Exception {
    try (LocalServers servers = new LocalServers()) {
      HttpServer server = servers.newServer();
      servers.echoAuthorization(server, "target");
      String entry = servers.redirectTo(server, "entry", "/target");

      assertEquals(
          new LoopbackURLFetcher().fetch(entry, "Bearer secret"), "authorization=Bearer secret");
    }
  }

  @Test(
      expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = "Too many redirects.*")
  public void checkRedirectLoopIsGivenUpOn() throws Exception {
    try (LocalServers servers = new LocalServers()) {
      HttpServer server = servers.newServer();
      String entry = servers.redirectTo(server, "entry", "/entry");
      new LoopbackURLFetcher().fetch(entry);
    }
  }

  @Test(
      expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = "Only http and https URLs are allowed.*")
  public void checkRedirectToNonHttpSchemeIsRejected() throws Exception {
    try (LocalServers servers = new LocalServers()) {
      String entry = servers.redirectTo("entry", "file:///etc/passwd");
      new LoopbackURLFetcher().fetch(entry);
    }
  }

  /** A fetcher that accepts loopback targets, so that local servers can stand in for real hosts. */
  private static class LoopbackURLFetcher extends URLFetcher {
    private final List<String> validated = new ArrayList<>();

    LoopbackURLFetcher() {
      super(1024);
    }

    @Override
    void validateTarget(String url) throws IOException {
      validated.add(url);
      if (!url.startsWith("http://") && !url.startsWith("https://")) {
        throw new IOException("Only http and https URLs are allowed, got: " + url);
      }
    }
  }

  /** A handful of throwaway HTTP servers bound to the loopback interface. */
  private static class LocalServers implements AutoCloseable {
    private final List<HttpServer> servers = new ArrayList<>();

    HttpServer newServer() throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.start();
      servers.add(server);
      return server;
    }

    private String url(HttpServer server, String path) {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + path;
    }

    String serveContent(String path, String content) throws IOException {
      HttpServer server = newServer();
      server.createContext("/" + path, exchange -> respond(exchange, 200, content));
      return url(server, path);
    }

    String echoAuthorization(String path) throws IOException {
      HttpServer server = newServer();
      echoAuthorization(server, path);
      return url(server, path);
    }

    void echoAuthorization(HttpServer server, String path) {
      server.createContext(
          "/" + path,
          exchange ->
              respond(
                  exchange,
                  200,
                  "authorization=" + exchange.getRequestHeaders().getFirst("Authorization")));
    }

    String redirectTo(String path, String location) throws IOException {
      return redirectTo(newServer(), path, location);
    }

    String redirectTo(HttpServer server, String path, String location) {
      server.createContext(
          "/" + path,
          exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
          });
      return url(server, path);
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
      byte[] bytes = body.getBytes(UTF_8);
      exchange.sendResponseHeaders(code, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    }

    @Override
    public void close() {
      servers.forEach(server -> server.stop(0));
    }
  }

  /** Limit to only one Byte. */
  static class OneByteURLFetcher extends URLFetcher {

    public OneByteURLFetcher(long maxFetchBytes) {
      super(maxFetchBytes);
    }

    /** Override the limit */
    @Override
    protected long getLimit() {
      return 1;
    }
  }

  private static class TimeoutCheckURLFetcher extends URLFetcher {
    private final Consumer<Integer> assertion;

    public TimeoutCheckURLFetcher(Consumer<Integer> assertion) {
      super(500);
      this.assertion = assertion;
    }

    @Override
    String fetch(URLConnection urlConnection) {
      assertion.accept(urlConnection.getReadTimeout());
      assertion.accept(urlConnection.getConnectTimeout());
      return "NOOP";
    }

    /** Answers "not a redirect" without issuing the request. */
    @Override
    Optional<String> redirectLocation(HttpURLConnection connection) {
      return Optional.empty();
    }
  }
}
