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

package com.elasticrtc.tutorial.recording.ws;

import org.kurento.client.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Hello World (WebRTC in loopback with recording) main class.
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 1.0.0
 */
@SpringBootApplication
@EnableWebSocket
public class RecorderApp implements WebSocketConfigurer {

  @Bean
  public RecorderHandler handler() {
    return new RecorderHandler();
  }

  @Bean
  public KurentoClient kurentoClient() {
    return KurentoClient.create();
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler(), "/recording");
  }

  @Bean
  public UserRegistry registry() {
    return new UserRegistry();
  }

  public static void main(String[] args) throws Exception {
    new SpringApplication(RecorderApp.class).run(args);
  }
}
