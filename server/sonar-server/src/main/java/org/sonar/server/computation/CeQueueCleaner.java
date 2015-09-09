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
package org.sonar.server.computation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sonar.api.platform.ServerUpgradeStatus;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeTaskTypes;

import static java.lang.String.format;
import static org.sonar.db.ce.CeActivityDto.Status.CANCELED;

@ServerSide
public class CeQueueCleaner {

  private static final Logger LOGGER = Loggers.get(CeQueueCleaner.class);

  private final DbClient dbClient;
  private final ServerUpgradeStatus serverUpgradeStatus;
  private final ReportFiles reportFiles;

  public CeQueueCleaner(DbClient dbClient, ServerUpgradeStatus serverUpgradeStatus, ReportFiles reportFiles) {
    this.dbClient = dbClient;
    this.serverUpgradeStatus = serverUpgradeStatus;
    this.reportFiles = reportFiles;
  }

  public void clean(DbSession dbSession) {
    LOGGER.info("Clean-up pending tasks of Compute Engine");
    if (serverUpgradeStatus.isUpgraded()) {
      cleanOnUpgrade(dbSession);
    } else {
      verifyConsistency(dbSession);
    }
  }

  private void cleanOnUpgrade(DbSession dbSession) {
    // we assume that pending tasks are not compatible with the new version
    // and can't be processed
    List<CeQueueDto> queueDtos = dbClient.ceQueueDao().selectAll(dbSession);
    LOGGER.info(format("Cancel all the %d pending tasks (due to upgrade)", queueDtos.size()));
    for (CeQueueDto queueDto : queueDtos) {
      cancel(dbSession, queueDto);
    }
    reportFiles.deleteAll();
  }

  private void verifyConsistency(DbSession dbSession) {
    // server is not being upgraded
    dbClient.ceQueueDao().resetAllToPendingStatus(dbSession);
    dbSession.commit();

    // verify that the report files are available for the tasks in queue
    Set<String> uuidsInQueue = new HashSet<>();
    for (CeQueueDto queueDto : dbClient.ceQueueDao().selectAll(dbSession)) {
      uuidsInQueue.add(queueDto.getUuid());
      if (CeTaskTypes.REPORT.equals(queueDto.getTaskType()) && !reportFiles.fileForUuid(queueDto.getUuid()).exists()) {
        // the report is not available on file system
        cancel(dbSession, queueDto);
      }
    }

    // clean-up filesystem
    for (String uuid : reportFiles.listUuids()) {
      if (!uuidsInQueue.contains(uuid)) {
        reportFiles.deleteIfExists(uuid);
      }
    }
  }

  private void cancel(DbSession dbSession, CeQueueDto queueDto) {
    CeActivityDto activityDto = new CeActivityDto(queueDto);
    activityDto.setStatus(CANCELED);
    dbClient.ceActivityDao().insert(dbSession, activityDto);
    dbClient.ceQueueDao().deleteByUuid(dbSession, queueDto.getUuid());
    dbSession.commit();
  }
}
