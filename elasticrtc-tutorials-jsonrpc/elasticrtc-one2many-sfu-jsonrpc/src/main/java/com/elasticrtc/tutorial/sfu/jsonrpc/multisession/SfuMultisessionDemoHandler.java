/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */

package com.elasticrtc.tutorial.sfu.jsonrpc.multisession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Named;

import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.jsonrpc.JsonRpcMethod;
import org.kurento.jsonrpc.Session;
import org.kurento.jsonrpc.TypeDefaultJsonRpcHandler;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Protocol handler for 1 to N video call communication.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.2.1
 */
public class SfuMultisessionDemoHandler extends TypeDefaultJsonRpcHandler {

  private static final Gson gson = new GsonBuilder().create();

  private final Map<String, SfuSession> sfuSessions = new ConcurrentHashMap<>();

  @Autowired
  private KurentoClient kurentoClient;

  public class NegotiationResponse {
    public String sdp;
  }

  /**
   * Method invoked by clients sending ice candidates to the server.
   *
   * @param session
   *          CLient session
   * @param candidate
   *          The ICE candidate
   */
  @JsonRpcMethod
  public synchronized void iceCandidate(@Named Session session,
      @Named("candidate") String candidate, @Named("userId") String userId) {
    SfuSession sfuSession = sfuSessions.get(session.getSessionId());

    if (sfuSession != null) {
      IceCandidate cand = gson.fromJson(candidate, IceCandidate.class);
      sfuSession.addCandidate(userId, cand);
    }

  }

  /**
   * Method invoked by a client connecting to the server.
   *
   * @param session
   *          the client session
   * @return The type of client that has been registered as.
   */
  @JsonRpcMethod
  public synchronized void register(@Named final Session session,
      @Named("simulcast") boolean simulcast) {
    SfuSession sfuSession = new SfuSession(session, kurentoClient, simulcast);

    sfuSessions.put(session.getSessionId(), sfuSession);
  }

  @JsonRpcMethod
  public synchronized void switchQuality(@Named final Session session,
      @Named("userId") String userId) {
    SfuSession sfuSession = sfuSessions.get(session.getSessionId());
    sfuSession.switchQuality(userId);
  }

  @JsonRpcMethod
  public synchronized NegotiationResponse negotiateWebRtc(@Named final Session session,
      @Named("userId") String userId, @Named("sdpOffer") String sdpOffer) throws IOException {

    NegotiationResponse response = new NegotiationResponse();
    SfuSession sfuSession = sfuSessions.get(session.getSessionId());

    sfuSession.createSession(userId);
    if (Strings.isNullOrEmpty(sdpOffer)) {
      response.sdp = sfuSession.generateOffer(userId);
    } else {
      response.sdp = sfuSession.processOffer(userId, sdpOffer);
    }
    return response;
  }

  /**
   * Process the answer received from the client, in response to a SDP offer.
   *
   * @param session
   * @param sdpAnswer
   * @throws IOException
   */
  @JsonRpcMethod
  public synchronized void processAnswer(@Named final Session session,
      @Named("sdpAnswer") String sdpAnswer, @Named("userId") String userId) throws IOException {

    SfuSession sfuSession = sfuSessions.get(session.getSessionId());
    sfuSession.processAnswer(sdpAnswer, userId);
  }

  @JsonRpcMethod
  public synchronized void stop(@Named Session session) throws IOException {
    SfuSession user = sfuSessions.remove(session.getSessionId());

    if (user != null) {
      user.release();
    }

  }

  @JsonRpcMethod
  public synchronized void stopUserSession(@Named Session session, @Named("userId") String userId)
      throws IOException {
    SfuSession sfuSession = sfuSessions.get(session.getSessionId());

    if (sfuSession != null) {
      sfuSession.releaseSession(userId);
    }
  }

  @Override
  public void afterConnectionClosed(Session session, String status) throws Exception {
    stop(session);
  }

}
