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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.sonar.api.compute.MetricsDefinition;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public class MetricsImpl implements Metrics {

  Map<String, MetricsDefinition.Metric> metrics = null;

  public void setMetrics(List<MetricsDefinition.Metric> metrics) {
    requireNonNull(metrics, "Metrics cannot be null");
    checkState(this.metrics == null, "Metrics have already been initialized");
    this.metrics = FluentIterable.from(metrics).uniqueIndex(MetricToKey.INSTANCE);
  }

  @Override
  public List<MetricsDefinition.Metric> getMetrics() {
    checkInitialized();
    return ImmutableList.copyOf(metrics.values());
  }

  @Override
  public MetricsDefinition.Metric getMetric(String metricKey) {
    checkInitialized();
    MetricsDefinition.Metric metric = metrics.get(metricKey);
    checkState(metric != null, "No metric with key " + metricKey);
    return metric;
  }

  @Override
  public boolean hasMetric(String metricKey) {
    checkInitialized();
    return metrics.get(metricKey) != null;
  }

  private void checkInitialized() {
    checkState(this.metrics != null, "Metrics have not been initialized yet");
  }

  private enum MetricToKey implements Function<MetricsDefinition.Metric, String> {
    INSTANCE;

    @Nullable
    @Override
    public String apply(@Nonnull MetricsDefinition.Metric input) {
      return input.getKey();
    }
  }
}
