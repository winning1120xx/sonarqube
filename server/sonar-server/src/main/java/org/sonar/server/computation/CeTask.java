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

import com.google.common.base.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;
import org.sonar.db.ce.CeQueueDto;

@Immutable
public class CeTask {

  private final String uuid;
  private final String type;
  private final String componentUuid;
  private final String submitterLogin;

  CeTask(CeTaskSubmit submit) {
    this.uuid = submit.getUuid();
    this.type = submit.getType();
    this.componentUuid = submit.getComponentUuid();
    this.submitterLogin = submit.getSubmitterLogin();
  }

  CeTask(CeQueueDto dto) {
    this.uuid = dto.getUuid();
    this.type = dto.getTaskType();
    this.componentUuid = dto.getComponentUuid();
    this.submitterLogin = dto.getSubmitterLogin();
  }

  public String getUuid() {
    return uuid;
  }

  public String getType() {
    return type;
  }

  @CheckForNull
  public String getComponentUuid() {
    return componentUuid;
  }

  @CheckForNull
  public String getSubmitterLogin() {
    return submitterLogin;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
      .add("componentUuid", componentUuid)
      .add("uuid", uuid)
      .add("type", type)
      .add("submitterLogin", submitterLogin)
      .toString();
  }
}
