/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.cache;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.batch.bootstrap.ProjectReactor;
import org.sonar.batch.analysis.AnalysisProperties;
import org.sonar.batch.analysis.DefaultAnalysisMode;
import org.sonar.batch.protocol.input.ProjectRepositories;
import org.sonar.batch.repository.DefaultProjectRepositoriesLoader;
import org.sonar.batch.repository.DefaultServerIssuesLoader;
import org.sonar.batch.repository.ProjectRepositoriesLoader;
import org.sonar.batch.repository.ServerIssuesLoader;
import org.sonar.batch.repository.user.UserRepositoryLoader;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ProjectCacheSynchronizerTest {
  private static final String BATCH_PROJECT = "/batch/project?key=org.codehaus.sonar-plugins%3Asonar-scm-git-plugin&preview=true";
  private static final String ISSUES = "/batch/issues?key=org.codehaus.sonar-plugins%3Asonar-scm-git-plugin";

  @Mock
  private ProjectDefinition project;
  @Mock
  private ProjectReactor projectReactor;
  @Mock
  private ProjectCacheStatus cacheStatus;
  @Mock
  private DefaultAnalysisMode analysisMode;
  @Mock
  private AnalysisProperties properties;
  @Mock
  private WSLoader ws;

  private ProjectRepositoriesLoader projectRepositoryLoader;
  private ServerIssuesLoader issuesLoader;
  private UserRepositoryLoader userRepositoryLoader;

  private ProjectCacheSynchronizer sync;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    String batchProject = getResourceAsString("batch_project.json");
    ByteSource issues = getResourceAsByteSource("batch_issues.protobuf");
    String lineHashes2 = getResourceAsString("api_sources_hash_GitScmProvider.text");
    String lineHashes1 = getResourceAsString("api_sources_hash_JGitBlameCommand.text");

    when(ws.loadString(BATCH_PROJECT)).thenReturn(new WSLoaderResult<>(batchProject, false));
    when(ws.loadSource(ISSUES)).thenReturn(new WSLoaderResult<>(issues, false));

    when(analysisMode.isIssues()).thenReturn(true);
    when(project.getKeyWithBranch()).thenReturn("org.codehaus.sonar-plugins:sonar-scm-git-plugin");
    when(projectReactor.getRoot()).thenReturn(project);
    when(properties.properties()).thenReturn(new HashMap<String, String>());

    projectRepositoryLoader = new DefaultProjectRepositoriesLoader(ws, analysisMode);
    issuesLoader = new DefaultServerIssuesLoader(ws);
    userRepositoryLoader = new UserRepositoryLoader(ws);

    sync = new ProjectCacheSynchronizer(projectReactor, projectRepositoryLoader, properties, issuesLoader, userRepositoryLoader,
      cacheStatus);
  }

  @Test
  public void testSync() {
    sync.load(false);

    verify(ws).loadString(BATCH_PROJECT);
    verify(ws).loadSource(ISSUES);
    verifyNoMoreInteractions(ws);

    verify(cacheStatus).save(anyString());
  }

  @Test
  public void testSyncNoLastAnalysis() {
    projectRepositoryLoader = mock(DefaultProjectRepositoriesLoader.class);
    ProjectRepositories mockedProjectRepositories = mock(ProjectRepositories.class);
    when(mockedProjectRepositories.lastAnalysisDate()).thenReturn(null);
    when(projectRepositoryLoader.load(any(ProjectDefinition.class), any(AnalysisProperties.class), any(MutableBoolean.class))).thenReturn(mockedProjectRepositories);

    sync = new ProjectCacheSynchronizer(projectReactor, projectRepositoryLoader, properties, issuesLoader, userRepositoryLoader,
      cacheStatus);
    sync.load(true);

    verify(cacheStatus).save("org.codehaus.sonar-plugins:sonar-scm-git-plugin");
  }

  @Test
  public void testDontSyncIfNotForce() {
    when(cacheStatus.getSyncStatus("org.codehaus.sonar-plugins:sonar-scm-git-plugin")).thenReturn(new Date());

    ProjectCacheSynchronizer sync = new ProjectCacheSynchronizer(projectReactor, projectRepositoryLoader, properties, issuesLoader, userRepositoryLoader,
      cacheStatus);
    sync.load(false);

    verifyNoMoreInteractions(ws);
  }

  private String getResourceAsString(String name) throws IOException {
    URL resource = this.getClass().getResource(getClass().getSimpleName() + "/" + name);
    return Resources.toString(resource, StandardCharsets.UTF_8);
  }

  private ByteSource getResourceAsByteSource(String name) throws IOException {
    URL resource = this.getClass().getResource(getClass().getSimpleName() + "/" + name);
    return Resources.asByteSource(resource);
  }
}
