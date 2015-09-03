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

package org.sonar.server.batch;

import java.io.ByteArrayInputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.sonar.api.platform.Server;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.batch.protocol.Constants.Severity;
import org.sonar.batch.protocol.input.BatchInput.ServerIssue;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentTesting;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.rule.RuleTesting;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.issue.IssueTesting;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsTester;
import org.sonar.test.DbTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Category(DbTests.class)
public class IssuesActionTest {

  private final static String PROJECT_KEY = "struts";
  private final static String MODULE_KEY = "struts-core";
  private final static String FILE_KEY = "Action.java";

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();

  WsTester tester;
  IssuesAction underTest;

  @Before
  public void before() {
    db.truncateTables();

    underTest = new IssuesAction(db.getDbClient(), userSessionRule, new ComponentFinder(db.getDbClient()));

    tester = new WsTester(new BatchWs(new BatchIndex(mock(Server.class)), underTest));
  }

  @Test
  public void load_issues() throws Exception {
    RuleDto rule = RuleTesting.newDto(RuleKey.of("java", "S001"));
    ComponentDto project = ComponentTesting.newProjectDto("PRJ").setKey(PROJECT_KEY);
    ComponentDto module = ComponentTesting.newModuleDto("MOD", project).setKey(MODULE_KEY);
    ComponentDto file = ComponentTesting.newFileDto(module, "FIL").setKey(FILE_KEY).setPath(null);
    db.getDbClient().componentDao().insert(db.getSession(), project, module, file);
    db.getDbClient().ruleDao().insert(db.getSession(), rule);
    IssueDto issue = IssueTesting.newDto(rule, file, project)
      .setKee("ISSUE_UUID")
      .setSeverity("BLOCKER")
      .setStatus("RESOLVED")
      .setResolution("FIXED")
      .setManualSeverity(false)
      .setMessage("msg")
      .setLine(42)
      .setChecksum("ABCDE")
      .setAssignee("john");
    db.getDbClient().issueDao().insert(db.getSession(), issue);
    db.getSession().commit();

    userSessionRule.login("henry")
      .setGlobalPermissions(GlobalPermissions.PREVIEW_EXECUTION)
      .addProjectUuidPermissions(UserRole.USER, "PRJ");

    // load the issues all the project
    WsTester.TestRequest request = tester.newGetRequest("batch", "issues").setParam("key", PROJECT_KEY);
    ServerIssue serverIssue = ServerIssue.parseDelimitedFrom(new ByteArrayInputStream(request.execute().output()));
    assertThat(serverIssue.getKey()).isEqualTo("ISSUE_UUID");
    assertThat(serverIssue.getModuleKey()).isEqualTo(MODULE_KEY);
    assertThat(serverIssue.hasPath()).isFalse();
    assertThat(serverIssue.getRuleRepository()).isEqualTo("java");
    assertThat(serverIssue.getRuleKey()).isEqualTo("S001");
    assertThat(serverIssue.getLine()).isEqualTo(42);
    assertThat(serverIssue.getMsg()).isEqualTo("msg");
    assertThat(serverIssue.getResolution()).isEqualTo("FIXED");
    assertThat(serverIssue.getStatus()).isEqualTo("RESOLVED");
    assertThat(serverIssue.getSeverity()).isEqualTo(Severity.BLOCKER);
    assertThat(serverIssue.getManualSeverity()).isFalse();
    assertThat(serverIssue.getChecksum()).isEqualTo("ABCDE");
    assertThat(serverIssue.getAssigneeLogin()).isEqualTo("john");

    // load the issues all the module
    request = tester.newGetRequest("batch", "issues").setParam("key", MODULE_KEY);
    serverIssue = ServerIssue.parseDelimitedFrom(new ByteArrayInputStream(request.execute().output()));
    assertThat(serverIssue.getModuleKey()).isEqualTo(MODULE_KEY);
  }

  @Test
  public void load_the_issues_associated_directly_to_module() throws Exception {
    RuleDto rule = RuleTesting.newDto(RuleKey.of("java", "S001"));
    ComponentDto project = ComponentTesting.newProjectDto("PRJ").setKey(PROJECT_KEY);
    ComponentDto module = ComponentTesting.newModuleDto("MOD", project).setKey(MODULE_KEY);
    db.getDbClient().componentDao().insert(db.getSession(), project, module);
    db.getDbClient().ruleDao().insert(db.getSession(), rule);
    IssueDto issue = IssueTesting.newDto(rule, module, project)
      .setKee("ISSUE_UUID")
      .setSeverity("BLOCKER")
      .setStatus("RESOLVED")
      .setResolution("FIXED")
      .setManualSeverity(false)
      .setMessage("msg")
      .setLine(42)
      .setChecksum("ABCDE")
      .setAssignee("john");
    db.getDbClient().issueDao().insert(db.getSession(), issue);
    db.getSession().commit();

    userSessionRule.login("henry")
      .setGlobalPermissions(GlobalPermissions.PREVIEW_EXECUTION)
      .addProjectUuidPermissions(UserRole.USER, "PRJ");

    WsTester.TestRequest request = tester.newGetRequest("batch", "issues").setParam("key", PROJECT_KEY);
    ServerIssue serverIssue = ServerIssue.parseDelimitedFrom(new ByteArrayInputStream(request.execute().output()));

    // the moduleKey of the issue is the module, but the not the module parent (as defined in table PROJECTS)
    assertThat(serverIssue.getKey()).isEqualTo("ISSUE_UUID");
    assertThat(serverIssue.getModuleKey()).isEqualTo(MODULE_KEY);
  }

  @Test(expected = ForbiddenException.class)
  public void fail_without_preview_permission() throws Exception {
    userSessionRule.login("henry").setGlobalPermissions(GlobalPermissions.PROVISIONING);

    WsTester.TestRequest request = tester.newGetRequest("batch", "issues").setParam("key", PROJECT_KEY);
    request.execute();
  }
}
