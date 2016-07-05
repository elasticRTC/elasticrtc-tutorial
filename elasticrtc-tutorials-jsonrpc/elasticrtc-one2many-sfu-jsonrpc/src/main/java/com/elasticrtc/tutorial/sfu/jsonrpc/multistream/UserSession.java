/*
 * (C) Copyright 2015 Kurento (http://kurento.org/)
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

import org.kurento.client.IceCandidate;
import org.kurento.jsonrpc.Session;
import org.kurento.module.sfu.WebRtcSfu;

import com.google.common.base.Objects;

/**
 * User session.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.2.1
 */
public class UserSession {

  private final Session session;
  private WebRtcSfu sfu;
  private String sfuSessionId;

  public UserSession(Session session) {
    this.session = session;
  }

  public Session getSession() {
    return session;
  }

  public WebRtcSfu getEndpoint() {
    return sfu;
  }

  public void setEndpoint(WebRtcSfu sfu) {
    this.sfu = sfu;
  }

  public void addCandidate(UserSession session, IceCandidate candidate) {
    sfu.addIceCandidate(session.getSfuSessionId(), candidate);
  }

  private String getSfuSessionId() {
    return this.sfuSessionId;
  }

  public void release(boolean isPresenter) {
    if (this.sfu != null) {
      if (isPresenter) {
        this.sfu.release();
      } else {
        this.sfu.releaseSession(sfuSessionId);
      }
    }
  }

  public void setSfuSessionId(String sfuSessionId) {
    this.sfuSessionId = sfuSessionId;
  }

  public boolean isNegotiationNeeded() {
    return this.sfu.isNegotiationNeeded(sfuSessionId);
  }

  public void processAnswer(String sdpAnswer) {
    this.sfu.processAnswer(sfuSessionId, sdpAnswer);
  }

  public String processOffer(String sdpOffer) {
    return this.sfu.processOffer(sfuSessionId, sdpOffer);
  }

  public String generateOffer() {
    return this.sfu.generateOffer(sfuSessionId);
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

}
