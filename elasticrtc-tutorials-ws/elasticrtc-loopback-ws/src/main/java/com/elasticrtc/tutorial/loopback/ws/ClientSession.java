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

package com.elasticrtc.tutorial.loopback.ws;

import org.kurento.client.IceCandidate;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;

/**
 * User session.
 *
 * @author David Fernandez (d.fernandezlop@gmail.com)
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 1.0.0
 */
public class ClientSession {
  private WebRtcEndpoint webRtcEndpoint;
  private MediaPipeline mediaPipeline;

  public ClientSession() {
  }

  public WebRtcEndpoint getWebRtcEndpoint() {
    return webRtcEndpoint;
  }

  public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
    this.webRtcEndpoint = webRtcEndpoint;
  }

  public MediaPipeline getMediaPipeline() {
    return mediaPipeline;
  }

  public void setMediaPipeline(MediaPipeline mediaPipeline) {
    this.mediaPipeline = mediaPipeline;
  }

  public void addCandidate(IceCandidate candidate) {
    webRtcEndpoint.addIceCandidate(candidate);
  }

  public void release() {
    this.mediaPipeline.release();
  }
}
