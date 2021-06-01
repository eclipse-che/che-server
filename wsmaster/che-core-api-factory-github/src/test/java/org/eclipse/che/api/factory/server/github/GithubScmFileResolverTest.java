package org.eclipse.che.api.factory.server.github;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.commons.subject.Subject;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;


@Listeners(MockitoTestNGListener.class)
public class GithubScmFileResolverTest {

  public static final String SCM_URL = "http://gitlab.2mcl.com";
  GithubURLParser githubURLParser;

  @Mock
  private URLFetcher urlFetcher;

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  private GithubScmFileResolver githubScmFileResolver;

  @BeforeMethod
  protected void init() {
    githubURLParser = new GithubURLParser(urlFetcher, devfileFilenamesProvider);
    assertNotNull(this.githubURLParser);
    githubScmFileResolver =
        new GithubScmFileResolver(
            githubURLParser,
            urlFetcher);
    assertNotNull(this.githubScmFileResolver);
  }

  /** Check url which is not a Gitlab url can't be accepted by this resolver */
  @Test
  public void checkInvalidAcceptUrl() {
    // shouldn't be accepted
    assertFalse(githubScmFileResolver.accept("http://foobar.com"));
  }

  /** Check Gitlab url will be be accepted by this resolver */
  @Test
  public void checkValidAcceptUrl() {
    // should be accepted
    assertTrue(githubScmFileResolver.accept("http://github.com/test/repo.git"));
  }

  @Test
  public void shouldReturnContentFromUrlFetcher() throws Exception {
    final String rawContent = "raw_content";
    final String filename = "devfile.yaml";
    when(urlFetcher.fetch(anyString())).thenReturn(rawContent);

    String content = githubScmFileResolver
        .fileContent("http://github.com/test/repo.git", filename);

    assertEquals(content, rawContent);

  }

}