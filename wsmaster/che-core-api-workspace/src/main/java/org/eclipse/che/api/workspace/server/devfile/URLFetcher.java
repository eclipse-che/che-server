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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.UrlTargetValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allow to grab content from URL
 *
 * @author Florent Benoit
 */
@Singleton
public class URLFetcher {

  /** Logger. */
  private static final Logger LOG = LoggerFactory.getLogger(URLFetcher.class);

  /** timeout when reading */
  @VisibleForTesting static final int CONNECTION_READ_TIMEOUT = 10 * 1000; // 10s

  /** How many redirects are followed before a request is given up on. */
  @VisibleForTesting static final int MAX_REDIRECTS = 5;

  /** The Compiled REGEX PATTERN that can be used for http|https git urls */
  final Pattern GIT_HTTP_URL_PATTERN = Pattern.compile("(?<sanitized>^http[s]?://.*)\\.git$");

  /** Maximum size of allowed data. */
  protected long maximumReadBytes;

  @Inject
  public URLFetcher(@Named("che.factory.scm_file_fetcher_limit_bytes") long maxFetchBytes) {
    this.maximumReadBytes = maxFetchBytes;
  }

  /**
   * Fetches the url provided and return its content. To prevent DOS attack, limit the amount of the
   * collected data
   *
   * @param url the URL to fetch
   * @return the content of the requested URL or {@code null} if error happened
   */
  public String fetchSafely(@NotNull final String url) {
    requireNonNull(url, "url parameter can't be null");
    try {
      return fetch(url);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Fetches the url provided and return its content. Uses default read connection timeout {@link
   * URLFetcher#CONNECTION_READ_TIMEOUT}
   *
   * @param url the URL to fetch
   * @return content of the requested URL
   * @throws IOException if fetch error occurs
   */
  public String fetch(@NotNull final String url) throws IOException {
    return fetch(url, CONNECTION_READ_TIMEOUT);
  }

  /**
   * Fetches the url provided and return its content. Uses default read connection timeout {@link
   * URLFetcher#CONNECTION_READ_TIMEOUT}
   *
   * @param url the URL to fetch
   * @param authorization Authorisation string or {@code null}
   * @return content of the requested URL
   * @throws IOException if fetch error occurs
   */
  public String fetch(@NotNull final String url, @Nullable String authorization)
      throws IOException {
    return fetch(url, CONNECTION_READ_TIMEOUT, authorization);
  }

  /**
   * Fetches the url provided and return its content.
   *
   * @param url the URL to fetch
   * @param timeout read and connection timeout (see {@link URLConnection#setConnectTimeout(int)}
   *     and {@link URLConnection#setReadTimeout(int)}
   * @return content of the requested URL
   * @throws IOException if fetch error occurs
   */
  String fetch(@NotNull final String url, int timeout) throws IOException {
    return fetch(url, timeout, null);
  }

  /**
   * Fetches the url provided and return its content.
   *
   * @param url the URL to fetch
   * @param authorization Authorisation string or {@code null}
   * @param timeout read and connection timeout (see {@link URLConnection#setConnectTimeout(int)}
   *     and {@link URLConnection#setReadTimeout(int)}
   * @return content of the requested URL
   * @throws IOException if fetch error occurs
   */
  String fetch(@NotNull final String url, int timeout, @Nullable String authorization)
      throws IOException {
    requireNonNull(url, "url parameter can't be null");
    // new URL() first, so that a malformed URL keeps reporting the parsing error it always did
    URL currentUrl = new URL(sanitized(url));
    String currentAuthorization = authorization;

    for (int hop = 0; ; hop++) {
      // every hop is validated: a redirect is as much under the control of whoever supplied the
      // URL as the URL itself, so validating only the first one would leave the check bypassable
      validateTarget(currentUrl.toString());

      URLConnection connection = currentUrl.openConnection();
      connection.setConnectTimeout(timeout);
      connection.setReadTimeout(timeout);
      if (!isNullOrEmpty(currentAuthorization)) {
        connection.setRequestProperty(HttpHeaders.AUTHORIZATION, currentAuthorization);
      }
      if (!(connection instanceof HttpURLConnection)) {
        return fetch(connection);
      }

      HttpURLConnection httpConnection = (HttpURLConnection) connection;
      httpConnection.setInstanceFollowRedirects(false);
      Optional<String> location = redirectLocation(httpConnection);
      if (location.isEmpty()) {
        return fetch(httpConnection);
      }
      httpConnection.disconnect();

      if (hop == MAX_REDIRECTS) {
        throw new IOException(
            "Too many redirects (more than " + MAX_REDIRECTS + ") while fetching " + url);
      }
      URL nextUrl = new URL(currentUrl, location.get());
      if (isSchemeDowngrade(currentUrl, nextUrl)) {
        throw new IOException(
            "Refusing to follow the redirect from " + currentUrl + " to " + nextUrl + " over http");
      }
      if (!isSameOrigin(currentUrl, nextUrl)) {
        // do not hand the caller's credentials to whoever the redirect points at
        currentAuthorization = null;
      }
      currentUrl = nextUrl;
    }
  }

  /**
   * Checks that the server is allowed to request the given URL. Called once per hop of a redirect
   * chain.
   *
   * @param url the URL about to be requested
   * @throws IOException if the URL may not be requested
   */
  @VisibleForTesting
  void validateTarget(String url) throws IOException {
    UrlTargetValidator.validate(url);
  }

  /**
   * Issues the request held by the given connection and returns where it redirects to, or an empty
   * optional if the response is not a redirect.
   *
   * @param connection the connection to send the request on
   * @return the value of the {@code Location} header of a redirect response
   * @throws IOException if the request fails, or if a redirect carries no location
   */
  @VisibleForTesting
  Optional<String> redirectLocation(HttpURLConnection connection) throws IOException {
    int status = connection.getResponseCode();
    if (status != HttpURLConnection.HTTP_MOVED_PERM
        && status != HttpURLConnection.HTTP_MOVED_TEMP
        && status != HttpURLConnection.HTTP_SEE_OTHER
        && status != 307
        && status != 308) {
      return Optional.empty();
    }
    String location = connection.getHeaderField("Location");
    if (isNullOrEmpty(location)) {
      throw new IOException(
          "Got a redirect response " + status + " without a location from " + connection.getURL());
    }
    return Optional.of(location);
  }

  private static boolean isSchemeDowngrade(URL from, URL to) {
    return "https".equalsIgnoreCase(from.getProtocol())
        && !"https".equalsIgnoreCase(to.getProtocol());
  }

  private static boolean isSameOrigin(URL first, URL second) {
    return first.getProtocol().equalsIgnoreCase(second.getProtocol())
        && String.valueOf(first.getHost())
            .toLowerCase(Locale.ROOT)
            .equals(String.valueOf(second.getHost()).toLowerCase(Locale.ROOT))
        && effectivePort(first) == effectivePort(second);
  }

  private static int effectivePort(URL url) {
    return url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
  }

  /**
   * Fetch the urlConnection stream by using the urlconnection and return its content To prevent DOS
   * attack, limit the amount of the collected data
   *
   * @param urlConnection the URL connection to fetch
   * @return the content of the file
   * @throws IOException if fetch error occurs
   */
  @VisibleForTesting
  String fetch(@NotNull URLConnection urlConnection) throws IOException {
    requireNonNull(urlConnection, "urlConnection parameter can't be null");
    final String value;
    try (InputStream inputStream = urlConnection.getInputStream();
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(ByteStreams.limit(inputStream, getLimit()), UTF_8))) {
      value = reader.lines().collect(Collectors.joining("\n"));
    } catch (IOException e) {
      // we shouldn't fetch if check is done before
      LOG.debug("Invalid URL", e);
      throw e;
    }
    return value;
  }

  /**
   * Maximum size that can be read.
   *
   * @return maximum size.
   */
  protected long getLimit() {
    return maximumReadBytes;
  }

  /**
   * Simple method to sanitize the Git urls like &quot;https://github.com/demo.git&quot; or
   * &quot;http://myowngit.example.com/demo.git&quot;
   *
   * @param url - the String format of the url
   * @return if the url ends with .git will return the url without .git otherwise return the url as
   *     it is
   */
  @VisibleForTesting
  String sanitized(String url) {
    if (url != null) {
      final Matcher matcher = GIT_HTTP_URL_PATTERN.matcher(url);
      if (matcher.find()) {
        return matcher.group("sanitized");
      }
    }
    return url;
  }
}
