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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Named;

import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.jsonrpc.JsonRpcMethod;
import org.kurento.jsonrpc.Session;
import org.kurento.jsonrpc.TypeDefaultJsonRpcHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Protocol handler for 1 to 1 video call communication.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 1.0.0
 */
public class CallHandler extends TypeDefaultJsonRpcHandler {

  public enum CallResponseEnum {
    ACCEPTED, REJECTED, FAILED;
  }

  public class CallResponse {
    public CallResponseEnum response;
    public String message;
  }

  private static final Gson gson = new GsonBuilder().create();
  private static final Logger log = LoggerFactory.getLogger(CallHandler.class);

  private final Map<String, MediaSession> mediaSessions = new ConcurrentHashMap<>();

  @Autowired
  private KurentoClient kurento;

  @Autowired
  private ClientRegistry registry;

  @JsonRpcMethod
  public synchronized String register(@Named Session session, @Named("name") String name)
      throws IOException {

    Client user = new Client(session, name);
    String responseMsg = "accepted";
    if (name.isEmpty()) {
      responseMsg = "rejected: empty user name";
    } else if (registry.contains(name)) {
      responseMsg = "rejected: user '" + name + "' already registered";
    } else {
      registry.register(user);
    }

    return responseMsg;
  }

  @JsonRpcMethod
  public synchronized CallResponse call(@Named Session session, @Named("to") String to) {
    Client caller = this.registry.getBySession(session);
    Client callee = registry.getByName(to);

    CallResponse response = new CallResponse();
    if (callee != null) {
      JsonObject message = new JsonObject();
      message.addProperty("caller", caller.getName());
      try {
        CallResponse calleeResponse =
            callee.sendRequest("incomingCall", message, CallResponse.class);

        response.message = calleeResponse.message;
        response.response = calleeResponse.response;

        switch (calleeResponse.response) {
          case ACCEPTED:
            MediaSession mediaSession = new MediaSession(kurento, caller, callee);
            mediaSessions.put(caller.getSessionId(), mediaSession);
            mediaSessions.put(callee.getSessionId(), mediaSession);
            break;
        }
      } catch (IOException e) {
        response.message =
            "Could not contact " + to + " due to an internal error. Please try again later!";
        response.response = CallResponseEnum.FAILED;
      }
    } else {
      response.message = "user '" + to + "' is not registered";
      response.response = CallResponseEnum.FAILED;
    }

    return response;
  }

  @JsonRpcMethod
  public synchronized String negotiateWebRtc(@Named Session session,
      @Named("sdpOffer") String sdpOffer) {
    Client client = this.registry.getBySession(session);

    String sdpAnswer = client.getEndpoint().processOffer(sdpOffer);
    client.getEndpoint().gatherCandidates();
    return sdpAnswer;
  }

  /**
   * Method invoked by clients sending ice candidates to the server.
   *
   * @param session
   *          Client session
   * @param candidate
   *          The ICE candidate
   */
  @JsonRpcMethod
  public synchronized void iceCandidate(@Named Session session,
      @Named("candidate") String candidate) {
    Client client = registry.getBySession(session);

    IceCandidate cand = gson.fromJson(candidate, IceCandidate.class);
    client.addCandidate(cand);
  }

  @JsonRpcMethod
  public synchronized void stop(@Named Session session) {
    String sessionId = session.getSessionId();
    MediaSession mediaSession = mediaSessions.remove(sessionId);
    if (mediaSession != null) {
      mediaSession.release();
    }
  }

  @Override
  public void afterConnectionClosed(Session session, String status) throws Exception {
    stop(session);
    registry.removeBySession(session);
  }

}
