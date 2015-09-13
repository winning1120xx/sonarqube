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
package org.sonar.server.computation.ws;

import com.google.common.base.Optional;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.ws.WsUtils;
import org.sonarqube.ws.WsCe;

public class CeTaskWsAction implements CeWsAction {

  public static final String ACTION = "task";

  public static final String PARAM_TASK_ID = "id";

  private final DbClient dbClient;
  private final WsTaskFormater wsTaskFormater;

  public CeTaskWsAction(DbClient dbClient, WsTaskFormater wsTaskFormater) {
    this.dbClient = dbClient;
    this.wsTaskFormater = wsTaskFormater;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction(ACTION)
      .setDescription("Task information")
      .setInternal(true)
      .setHandler(this);

    action
      .createParam(PARAM_TASK_ID)
      .setRequired(true)
      .setDescription("Id of task")
      .setExampleValue(Uuids.UUID_EXAMPLE_01);
  }

  @Override
  public void handle(Request wsRequest, Response wsResponse) throws Exception {
    String taskId = wsRequest.mandatoryParam(PARAM_TASK_ID);
    DbSession dbSession = dbClient.openSession(false);
    try {
      WsCe.TaskResponse taskResponse;
      Optional<CeQueueDto> queueDto = dbClient.ceQueueDao().selectByUuid(dbSession, taskId);
      if (queueDto.isPresent()) {
        taskResponse = wsTaskFormater.format(queueDto.get());
      } else {
        Optional<CeActivityDto> activityDto = dbClient.ceActivityDao().selectByUuid(dbSession, taskId);
        if (activityDto.isPresent()) {
          taskResponse = wsTaskFormater.format(activityDto.get());
        } else {
          throw new NotFoundException();
        }
      }
      WsUtils.writeProtobuf(taskResponse, wsRequest, wsResponse);

    } finally {
      dbClient.closeSession(dbSession);
    }

  }
}
