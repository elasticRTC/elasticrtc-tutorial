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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * User session.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 1.0.0
 */
public class Client {

  private static final Logger log = LoggerFactory.getLogger(Client.class);
  private static final Gson gson = new GsonBuilder().create();

  private final String name;
  private final Session session;

  private WebRtcEndpoint webRtcEndpoint;
  private final List<IceCandidate> candidateList = new ArrayList<IceCandidate>();

  public Client(Session session, String name) {
    this.session = session;
    this.name = name;
  }

  public Session getSession() {
    return session;
  }

  public String getName() {
    return name;
  }

  public void processOffer(String sdpOffer) {
    this.webRtcEndpoint.processOffer(sdpOffer);
  }

  public String getSessionId() {
    return session.getSessionId();
  }

  public void setEndpoint(WebRtcEndpoint webRtcEndpoint) {

    if (this.webRtcEndpoint != null) {
      this.webRtcEndpoint.release();
    }

    this.webRtcEndpoint = webRtcEndpoint;

    this.webRtcEndpoint.addOnIceCandidateListener(event -> {
      try {
        synchronized (session) {
          session.sendNotification("iceCandidate", event.getCandidate());
        }
      } catch (IOException e) {
        log.debug(e.getMessage(), e);
      }
    });

    for (IceCandidate e : candidateList) {
      this.webRtcEndpoint.addIceCandidate(e);
    }
    this.candidateList.clear();
  }

  public void addCandidate(IceCandidate candidate) {
    if (this.webRtcEndpoint != null) {
      this.webRtcEndpoint.addIceCandidate(candidate);
    } else {
      candidateList.add(candidate);
    }
  }

  public void clear() {
    this.webRtcEndpoint.release();
    this.webRtcEndpoint = null;
    this.candidateList.clear();
  }

  public void sendNotification(String method) throws IOException {
    session.sendNotification(method);
  }

  public JsonElement sendRequest(String method, JsonObject params) throws IOException {
    return sendRequest(method, params, JsonElement.class);
  }

  public <T> T sendRequest(String method, JsonObject params, Class<T> responseType)
      throws IOException {
    JsonElement result = session.sendRequest(method, params);
    return gson.fromJson(result, responseType);
  }

  public void connect(Client callee) {
    this.webRtcEndpoint.connect(callee.webRtcEndpoint);

  }

  public WebRtcEndpoint getEndpoint() {
    return this.webRtcEndpoint;
  }

}
