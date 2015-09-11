/*
 * Orchestrator
 * Copyright (C) 2011 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.sonar.orchestrator.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public final class NetworkUtils {
  private NetworkUtils() {
  }

  public static int getNextAvailablePort() {
    System.out.println("=== Override method provided by orchestrator");

    try (ServerSocket socket = new ServerSocket()) {
      socket.bind(new InetSocketAddress("0.0.0.0", 0));
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Can't find a free network port", e);
    }
  }
}
