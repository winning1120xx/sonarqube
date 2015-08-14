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

import com.google.common.collect.Table;
import java.util.List;
import java.util.TreeSet;
import org.sonar.db.component.ComponentDto;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.ImmutableTable.copyOf;

public class SearchProjectPermissionsData {
  private final List<ComponentDto> projects;
  private final int total;
  private final Table<Long, String, Integer> userCountByProjectIdAndPermission;
  private final Table<Long, String, Integer> groupCountByProjectIdAndPermission;

  private SearchProjectPermissionsData(Builder builder) {
    this.projects = copyOf(builder.projects);
    this.total = builder.total;
    this.userCountByProjectIdAndPermission = copyOf(builder.userCountByProjectIdAndPermission);
    this.groupCountByProjectIdAndPermission = copyOf(builder.groupCountByProjectIdAndPermission);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public List<ComponentDto> rootComponents() {
    return projects;
  }

  public int total() {
    return total;
  }

  public Table<Long, String, Integer> userCountByProjectIdAndPermission() {
    return userCountByProjectIdAndPermission;
  }

  public Table<Long, String, Integer> groupCountByProjectIdAndPermission() {
    return groupCountByProjectIdAndPermission;
  }

  public TreeSet<String> permissions(Long rootComponentId) {
    TreeSet<String> permissions = new TreeSet<>();
    permissions.addAll(userCountByProjectIdAndPermission.row(rootComponentId).keySet());
    permissions.addAll(groupCountByProjectIdAndPermission.row(rootComponentId).keySet());
    return permissions;
  }

  public static class Builder {
    private List<ComponentDto> projects;
    private int total;
    private Table<Long, String, Integer> userCountByProjectIdAndPermission;
    private Table<Long, String, Integer> groupCountByProjectIdAndPermission;

    private Builder() {
      // initialized by main class
    }

    public SearchProjectPermissionsData build() {
      checkState(projects != null);
      checkState(userCountByProjectIdAndPermission != null);
      checkState(groupCountByProjectIdAndPermission != null);

      return new SearchProjectPermissionsData(this);
    }

    public Builder rootComponents(List<ComponentDto> projects) {
      this.projects = projects;
      return this;
    }

    public Builder total(int total) {
      this.total = total;
      return this;
    }

    public Builder userCountByProjectIdAndPermission(Table<Long, String, Integer> userCountByProjectIdAndPermission) {
      this.userCountByProjectIdAndPermission = userCountByProjectIdAndPermission;
      return this;
    }

    public Builder groupCountByProjectIdAndPermission(Table<Long, String, Integer> groupCountByProjectIdAndPermission) {
      this.groupCountByProjectIdAndPermission = groupCountByProjectIdAndPermission;
      return this;
    }
  }
}
