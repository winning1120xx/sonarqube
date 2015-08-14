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

package org.sonar.server.permission.ws;

import org.sonar.api.i18n.I18n;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.core.permission.ComponentPermissions;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.Permissions.Permission;
import org.sonarqube.ws.Permissions.SearchProjectPermissionsResponse;
import org.sonarqube.ws.Permissions.SearchProjectPermissionsResponse.Project;

import static org.sonar.server.permission.ws.PermissionWsCommons.PARAM_PROJECT_KEY;
import static org.sonar.server.permission.ws.PermissionWsCommons.PARAM_PROJECT_UUID;
import static org.sonar.server.permission.ws.PermissionWsCommons.createProjectKeyParameter;
import static org.sonar.server.permission.ws.PermissionWsCommons.createProjectUuidParameter;
import static org.sonar.server.ws.WsUtils.checkRequest;
import static org.sonar.server.ws.WsUtils.writeProtobuf;

public class SearchProjectPermissionsAction implements PermissionsWsAction {
  private static final String PROPERTY_PREFIX = "global_permissions.";
  private static final String DESCRIPTION_SUFFIX = ".desc";

  private final DbClient dbClient;
  private final UserSession userSession;
  private final I18n i18n;
  private final SearchProjectPermissionsDataLoader dataLoader;

  public SearchProjectPermissionsAction(DbClient dbClient, UserSession userSession, I18n i18n, SearchProjectPermissionsDataLoader dataLoader) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.i18n = i18n;
    this.dataLoader = dataLoader;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction("search_project_permissions")
      .setDescription("List project permissions. <br />" +
        "Requires 'Administer System' permission.")
      .setResponseExample(getClass().getResource(""))
      .setSince("5.2")
      .addPagingParams(25)
      .addSearchQuery("sonarq", "names", "keys")
      .setHandler(this);

    createProjectUuidParameter(action);
    createProjectKeyParameter(action);
  }

  @Override
  public void handle(Request wsRequest, Response wsResponse) throws Exception {
    checkWsRequest(wsRequest);
    checkPermissions();

    DbSession dbSession = dbClient.openSession(false);
    try {
      SearchProjectPermissionsData data = dataLoader.load(wsRequest);
      SearchProjectPermissionsResponse response = response(data);
      writeProtobuf(response, wsRequest, wsResponse);
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  private static void checkWsRequest(Request wsRequest) {
    String projectUuid = wsRequest.param(PARAM_PROJECT_UUID);
    String projectKey = wsRequest.param(PARAM_PROJECT_KEY);

    if (projectUuid != null || projectKey != null) {
      checkRequest(projectUuid != null ^ projectKey != null, "Project id or project key can be provided, not both.");
    }
  }

  private SearchProjectPermissionsResponse response(SearchProjectPermissionsData data) {
    SearchProjectPermissionsResponse.Builder response = SearchProjectPermissionsResponse.newBuilder();
    Permission.Builder permissionResponse = Permission.newBuilder();

    Project.Builder rootComponentBuilder = Project.newBuilder();
    for (ComponentDto rootComponent : data.rootComponents()) {
      rootComponentBuilder
        .clear()
        .setUuid(rootComponent.uuid())
        .setKey(rootComponent.key())
        .setName(rootComponent.name());
      for (String permission : data.permissions(rootComponent.getId())) {
        rootComponentBuilder.addPermissions(
          permissionResponse
            .clear()
            .setKey(permission)
            .setUsersCount(data.userCountByProjectIdAndPermission().get(rootComponent.getId(), permission))
            .setGroupsCount(data.groupCountByProjectIdAndPermission().get(rootComponent.getId(), permission)));
      }
      response.addProjects(rootComponentBuilder);
    }

    for (String permissionKey : ComponentPermissions.ALL) {
      response.addPermissions(
        permissionResponse
          .clear()
          .setKey(permissionKey)
          .setName(i18nName(permissionKey))
          .setDescription(i18nDescriptionMessage(permissionKey))
      );
    }

    return response.build();
  }

  private void checkPermissions() {
    userSession
      .checkLoggedIn()
      .checkGlobalPermission(GlobalPermissions.SYSTEM_ADMIN);
  }

  private String i18nDescriptionMessage(String permissionKey) {
    return i18n.message(userSession.locale(), PROPERTY_PREFIX + permissionKey + DESCRIPTION_SUFFIX, "");
  }

  private String i18nName(String permissionKey) {
    return i18n.message(userSession.locale(), PROPERTY_PREFIX + permissionKey, permissionKey);
  }
}
