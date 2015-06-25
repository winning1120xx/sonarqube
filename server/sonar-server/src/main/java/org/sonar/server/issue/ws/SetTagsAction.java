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
package org.sonar.server.issue.ws;

import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import org.sonar.api.issue.ActionPlan;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.internal.DefaultIssueComment;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.NewAction;
import org.sonar.api.user.User;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.core.component.ComponentDto;
import org.sonar.server.issue.IssueService;

/**
 * Set tags on an issue
 */
public class SetTagsAction implements IssuesWsAction {

  private final IssueService service;

  private final IssueJsonWriter issueWriter;

  public SetTagsAction(IssueService service, IssueJsonWriter issueWriter) {
    this.service = service;
    this.issueWriter = issueWriter;
  }

  @Override
  public void define(WebService.NewController controller) {
    NewAction action = controller.createAction("set_tags")
      .setHandler(this)
      .setPost(true)
      .setSince("5.1")
      .setDescription("Set tags on an issue. Requires authentication and Browse permission on project");
    action.createParam("key")
      .setDescription("Issue key")
      .setExampleValue("5bccd6e8-f525-43a2-8d76-fcb13dde79ef")
      .setRequired(true);
    action.createParam("tags")
      .setDescription("Comma-separated list of tags. All tags are removed if parameter is empty or not set.")
      .setExampleValue("security,cwe,misra-c");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    String key = request.mandatoryParam("key");
    List<String> tags = Objects.firstNonNull(request.paramAsStrings("tags"), Collections.<String>emptyList());
    service.setTags(key, tags);
    Issue updatedIssue = service.getByKey(key);
    JsonWriter json = response.newJsonWriter().beginObject().name("issue");
    issueWriter.write(json, updatedIssue,
      Maps.<String, User>newHashMap(),
      Maps.<String, ComponentDto>newHashMap(),
      Maps.<String, ComponentDto>newHashMap(),
      HashMultimap.<String, DefaultIssueComment>create(),
      Maps.<String, ActionPlan>newHashMap(), null);
    json.endObject().close();
  }

}
