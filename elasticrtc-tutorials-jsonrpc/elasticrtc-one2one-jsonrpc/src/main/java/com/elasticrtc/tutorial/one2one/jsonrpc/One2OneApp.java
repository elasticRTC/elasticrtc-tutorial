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

import org.kurento.client.KurentoClient;
import org.kurento.jsonrpc.internal.server.config.JsonRpcConfiguration;
import org.kurento.jsonrpc.server.JsonRpcConfigurer;
import org.kurento.jsonrpc.server.JsonRpcHandlerRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * One2One application bean definition and config
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 1.0.0
 */
@Configuration
@Import(JsonRpcConfiguration.class)
@EnableAutoConfiguration
public class One2OneApp implements JsonRpcConfigurer {
  @Bean
  public CallHandler callHandler() {
    return new CallHandler();
  }

  @Bean
  public ClientRegistry registry() {
    return new ClientRegistry();
  }

  @Bean
  public KurentoClient kurentoClient() {
    return KurentoClient.create();
  }

  @Override
  public void registerJsonRpcHandlers(JsonRpcHandlerRegistry registry) {
    registry.addHandler(callHandler().withSockJS(), "/one2one");
  }

  public static void main(String[] args) throws Exception {
    new SpringApplication(One2OneApp.class).run(args);
  }
}
