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
package org.sonar.server.rule.ws;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.server.rule.Rule;
import org.sonar.server.search.QueryContext;
import org.sonar.server.user.UserSession;

public class RuleJsonWriter {

  private static final Set<String> CONCISE_FIELDS = ImmutableSet.of("key", "name", "lang", "langName", "status");

  private final RuleMapping mapping;
  private final UserSession userSession;

  public RuleJsonWriter(RuleMapping mapping, UserSession userSession) {
    this.mapping = mapping;
    this.userSession = userSession;
  }

  public void write(JsonWriter json, Rule rule) {
    QueryContext queryContext = new QueryContext(userSession).setFieldsToReturn(CONCISE_FIELDS);
    mapping.write(rule, json, queryContext);
  }
}
