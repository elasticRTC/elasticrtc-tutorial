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

package com.elasticrtc.tutorial.sfu.jsonrpc.multistream;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Named;

import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.jsonrpc.JsonRpcMethod;
import org.kurento.jsonrpc.Session;
import org.kurento.jsonrpc.TypeDefaultJsonRpcHandler;
import org.kurento.module.sfu.OnSessionIceCandidateEvent;
import org.kurento.module.sfu.WebRtcSfu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Protocol handler for 1 to N video call communication.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.2.1
 */
public class SfuMultistreamRenegotiationDemoHandler extends TypeDefaultJsonRpcHandler {

  private static final Logger log = LoggerFactory
      .getLogger(SfuMultistreamRenegotiationDemoHandler.class);
  private static final Gson gson = new GsonBuilder().create();

  private final ConcurrentHashMap<String, UserSession> clients = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CandidateManager> candidateManagers = new ConcurrentHashMap<>();

  @Autowired
  private KurentoClient kurento;

  private MediaPipeline pipeline;
  private UserSession presenter;

  public class RegisterResponse {
    public String response;
    public String type = "viewer";
    public String message;
  }

  public class NegotiationResponse {
    public String sdpOffer;
    public String sdpAnswer;
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
  public synchronized void iceCandidate(@Named Session session, @Named("candidate") String candidate) {
    UserSession user = clients.get(session.getSessionId());

    if ((user != null) && (presenter != null)) {
      IceCandidate cand = gson.fromJson(candidate, IceCandidate.class);
      presenter.addCandidate(user, cand);
    }

  }

  /**
   * Method invoked by a client connecting to the server. The first client to invoke this method
   * will be considered the presenter.
   *
   * @param session
   *          the client session
   * @return The type of client that has been registered as.
   */
  @JsonRpcMethod
  public synchronized RegisterResponse register(@Named final Session session,
      @Named("simulcast") boolean simulcast) {

    RegisterResponse response = new RegisterResponse();
    response.type = "viewer";
    UserSession user = new UserSession(session);
    clients.put(session.getSessionId(), user);

    if (pipeline == null) {
      pipeline = kurento.createMediaPipeline();
    }

    String sfuSessionId;

    if (presenter == null) {
      presenter = user;
      response.type = "presenter";
      WebRtcSfu sfu = new WebRtcSfu.Builder(pipeline).build();
      sfu.setSimulcast(simulcast);
      sfu.setMaxVideoRecvBandwidth(2000);
      sfu.setMinVideoRecvBandwidth(2000);
      sfu.addOnSessionIceCandidateListener(new EventListener<OnSessionIceCandidateEvent>() {
        @Override
        public void onEvent(OnSessionIceCandidateEvent event) {
          candidateManagers.get(event.getSessionId()).manageCandidate(event.getCandidate());
        }
      });
      user.setEndpoint(sfu);

      sfuSessionId = sfu.createSession();
      sfu.setMasterSession(sfuSessionId);

    } else {
      user.setEndpoint(presenter.getEndpoint());
      sfuSessionId = presenter.getEndpoint().createSession();
    }

    user.setSfuSessionId(sfuSessionId);
    candidateManagers.put(sfuSessionId, new CandidateManager(session));

    return response;
  }

  @JsonRpcMethod
  public synchronized NegotiationResponse negotiateWebRtc(@Named final Session session,
      @Named("sdpOffer") String sdpOffer) throws Exception {

    NegotiationResponse response = new NegotiationResponse();
    UserSession user = clients.get(session.getSessionId());

    if (sdpOffer == null) {
      response.sdpOffer = user.generateOffer();
      log.debug("Generated viewer SDP offer:\n{}", response.sdpOffer);
    } else {
      if (!presenter.equals(user)) {
        throw new Exception("Only the presenter can initiate media negotiation");
      }
      response.sdpAnswer = user.processOffer(sdpOffer);
      log.debug("Processed presenter SDP offer:\n{}\nOur SDP answer:\n{}", sdpOffer,
          response.sdpAnswer);

      for (UserSession sessUser : clients.values()) {
        if (presenter.equals(sessUser)) {
          continue;
        }

        if (sessUser.isNegotiationNeeded()) {
          String viewerSdpOffer = sessUser.generateOffer();

          log.debug("Negotiate viewer with SDP offer:\n{}", viewerSdpOffer);

          sessUser.getSession().sendNotification("viewerNegotiation", viewerSdpOffer);
        }
      }
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
      @Named("sdpAnswer") String sdpAnswer) {

    UserSession viewer = clients.get(session.getSessionId());
    viewer.processAnswer(sdpAnswer);

    log.debug("Processed viewer SDP answer:\n{}", sdpAnswer);
  }

  @JsonRpcMethod
  public synchronized void stop(@Named Session session) {
    UserSession user = clients.remove(session.getSessionId());

    if (user != null) {
      user.release(user.equals(presenter));
      if (user.equals(presenter)) {
        presenter = null;
      }
    }

    if (clients.isEmpty() && (pipeline != null)) {
      pipeline.release();
      pipeline = null;
    }

  }

  @Override
  public void afterConnectionClosed(Session session, String status) {
    stop(session);
  }

  private class CandidateManager {

    private final Session session;

    CandidateManager(final Session session) {
      this.session = session;
    }

    public void manageCandidate(IceCandidate candidate) {

      try {
        synchronized (session) {
          session.sendNotification("iceCandidate", candidate);
        }
        log.debug("Sent candidate {}", candidate);
      } catch (IOException e) {
        log.warn(e.getMessage(), e);
      }
    }
  }

}
