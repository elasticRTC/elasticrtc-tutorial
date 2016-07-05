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

package com.elasticrtc.tutorial.sfu.jsonrpc;

import org.kurento.client.KurentoClient;
import org.kurento.jsonrpc.internal.server.config.JsonRpcConfiguration;
import org.kurento.jsonrpc.server.JsonRpcConfigurer;
import org.kurento.jsonrpc.server.JsonRpcHandlerRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.elasticrtc.tutorial.sfu.jsonrpc.monoliticsfu.MonoliticSfuDemoHandler;
import com.elasticrtc.tutorial.sfu.jsonrpc.multibrowser.SfuMultibrowserDemoHandler;
import com.elasticrtc.tutorial.sfu.jsonrpc.multisession.SfuMultisessionDemoHandler;
import com.elasticrtc.tutorial.sfu.jsonrpc.multistream.SfuMultistreamRenegotiationDemoHandler;
import com.elasticrtc.tutorial.sfu.jsonrpc.peerconnection.PeerConnectionDemoHandler;

/**
 * SFU
 *
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.2.1
 */
@Configuration
@Import(JsonRpcConfiguration.class)
@EnableAutoConfiguration
public class SfuDemoApp implements JsonRpcConfigurer {

  @Bean
  public SfuMultisessionDemoHandler multisessionHandler() {
    return new SfuMultisessionDemoHandler();
  }

  @Bean
  public SfuMultibrowserDemoHandler multibrowserHandler() {
    return new SfuMultibrowserDemoHandler();
  }

  @Bean
  public SfuMultistreamRenegotiationDemoHandler multistreamRenegotiationHandler() {
    return new SfuMultistreamRenegotiationDemoHandler();
  }

  @Bean
  public PeerConnectionDemoHandler peerConnectionHandler() {
    return new PeerConnectionDemoHandler();
  }

  @Bean
  public MonoliticSfuDemoHandler monoliticSfuDemoHandler() {
    return new MonoliticSfuDemoHandler();
  }

  @Bean
  public KurentoClient kurentoClient() {
    return KurentoClient.create();
  }

  @Override
  public void registerJsonRpcHandlers(JsonRpcHandlerRegistry registry) {
    registry.addHandler(multisessionHandler().withSockJS(), "/sfu-multisession")
        .addHandler(multibrowserHandler().withSockJS(), "/sfu-multibrowser")
        .addHandler(multistreamRenegotiationHandler().withSockJS(), "/sfu-multistream")
        .addHandler(peerConnectionHandler().withSockJS(), "/peerconnection")
        .addHandler(monoliticSfuDemoHandler().withSockJS(), "/monoliticsfu");
  }

  @Bean
  public ServletServerContainerFactoryBean createWebSocketContainer() {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(1000000); // chars
    container.setMaxBinaryMessageBufferSize(1000000); // bytes
    return container;
  }

  public static void main(String[] args) throws Exception {
    new SpringApplication(SfuDemoApp.class).run(args);
  }
}
