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

import org.kurento.client.IceCandidate;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.google.common.base.Objects;

/**
 * Client session.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 1.0.0
 */
public class ClientSession {

  private static final Logger log = LoggerFactory.getLogger(ClientSession.class);

  private final SimpMessagingTemplate messagingTemplate;

  private WebRtcEndpoint webRtcEndpoint;
  private MediaPipeline mediaPipeline;

  private final String sessionId;

  public ClientSession(SimpMessagingTemplate messagingTemplate, String sessionId) {
    this.messagingTemplate = messagingTemplate;
    this.sessionId = sessionId;
  }

  public WebRtcEndpoint getWebRtcEndpoint() {
    return webRtcEndpoint;
  }

  public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {

    if (this.webRtcEndpoint != null) {
      this.webRtcEndpoint.release();
    }

    this.webRtcEndpoint = webRtcEndpoint;

    this.webRtcEndpoint.addOnIceCandidateListener(event -> {
      SimpMessageHeaderAccessor headerAccessor =
          SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
      headerAccessor.setSessionId(sessionId);
      headerAccessor.setLeaveMutable(true);

      log.debug("CLient {}: Sending candidate to remote peer", this);
      messagingTemplate.convertAndSendToUser(sessionId, "/queue/ice-candidates",
          event.getCandidate(), headerAccessor.getMessageHeaders());
      // messagingTemplate.convertAndSend(destination, event.getCandidate());
    });

    this.setLoopback();
  }

  public void setLoopback() {
    if (this.webRtcEndpoint != null) {
      this.webRtcEndpoint.connect(this.webRtcEndpoint);
    }
  }

  public void setPipeline(MediaPipeline mediaPipeline) {
    this.mediaPipeline = mediaPipeline;
  }

  public void addCandidate(IceCandidate candidate) {
    webRtcEndpoint.addIceCandidate(candidate);
  }

  public void release() {
    this.webRtcEndpoint.release();
    this.mediaPipeline.release();
  }

  public void processAnswer(String sdpAnswer) {
    this.webRtcEndpoint.processAnswer(sdpAnswer);
  }

  public void gatherCandidates() {
    this.webRtcEndpoint.gatherCandidates();
  }

  public String generateOffer() {
    return this.webRtcEndpoint.generateOffer();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.sessionId);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ClientSession)) {
      return false;
    }
    return Objects.equal(((ClientSession) obj).sessionId, this.sessionId);
  }

  @Override
  public String toString() {
    return this.sessionId;
  }

  public String getSessionId() {
    return this.sessionId;
  }

}
