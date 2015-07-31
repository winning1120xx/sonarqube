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

package org.sonar.server.metric;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.compute.MetricsDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MetricsImplTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MetricsImpl underTest = new MetricsImpl();

  @Test
  public void get_metrics() throws Exception {
    underTest.setMetrics(Collections.singletonList(newMetric("key")));

    assertThat(underTest.getMetrics()).hasSize(1);
  }

  @Test
  public void get_metric_by_key() throws Exception {
    underTest.setMetrics(Collections.singletonList(newMetric("key")));

    assertThat(underTest.getMetric("key")).isNotNull();
  }

  @Test
  public void get_metrics_throws_ISE_when_not_initialized() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Metrics have not been initialized yet");

    underTest.getMetrics();
  }

  @Test
  public void get_metric_by_key_throws_ISE_when_not_initialized() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Metrics have not been initialized yet");

    underTest.getMetric("key");
  }

  @Test
  public void has_metric() throws Exception {
    underTest.setMetrics(Collections.singletonList(newMetric("key")));

    assertThat(underTest.hasMetric("key")).isTrue();
    assertThat(underTest.hasMetric("unknown")).isFalse();
  }

  @Test
  public void has_metric_throws_ISE_when_not_initialized() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Metrics have not been initialized yet");

    underTest.hasMetric("key");
  }

  @Test
  public void set_metrics_fail_with_NPE_on_null_value() throws Exception {
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("Metrics cannot be null");

    underTest.setMetrics(null);
  }

  @Test
  public void set_metrics_fail_with_ISE_when_already_initialized() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Metrics have already been initialized");

    underTest.setMetrics(Collections.singletonList(newMetric("key")));
    underTest.setMetrics(Collections.singletonList(newMetric("key")));
  }

  @Test
  public void set_metrics_supports_empty_arg_is_empty() {
    underTest.setMetrics(ImmutableList.<MetricsDefinition.Metric>of());

    assertThat(underTest.getMetrics()).isEmpty();
  }

  private MetricsDefinition.Metric newMetric(String metricKey) {
    MetricsDefinition.Metric metric = mock(MetricsDefinition.Metric.class);
    when(metric.getKey()).thenReturn(metricKey);
    return metric;
  }


}
