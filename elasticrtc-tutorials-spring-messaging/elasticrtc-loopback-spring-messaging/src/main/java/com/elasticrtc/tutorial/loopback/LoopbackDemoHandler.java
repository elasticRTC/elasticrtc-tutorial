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

package com.elasticrtc.tutorial.loopback;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Protocol handler for loopback.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 1.0.0
 */
@Controller
public class LoopbackDemoHandler {

  private static final Logger log = LoggerFactory.getLogger(LoopbackDemoHandler.class);

  @Autowired
  private KurentoClient kurento;

  @Autowired
  private SimpMessagingTemplate messagingTemplate;

  private final Map<String, ClientSession> clients = new ConcurrentHashMap<>();

  @MessageMapping("/start")
  @SendToUser("/topic/start")
  public String startMediaSession(SimpMessageHeaderAccessor headerAccessor) throws IOException {

    ClientSession client = clients.get(headerAccessor.getSessionId());
    log.debug("Client {}: START media session {}", client, headerAccessor.getSessionId());
    MediaPipeline pipeline = kurento.createMediaPipeline();
    WebRtcEndpoint webrtc = new WebRtcEndpoint.Builder(pipeline).build();

    client.setPipeline(pipeline);
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
  @MessageMapping("/processAnswer")
  public void processAnswer(ProcessAnswerMessage message, SimpMessageHeaderAccessor headerAccessor)
      throws IOException {

    ClientSession client = clients.get(headerAccessor.getSessionId());
    log.debug("Client {}: process sdpanswer {}", client, message.getSdpAnswer());
    client.processAnswer(message.getSdpAnswer());
    client.gatherCandidates();
  }

  /**
   * Method invoked by clients stopping the loopback session.
   *
   * @param session
   *          Client session
   */
  @MessageMapping("/stop")
  public synchronized void stopMediaSession(SimpMessageHeaderAccessor headerAccessor) {
    ClientSession client = clients.get(headerAccessor.getSessionId());
    log.debug("Client {}: STOP media session", client);
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
  @MessageMapping("/ice-candidate")
  public void iceCandidate(IceCandidateSerializable candidate,
      SimpMessageHeaderAccessor headerAccessor) {
    ClientSession client = clients.get(headerAccessor.getSessionId());

    log.debug("Client {}: Received iceCandidate", client);

    client.addCandidate(candidate);
  }

  @MessageExceptionHandler
  @SendToUser("/topic/errors")
  public String handleException(Throwable exception) {
    return exception.getMessage();
  }

  @EventListener
  public void handleSessionConnection(SessionConnectedEvent event) {
    String sessionId =
        (String) event.getMessage().getHeaders().get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
    ClientSession client = new ClientSession(messagingTemplate, sessionId);
    log.debug("Client {}: REGISTER session", client);
    clients.put(client.getSessionId(), client);
  }

  @EventListener
  public void handleSessionDisconnection(SessionDisconnectEvent event) {
    String sessionId =
        (String) event.getMessage().getHeaders().get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
    clients.remove(sessionId);

    log.debug("Client {}: Removed from system", sessionId);
  }

}
