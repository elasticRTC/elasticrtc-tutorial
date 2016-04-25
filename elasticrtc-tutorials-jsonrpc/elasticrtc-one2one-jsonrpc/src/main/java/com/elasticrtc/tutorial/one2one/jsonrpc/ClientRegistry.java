/*
 * (C) Copyright 2016 elasticRTC (https://www.elasticRTC.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.elasticrtc.tutorial.one2one.jsonrpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.jsonrpc.Session;

/**
 * Map of users registered in the system. This class has a concurrent hash map to store users, using
 * its name as key in the map.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 1.0.0
 */
public class ClientRegistry {

  private Map<String, Client> usersByName = new ConcurrentHashMap<>();
  private Map<String, Client> usersBySessionId = new ConcurrentHashMap<>();

  public void register(Client user) {
    usersByName.put(user.getName(), user);
    usersBySessionId.put(user.getSession().getSessionId(), user);
  }

  public Client getByName(String name) {
    return usersByName.get(name);
  }

  public Client getBySession(Session session) {
    return usersBySessionId.get(session.getSessionId());
  }

  public boolean contains(String name) {
    return usersByName.keySet().contains(name);
  }

  public Client removeBySession(Session session) {
    final Client user = getBySession(session);
    if (user != null) {
      usersByName.remove(user.getName());
      usersBySessionId.remove(session.getSessionId());
    }
    return user;
  }

}
