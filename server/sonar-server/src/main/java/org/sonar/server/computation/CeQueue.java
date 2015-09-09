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

import com.google.common.base.Optional;
import org.picocontainer.Startable;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.System2;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.server.computation.monitoring.CEQueueStatus;

import static java.lang.String.format;

@ServerSide
public class CeQueue implements Startable {

  private final System2 system2;
  private final DbClient dbClient;
  private final UuidFactory uuidFactory;
  private final CeQueueCleaner cleaner;
  private final CEQueueStatus queueStatus;

  // state
  private boolean submitPaused = false;
  private boolean peekPaused = false;

  public CeQueue(System2 system2, DbClient dbClient, UuidFactory uuidFactory,
                 CeQueueCleaner cleaner, CEQueueStatus queueStatus) {
    this.system2 = system2;
    this.dbClient = dbClient;
    this.uuidFactory = uuidFactory;
    this.cleaner = cleaner;
    this.queueStatus = queueStatus;
  }

  @Override
  public void start() {
    DbSession dbSession = dbClient.openSession(false);
    try {
      cleaner.clean(dbSession);

      // initialize JMX counters
      queueStatus.initPendingCount(dbClient.ceQueueDao().countByStatus(dbSession, CeQueueDto.Status.PENDING));
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  @Override
  public void stop() {
    // nothing to do
  }

  public CeTaskSubmit prepareSubmit() {
    return new CeTaskSubmit(uuidFactory.create());
  }

  public CeTask submit(CeTaskSubmit submit) {
    if (submitPaused) {
      throw new IllegalStateException("Compute Engine does not currently accept new tasks");
    }
    CeTask task = new CeTask(submit);
    DbSession dbSession = dbClient.openSession(false);
    try {
      CeQueueDto dto = new CeQueueDto();
      dto.setUuid(task.getUuid());
      dto.setTaskType(task.getType());
      dto.setComponentUuid(task.getComponentUuid());
      dto.setStatus(CeQueueDto.Status.PENDING);
      dto.setSubmitterLogin(task.getSubmitterLogin());
      dto.setStartedAt(null);
      dbClient.ceQueueDao().insert(dbSession, dto);
      dbSession.commit();
      queueStatus.addReceived();
      return task;
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  public Optional<CeTask> peek() {
    if (peekPaused) {
      return Optional.absent();
    }
    DbSession dbSession = dbClient.openSession(false);
    try {
      Optional<CeQueueDto> dto = dbClient.ceQueueDao().peek(dbSession);
      if (!dto.isPresent()) {
        return Optional.absent();
      }
      return Optional.of(new CeTask(dto.get()));

    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  public boolean cancel(String taskUuid) {
    DbSession dbSession = dbClient.openSession(false);
    try {
      Optional<CeQueueDto> queueDto = dbClient.ceQueueDao().selectByUuid(dbSession, taskUuid);
      if (queueDto.isPresent()) {
        if (!queueDto.get().getStatus().equals(CeQueueDto.Status.PENDING)) {
          throw new IllegalStateException(String.format("Task is in progress and can't be cancelled [uuid=%s]", taskUuid));
        }
        CeActivityDto activityDto = new CeActivityDto(queueDto.get());
        activityDto.setStatus(CeActivityDto.Status.CANCELED);
        dbClient.ceQueueDao().deleteByUuid(dbSession, queueDto.get().getUuid());
        dbClient.ceActivityDao().insert(dbSession, activityDto);
        dbSession.commit();
        return true;
      }
      return false;
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  /**
   * Cancel all tasks with status {@link org.sonar.db.ce.CeQueueDto.Status#PENDING}.
   * Tasks with status {@link org.sonar.db.ce.CeQueueDto.Status#IN_PROGRESS} are
   * ignored and are not canceled.
   */
  public int cancelAll() {
    int count = 0;
    DbSession dbSession = dbClient.openSession(false);
    try {
      for (CeQueueDto queueDto : dbClient.ceQueueDao().selectAll(dbSession)) {
        if (queueDto.getStatus().equals(CeQueueDto.Status.PENDING)) {
          CeActivityDto historyDto = new CeActivityDto(queueDto);
          historyDto.setStatus(CeActivityDto.Status.CANCELED);
          dbClient.ceActivityDao().insert(dbSession, historyDto);
          dbClient.ceQueueDao().deleteByUuid(dbSession, queueDto.getUuid());
          count++;
        }
      }
      dbSession.commit();
      return count;
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  public void remove(CeTask task, CeActivityDto.Status status) {
    DbSession dbSession = dbClient.openSession(false);
    try {
      Optional<CeQueueDto> queueDto = dbClient.ceQueueDao().selectByUuid(dbSession, task.getUuid());
      if (!queueDto.isPresent()) {
        throw new IllegalStateException(format("Task does not exist anymore: %s", task));
      }
      CeActivityDto historyDto = new CeActivityDto(queueDto.get());
      historyDto.setStatus(status);
      Long startedAt = historyDto.getStartedAt();
      if (startedAt != null) {
        historyDto.setFinishedAt(system2.now());
        historyDto.setExecutionTimeMs(historyDto.getFinishedAt() - startedAt);
      }

      dbClient.ceQueueDao().deleteByUuid(dbSession, queueDto.get().getUuid());
      dbClient.ceActivityDao().insert(dbSession, historyDto);
      dbSession.commit();
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  public void pauseSubmit() {
    this.submitPaused = true;
  }

  public void resumeSubmit() {
    this.submitPaused = false;
  }

  public boolean isSubmitPaused() {
    return submitPaused;
  }

  public void pausePeek() {
    this.peekPaused = true;
  }

  public void resumePeek() {
    this.peekPaused = false;
  }

  public boolean isPeekPaused() {
    return peekPaused;
  }
}
