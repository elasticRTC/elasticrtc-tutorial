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

package com.elasticrtc.tutorial.sfu.jsonrpc.multisession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.RembParams;
import org.kurento.jsonrpc.Session;
import org.kurento.module.sfu.OnSessionIceCandidateEvent;
import org.kurento.module.sfu.WebRtcSfu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

/**
 * User session.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.2.1
 */
public class SfuSession {

  private static final Logger log = LoggerFactory.getLogger(SfuSession.class);

  public static final int HIGH_QUALITY_BITRATE = 2000000; // bps
  public static final int LOW_QUALITY_BITRATE = 240000; // bps

  private final MediaPipeline pipeline;

  private final Map<String, CandidateManager> candidateManagers = new ConcurrentHashMap<>();
  private final Map<String, String> userId2SfuSession = new ConcurrentHashMap<>();
  private final Map<String, Boolean> userIdHighQuality = new ConcurrentHashMap<>();
  private final Session session;
  private final WebRtcSfu sfu;

  private RecorderEndpoint recorder;

  public SfuSession(Session session, KurentoClient client, boolean simulcast) {
    this.session = session;
    this.pipeline = client.createMediaPipeline();
    this.sfu = new WebRtcSfu.Builder(pipeline).build();

    RembParams rembParams = new RembParams();
    rembParams.setRembOnConnect(HIGH_QUALITY_BITRATE);
    sfu.setRembParams(rembParams);

    sfu.setSimulcast(simulcast);

    sfu.setMaxVideoRecvBandwidth(HIGH_QUALITY_BITRATE / 1000); // kbps
    sfu.setMaxVideoSendBandwidth(HIGH_QUALITY_BITRATE / 1000); // kbps

    // this should lead to less test failures ;)
    sfu.setMinVideoSendBandwidth(HIGH_QUALITY_BITRATE / 1000); // kbps

    sfu.addOnSessionIceCandidateListener(new EventListener<OnSessionIceCandidateEvent>() {
      @Override
      public void onEvent(OnSessionIceCandidateEvent event) {
        candidateManagers.get(event.getSessionId()).manageCandidate(event.getCandidate());
      }
    });
  }

  public void switchQuality(String userId) {
    if (userIdHighQuality.get(userId)) {
      sfu.setVideoTargetBitrate(userId2SfuSession.get(userId), LOW_QUALITY_BITRATE);
      userIdHighQuality.put(userId, false);
    } else {
      sfu.setVideoTargetBitrate(userId2SfuSession.get(userId), HIGH_QUALITY_BITRATE);
      userIdHighQuality.put(userId, true);
    }
    log.debug("{} is now receiving in {} quality", userId, userIdHighQuality.get(userId)
        ? "high"
        : "low");
  }

  public void addCandidate(String userId, IceCandidate candidate) {
    sfu.addIceCandidate(userId2SfuSession.get(userId), candidate);
  }

  public void release() {
    this.sfu.release();
    this.pipeline.release();

  }

  public void releaseSession(String userId) {
    this.sfu.releaseSession(userId2SfuSession.get(userId));
    this.candidateManagers.remove(userId2SfuSession.get(userId));
  }

  public void processAnswer(String sdpAnswer, String userId) {
    this.sfu.processAnswer(userId2SfuSession.get(userId), sdpAnswer);
  }

  public String generateOffer(String userId) {
    return this.sfu.generateOffer(userId2SfuSession.get(userId));
  }

  public String processOffer(String userId, String sdpOffer) {
    return this.sfu.processOffer(userId2SfuSession.get(userId), sdpOffer);
  }

  public void createSession(String userId) {
    String sessionId = this.sfu.createSession();
    userId2SfuSession.put(userId, sessionId);
    userIdHighQuality.put(userId, true);
    candidateManagers.put(sessionId, new CandidateManager(userId));

    if ("presenter".equals(userId)) {
      this.sfu.setMasterSession(sessionId);
    }
  }

  public void startRecording(String path, MediaProfileSpecType mediaProfile) {
    if (recorder == null) {
      recorder = new RecorderEndpoint.Builder(this.pipeline, path).withMediaProfile(mediaProfile)
          .build();
      this.sfu.connect(recorder);
      recorder.record();
    }
  }

  public void stopRecording() {
    if (recorder != null) {
      recorder.stop();
      try {
        recorder.release();
        recorder = null;
      } catch (Exception e) {
        log.warn("Error releasing recorder endpoint", e);
      }
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.session.getSessionId());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SfuSession)) {
      return false;
    }
    return Objects.equal(((SfuSession) obj).session.getSessionId(), this.session.getSessionId());
  }

  class UserIceCandidate {
    final IceCandidate candidate;
    final String userId;

    UserIceCandidate(IceCandidate candidate, String userId) {
      this.candidate = candidate;
      this.userId = userId;
    }
  }

  private class CandidateManager {

    private final String userId;

    CandidateManager(String userId) {
      this.userId = userId;
    }

    public void manageCandidate(IceCandidate candidate) {

      try {
        synchronized (session) {
          session.sendNotification("iceCandidate", new UserIceCandidate(candidate, userId));
        }
      } catch (IOException e) {
        log.debug(e.getMessage(), e);
      }
    }
  }

}
