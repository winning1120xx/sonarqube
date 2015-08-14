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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import java.util.List;
import javax.annotation.Nonnull;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.api.server.ws.Request;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.permission.UserCountByProjectAndPermissionDto;
import org.sonar.server.component.ComponentFinder;

import static java.util.Collections.singletonList;
import static org.sonar.api.server.ws.WebService.Param.PAGE;
import static org.sonar.api.server.ws.WebService.Param.PAGE_SIZE;
import static org.sonar.api.server.ws.WebService.Param.TEXT_QUERY;
import static org.sonar.server.permission.ws.PermissionWsCommons.PARAM_PROJECT_KEY;
import static org.sonar.server.permission.ws.PermissionWsCommons.PARAM_PROJECT_UUID;
import static org.sonar.server.permission.ws.SearchProjectPermissionsData.newBuilder;

public class SearchProjectPermissionsDataLoader {
  private final DbClient dbClient;
  private final ComponentFinder componentFinder;

  public SearchProjectPermissionsDataLoader(DbClient dbClient, ComponentFinder componentFinder) {
    this.dbClient = dbClient;
    this.componentFinder = componentFinder;
  }

  public SearchProjectPermissionsData load(Request wsRequest) {
    DbSession dbSession = dbClient.openSession(false);
    try {
      SearchProjectPermissionsData.Builder data = newBuilder();
      List<ComponentDto> rootComponents = searchRootComponents(dbSession, wsRequest);
      List<Long> rootComponentIds = Lists.transform(rootComponents, ComponentToIdFunction.INSTANCE);

      data.rootComponents(rootComponents)
        .total(countRootComponents(dbSession, wsRequest))
        .userCountByProjectIdAndPermission(userCountByRootComponentIdAndPermission(dbSession, rootComponentIds))
        .groupCountByProjectIdAndPermission(groupCountByRootComponentIdAndPermission(dbSession, rootComponentIds));

      return data.build();
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  private int countRootComponents(DbSession dbSession, Request wsRequest) {
    return dbClient.componentDao().countRootComponents(dbSession, wsRequest.param(TEXT_QUERY));
  }

  private List<ComponentDto> searchRootComponents(DbSession dbSession, Request wsRequest) {
    int page = wsRequest.mandatoryParamAsInt(PAGE);
    int pageSize = wsRequest.mandatoryParamAsInt(PAGE_SIZE);
    String query = wsRequest.param(TEXT_QUERY);
    String projectUuid = wsRequest.param(PARAM_PROJECT_UUID);
    String projectKey = wsRequest.param(PARAM_PROJECT_KEY);

    if (projectUuid != null || projectKey != null) {
      return singletonList(componentFinder.getProjectByUuidOrKey(dbSession, projectUuid, projectKey));
    }

    return dbClient.componentDao().selectRootComponents(dbSession, page, pageSize, query);
  }

  private Table<Long, String, Integer> userCountByRootComponentIdAndPermission(DbSession dbSession, List<Long> rootComponentIds) {
    final Table<Long, String, Integer> userCountByRootComponentIdAndPermission = TreeBasedTable.create();

    dbClient.permissionDao().usersCountByProjectIdAndPermission(dbSession, rootComponentIds, new ResultHandler() {
      @Override
      public void handleResult(ResultContext context) {
        UserCountByProjectAndPermissionDto row = (UserCountByProjectAndPermissionDto) context.getResultObject();
        userCountByRootComponentIdAndPermission.put(row.getProjectId(), row.getPermission(), row.getUserCount());
      }
    });

    return userCountByRootComponentIdAndPermission;
  }

  private Table<Long, String, Integer> groupCountByRootComponentIdAndPermission(DbSession dbSession, List<Long> rootComponentIds) {
    final Table<Long, String, Integer> userCountByRootComponentIdAndPermission = TreeBasedTable.create();

    dbClient.permissionDao().groupsCountByProjectIdAndPermission(dbSession, rootComponentIds, new ResultHandler() {
      @Override
      public void handleResult(ResultContext context) {
        UserCountByProjectAndPermissionDto row = (UserCountByProjectAndPermissionDto) context.getResultObject();
        userCountByRootComponentIdAndPermission.put(row.getProjectId(), row.getPermission(), row.getUserCount());
      }
    });

    return userCountByRootComponentIdAndPermission;
  }

  private enum ComponentToIdFunction implements Function<ComponentDto, Long> {
    INSTANCE;

    @Override
    public Long apply(@Nonnull ComponentDto component) {
      return component.getId();
    }
  }
}
