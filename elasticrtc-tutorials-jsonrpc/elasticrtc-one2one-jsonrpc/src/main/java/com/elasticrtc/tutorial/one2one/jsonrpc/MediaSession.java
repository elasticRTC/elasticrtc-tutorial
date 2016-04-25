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

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Media Pipeline (WebRTC endpoints, i.e. Kurento Media Elements) and connections for the 1 to 1
 * video communication.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 1.0.0
 */
public class MediaSession {

  private static final Logger log = LoggerFactory.getLogger(MediaSession.class);
  private MediaPipeline pipeline;
  private Client caller;
  private Client callee;

  public MediaSession(KurentoClient kurento, Client caller, Client callee) {
    try {
      this.pipeline = kurento.createMediaPipeline();
      this.caller = caller;
      this.callee = callee;
      this.caller.setEndpoint(new WebRtcEndpoint.Builder(pipeline).build());
      this.callee.setEndpoint(new WebRtcEndpoint.Builder(pipeline).build());

      this.caller.connect(this.callee);
      this.callee.connect(this.caller);
    } catch (Throwable t) {
      if (this.pipeline != null) {
        pipeline.release();
      }
    }
  }

  public void release() {
    if (pipeline != null) {
      pipeline.release();
    }

    if (caller != null) {
      try {
        caller.sendNotification("stopMediaSession");
      } catch (IOException e) {
        log.warn("Could not inform user {} that the media session has been cancelled",
            callee.getName());
      }
      caller.clear();
    }

    if (caller != null) {
      try {
        callee.sendNotification("stopMediaSession");
      } catch (IOException e) {
        log.warn("Could not inform user {} that the media session has been cancelled",
            callee.getName());
      }
      callee.clear();
    }
  }

}
