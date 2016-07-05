/*
 * (C) Copyright 2016 Kurento (http://kurento.org/)
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

package com.elasticrtc.tutorial.sfu.jsonrpc.monoliticsfu;

import java.util.ArrayList;
import java.util.List;

import org.kurento.jsonrpc.Session;
import org.kurento.module.sfu.MediaStream;
import org.kurento.module.sfu.MediaStreamTrack;
import org.kurento.module.sfu.RTCIceCandidate;
import org.kurento.module.sfu.RTCPeerConnection;
import org.kurento.module.sfu.RTCRtpReceiver;
import org.kurento.module.sfu.RTCSdpType;
import org.kurento.module.sfu.RTCSessionDescription;

import com.google.common.base.Objects;

/**
 * User session.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.2.1
 */
public class UserSession {

  private final Session session;
  private RTCPeerConnection pc;
  private final List<RTCPeerConnection> connectedPeers = new ArrayList<RTCPeerConnection>();

  public UserSession(Session session) {
    this.session = session;
  }

  public Session getSession() {
    return session;
  }

  public RTCPeerConnection getEndpoint() {
    return pc;
  }

  public void setEndpoint(RTCPeerConnection pc) {
    this.pc = pc;
  }

  public void addCandidate(RTCIceCandidate candidate) {
    pc.addIceCandidate(candidate);
  }

  public void release(boolean isPresenter) {
    this.pc.release();
  }

  public boolean isNegotiationNeeded() {
    return pc.getNegotiationNeeded();
  }

  public void processAnswer(String sdpAnswer) {
    pc.setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, sdpAnswer));
  }

  public String processOffer(String sdpOffer) {
    pc.setRemoteDescription(new RTCSessionDescription(RTCSdpType.OFFER, sdpOffer));
    RTCSessionDescription answer = pc.createAnswer();
    pc.setLocalDescription(answer);

    return answer.getSdp();
  }

  public String generateOffer() {
    RTCSessionDescription offer = pc.createOffer();
    pc.setLocalDescription(offer);

    return offer.getSdp();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.session.getSessionId());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof UserSession)) {
      return false;
    }
    return Objects.equal(((UserSession) obj).session.getSessionId(), this.session.getSessionId());
  }

  public void connect(UserSession user) {
    List<RTCRtpReceiver> receivers = pc.getReceivers();
    connectedPeers.add(user.pc);

    List<MediaStream> streams = pc.getRemoteMediaStreams();
    for (RTCRtpReceiver receiver : receivers) {
      MediaStreamTrack track = receiver.getTrack();
      user.pc.addTrack(track, streams);
    }
  }

}
