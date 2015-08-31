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

package org.sonar.batch.issue.tracking;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.rule.RuleKey;
import org.sonar.batch.protocol.output.BatchReport;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@BatchSide
public class IssueTracking {

  private FileHashes fileHashes;

  /**
   * @param sourceHashHolder Null when working on resource that is not a file (directory/project)
   */
  public IssueTrackingResult track(@Nullable FileHashes fileHashes, Collection<ServerIssue> previousIssues, Collection<BatchReport.Issue> rawIssues) {
    this.fileHashes = fileHashes;
    IssueTrackingResult result = new IssueTrackingResult();

    // Map new issues with old ones
    mapIssues(rawIssues, previousIssues, result);
    return result;
  }

  private String checksum(BatchReport.Issue rawIssue) {
    if (fileHashes != null && rawIssue.hasLine()) {
      // Extra verification if some plugin managed to create issue on a wrong line
      Preconditions.checkState(rawIssue.getLine() <= fileHashes.length(), "Invalid line number for issue %s. File has only %s line(s)", rawIssue, fileHashes.length());
      return fileHashes.getHash(rawIssue.getLine());
    }
    return null;
  }

  @VisibleForTesting
  void mapIssues(Collection<BatchReport.Issue> rawIssues, @Nullable Collection<ServerIssue> previousIssues,
    IssueTrackingResult result) {

    if (previousIssues != null) {
      mapLastIssues(rawIssues, previousIssues, result);
    }

    // If each new issue matches an old one we can stop the matching mechanism
    if (result.matched().size() != rawIssues.size()) {
      mapIssuesOnSameRule(rawIssues, result);
    }
  }

  private void mapLastIssues(Collection<BatchReport.Issue> rawIssues, Collection<ServerIssue> previousIssues, IssueTrackingResult result) {
    for (ServerIssue lastIssue : previousIssues) {
      result.addUnmatched(lastIssue);
    }

    // Try first to match issues on same rule with same line and with same checksum (but not necessarily with same message)
    for (BatchReport.Issue rawIssue : rawIssues) {
      if (isNotAlreadyMapped(rawIssue, result)) {
        mapIssue(
          rawIssue,
          findLastIssueWithSameLineAndChecksum(rawIssue, result),
          result);
      }
    }
  }

  private void mapIssuesOnSameRule(Collection<BatchReport.Issue> rawIssues, IssueTrackingResult result) {
    // Try then to match issues on same rule with same message and with same checksum
    for (BatchReport.Issue rawIssue : rawIssues) {
      if (isNotAlreadyMapped(rawIssue, result)) {
        mapIssue(
          rawIssue,
          findLastIssueWithSameChecksumAndMessage(rawIssue, result.unmatchedByKeyForRule(ruleKey(rawIssue)).values()),
          result);
      }
    }

    // Try then to match issues on same rule with same line and with same message
    for (BatchReport.Issue rawIssue : rawIssues) {
      if (isNotAlreadyMapped(rawIssue, result)) {
        mapIssue(
          rawIssue,
          findLastIssueWithSameLineAndMessage(rawIssue, result.unmatchedByKeyForRule(ruleKey(rawIssue)).values()),
          result);
      }
    }

    // Last check: match issue if same rule and same checksum but different line and different message
    // See SONAR-2812
    for (BatchReport.Issue rawIssue : rawIssues) {
      if (isNotAlreadyMapped(rawIssue, result)) {
        mapIssue(
          rawIssue,
          findLastIssueWithSameChecksum(rawIssue, result.unmatchedByKeyForRule(ruleKey(rawIssue)).values()),
          result);
      }
    }
  }

  private static RuleKey ruleKey(BatchReport.Issue rawIssue) {
    return RuleKey.of(rawIssue.getRuleRepository(), rawIssue.getRuleKey());
  }

  private ServerIssue findLastIssueWithSameChecksum(BatchReport.Issue rawIssue, Collection<ServerIssue> previousIssues) {
    for (ServerIssue previousIssue : previousIssues) {
      if (isSameChecksum(rawIssue, previousIssue)) {
        return previousIssue;
      }
    }
    return null;
  }

  private ServerIssue findLastIssueWithSameLineAndMessage(BatchReport.Issue rawIssue, Collection<ServerIssue> previousIssues) {
    for (ServerIssue previousIssue : previousIssues) {
      if (isSameLine(rawIssue, previousIssue) && isSameMessage(rawIssue, previousIssue)) {
        return previousIssue;
      }
    }
    return null;
  }

  private ServerIssue findLastIssueWithSameChecksumAndMessage(BatchReport.Issue rawIssue, Collection<ServerIssue> previousIssues) {
    for (ServerIssue previousIssue : previousIssues) {
      if (isSameChecksum(rawIssue, previousIssue) && isSameMessage(rawIssue, previousIssue)) {
        return previousIssue;
      }
    }
    return null;
  }

  private ServerIssue findLastIssueWithSameLineAndChecksum(BatchReport.Issue rawIssue, IssueTrackingResult result) {
    Collection<ServerIssue> sameRuleAndSameLineAndSameChecksum = result.unmatchedForRuleAndForLineAndForChecksum(ruleKey(rawIssue), line(rawIssue), checksum(rawIssue));
    if (!sameRuleAndSameLineAndSameChecksum.isEmpty()) {
      return sameRuleAndSameLineAndSameChecksum.iterator().next();
    }
    return null;
  }

  @CheckForNull
  private static Integer line(BatchReport.Issue rawIssue) {
    return rawIssue.hasLine() ? rawIssue.getLine() : null;
  }

  private static boolean isNotAlreadyMapped(BatchReport.Issue rawIssue, IssueTrackingResult result) {
    return !result.isMatched(rawIssue);
  }

  private boolean isSameChecksum(BatchReport.Issue rawIssue, ServerIssue previousIssue) {
    return Objects.equal(previousIssue.checksum(), checksum(rawIssue));
  }

  private boolean isSameLine(BatchReport.Issue rawIssue, ServerIssue previousIssue) {
    return Objects.equal(previousIssue.line(), line(rawIssue));
  }

  private boolean isSameMessage(BatchReport.Issue rawIssue, ServerIssue previousIssue) {
    return Objects.equal(message(rawIssue), previousIssue.message());
  }

  @CheckForNull
  private static String message(BatchReport.Issue rawIssue) {
    return rawIssue.hasMsg() ? rawIssue.getMsg() : null;
  }

  private static void mapIssue(BatchReport.Issue rawIssue, @Nullable ServerIssue ref, IssueTrackingResult result) {
    if (ref != null) {
      result.setMatch(rawIssue, ref);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

}
