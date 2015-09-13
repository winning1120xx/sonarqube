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

package org.sonar.db.ce;

import com.google.common.base.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.internal.TestSystem2;
import org.sonar.db.DbTester;
import org.sonar.test.DbTests;

import static org.assertj.core.api.Assertions.assertThat;

@Category(DbTests.class)
public class CeActivityDaoTest {

  System2 system2 = new TestSystem2().setNow(1_450_000_000_000L);

  @Rule
  public DbTester db = DbTester.create(system2);

  CeActivityDao underTest = new CeActivityDao(system2);

  @Test
  public void test_insert() {
    insert("TASK_1", "PROJECT_1", CeActivityDto.Status.SUCCESS);

    Optional<CeActivityDto> saved = underTest.selectByUuid(db.getSession(), "TASK_1");
    assertThat(saved.isPresent()).isTrue();
    assertThat(saved.get().getUuid()).isEqualTo("TASK_1");
    assertThat(saved.get().getComponentUuid()).isEqualTo("PROJECT_1");
    assertThat(saved.get().getStatus()).isEqualTo(CeActivityDto.Status.SUCCESS);
    assertThat(saved.get().getSubmitterLogin()).isEqualTo("henri");
    assertThat(saved.get().getIsLast()).isTrue();
    assertThat(saved.get().getIsLastKey()).isEqualTo("REPORTPROJECT_1");
    assertThat(saved.get().getSubmittedAt()).isEqualTo(1_300_000_000_000L);
    assertThat(saved.get().getCreatedAt()).isEqualTo(1_450_000_000_000L);
    assertThat(saved.get().getStartedAt()).isEqualTo(1_500_000_000_000L);
    assertThat(saved.get().getFinishedAt()).isEqualTo(1_500_000_000_500L);
    assertThat(saved.get().getExecutionTimeMs()).isEqualTo(500L);
  }

  @Test
  public void insert_must_set_relevant_is_last_field() {
    // only a single task on PROJECT_1 -> is_last=true
    insert("TASK_1", "PROJECT_1", CeActivityDto.Status.SUCCESS);
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_1").get().getIsLast()).isTrue();

    // only a single task on PROJECT_2 -> is_last=true
    insert("TASK_2", "PROJECT_2", CeActivityDto.Status.SUCCESS);
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_2").get().getIsLast()).isTrue();

    // two tasks on PROJECT_1, the more recent one is TASK_3
    insert("TASK_3", "PROJECT_1", CeActivityDto.Status.FAILED);
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_1").get().getIsLast()).isFalse();
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_2").get().getIsLast()).isTrue();
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_3").get().getIsLast()).isTrue();
  }

  private void insert(String uuid, String componentUuid, CeActivityDto.Status status) {
    CeQueueDto queueDto = new CeQueueDto();
    queueDto.setUuid(uuid);
    queueDto.setTaskType("REPORT");
    queueDto.setComponentUuid(componentUuid);
    queueDto.setStatus(CeQueueDto.Status.IN_PROGRESS);
    queueDto.setSubmitterLogin("henri");
    queueDto.setCreatedAt(1_300_000_000_000L);

    CeActivityDto dto = new CeActivityDto(queueDto);
    dto.setStatus(status);
    dto.setStartedAt(1_500_000_000_000L);
    dto.setFinishedAt(1_500_000_000_500L);
    dto.setExecutionTimeMs(500L);
    underTest.insert(db.getSession(), dto);
  }
}
