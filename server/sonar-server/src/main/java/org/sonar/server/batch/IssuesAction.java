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

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.batch.protocol.input.BatchInput;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.plugins.MimeTypes;
import org.sonar.server.user.UserSession;

public class IssuesAction implements BatchWsAction {

  private static final String PARAM_KEY = "key";

  private final DbClient dbClient;
  private final UserSession userSession;
  private final ComponentFinder componentFinder;

  public IssuesAction(DbClient dbClient, UserSession userSession, ComponentFinder componentFinder) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.componentFinder = componentFinder;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("issues")
      .setDescription("Return open issues")
      .setSince("5.1")
      .setInternal(true)
      .setHandler(this);

    action
      .createParam(PARAM_KEY)
      .setRequired(true)
      .setDescription("Key of module or project")
      .setExampleValue("org.codehaus.sonar:sonar");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkGlobalPermission(GlobalPermissions.PREVIEW_EXECUTION);

    String moduleOrProjectKey = request.mandatoryParam(PARAM_KEY);

    DbSession dbSession = dbClient.openSession(false);
    try {
      ComponentDto moduleOrProject = componentFinder.getByKey(dbSession, moduleOrProjectKey);
      userSession.checkProjectUuidPermission(UserRole.USER, moduleOrProject.projectUuid());

      ModuleKeys moduleKeys = new ModuleKeys(dbClient, dbSession, moduleOrProject);
      response.stream().setMediaType(MimeTypes.PROTOBUF);
      DbScrollHandler dbScrollHandler = new DbScrollHandler(moduleKeys, response.stream().output());
      dbClient.issueDao().selectNonClosedIssuesByModuleOrProjectUuid(dbSession, moduleOrProject.uuid(), dbScrollHandler);

    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  private static class ModuleKeys {
    private final Map<String, String> keysByUuids = new HashMap<>();

    public ModuleKeys(DbClient dbClient, DbSession dbSession, ComponentDto moduleOrProject) {
      if (Qualifiers.MODULE.equals(moduleOrProject.qualifier())) {
        keysByUuids.put(moduleOrProject.uuid(), moduleOrProject.getKey());
      } else {
        List<ComponentDto> modulesAndProject = dbClient.componentDao().selectDescendantModules(dbSession, moduleOrProject.uuid());
        for (ComponentDto c : modulesAndProject) {
          keysByUuids.put(c.uuid(), c.key());
        }
      }
    }

    @CheckForNull
    public String get(IssueDto issue) {
      String moduleUuidPath = issue.getModuleUuidPath();
      if (moduleUuidPath != null) {
        return keysByUuids.get(ComponentDto.extractLeafModuleUuidFromPath(moduleUuidPath));
      }
      return null;
    }
  }

  private static class DbScrollHandler implements ResultHandler {
    private final BatchInput.ServerIssue.Builder outputBuilder = BatchInput.ServerIssue.newBuilder();
    private final ModuleKeys moduleKeys;
    private final OutputStream outputStream;

    public DbScrollHandler(ModuleKeys moduleKeys, OutputStream outputStream) {
      this.moduleKeys = moduleKeys;
      this.outputStream = outputStream;
    }

    @Override
    public void handleResult(ResultContext resultContext) {
      outputBuilder.clear();

      IssueDto dto = (IssueDto) resultContext.getResultObject();
      outputBuilder.setKey(dto.getKey());
      String moduleKey = moduleKeys.get(dto);
      if (moduleKey != null) {
        outputBuilder.setModuleKey(moduleKey);
      }
      String path = dto.getFilePath();
      if (path != null) {
        outputBuilder.setPath(path);
      }
      outputBuilder.setRuleRepository(dto.getRuleRepo());
      outputBuilder.setRuleKey(dto.getRule());
      String checksum = dto.getChecksum();
      if (checksum != null) {
        outputBuilder.setChecksum(checksum);
      }
      String assigneeLogin = dto.getAssignee();
      if (assigneeLogin != null) {
        outputBuilder.setAssigneeLogin(assigneeLogin);
      }
      Integer line = dto.getLine();
      if (line != null) {
        outputBuilder.setLine(line);
      }
      String message = dto.getMessage();
      if (message != null) {
        outputBuilder.setMsg(message);
      }
      outputBuilder.setSeverity(org.sonar.batch.protocol.Constants.Severity.valueOf(dto.getSeverity()));
      outputBuilder.setManualSeverity(dto.isManualSeverity());
      outputBuilder.setStatus(dto.getStatus());
      String resolution = dto.getResolution();
      if (resolution != null) {
        outputBuilder.setResolution(resolution);
      }
      outputBuilder.setCreationDate(dto.getIssueCreationTime());
      try {
        outputBuilder.build().writeDelimitedTo(outputStream);
      } catch (IOException e) {
        throw new IllegalStateException("Unable to serialize issue", e);
      }
    }
  }

}
