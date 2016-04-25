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

package com.elasticrtc.tutorial.loopback.jsonrpc;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Named;

import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonRpcMethod;
import org.kurento.jsonrpc.Session;
import org.kurento.jsonrpc.TypeDefaultJsonRpcHandler;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Protocol handler for loopback.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 1.0.0
 */
public class LoopbackSessionHandler extends TypeDefaultJsonRpcHandler {

  private static final Gson gson = new GsonBuilder().create();

  @Autowired
  private KurentoClient kurento;

  private final Map<String, ClientSession> clients = new ConcurrentHashMap<>();

  /**
   * Method invoked by a client connecting to the server. Each client will be represented by a
   * ClientSession instance
   *
   * @param session
   *          the client session
   */
  @JsonRpcMethod
  public synchronized void registerClient(@Named final Session session) {

    ClientSession user = new ClientSession(session);
    clients.put(session.getSessionId(), user);

  }

  @JsonRpcMethod
  public synchronized String startMediaSession(@Named final Session session) throws IOException {

    ClientSession client = clients.get(session.getSessionId());
    MediaPipeline pipeline = kurento.createMediaPipeline();
    WebRtcEndpoint webrtc = new WebRtcEndpoint.Builder(pipeline).build();

    client.setPipeline(kurento.createMediaPipeline());
    client.setWebRtcEndpoint(webrtc);
    return client.generateOffer();
  }

  /**
   * Process the answer received from the client, in response to an SDP offer.
   *
   * @param session
   * @param sdpAnswer
   * @throws IOException
   */
  @JsonRpcMethod
  public synchronized void processAnswer(@Named final Session session,
      @Named("sdpAnswer") String sdpAnswer) throws IOException {

    ClientSession client = clients.get(session.getSessionId());
    client.processAnswer(sdpAnswer);
    client.gatherCandidates();
  }

  /**
   * Method invoked by clients stopping the loopback session.
   *
   * @param session
   *          Client session
   */
  @JsonRpcMethod
  public synchronized void stopMediaSession(@Named Session session) {
    ClientSession client = clients.get(session.getSessionId());
    if (client != null) {
      client.release();
    }

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
    ClientSession client = clients.get(session.getSessionId());

    IceCandidate cand = gson.fromJson(candidate, IceCandidate.class);
    client.addCandidate(cand);
  }

  @Override
  public void afterConnectionClosed(Session session, String status) throws Exception {
    stopMediaSession(session);
    clients.remove(session.getSessionId());
  }

}
