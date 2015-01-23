/*
 * 
 * Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
 * Web: www.42.co.nz
 * Email: robert@42.co.nz
 * Author: R T Huitema
 * 
 * This file is part of the signalk-server-java project
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Adapted from https://github.com/rhq-project/rhq-metrics
 */

package nz.co.fortytwo.signalk.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.Future;

import java.util.Properties;

import nz.co.fortytwo.signalk.server.util.Constants;
import nz.co.fortytwo.signalk.server.util.Util;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.websocket.WebsocketConstants;
import org.apache.log4j.Logger;

public class NettyServer implements Processor{

	private Properties config;
	private final EventLoopGroup group;
	private final EventLoopGroup workerGroup;

	private static Logger logger = Logger.getLogger(NettyServer.class);
	private static final StringDecoder DECODER = new StringDecoder();
	private static final StringEncoder ENCODER = new StringEncoder();
	private CamelNettyHandler forwardingHandler = null;
	private int port = 5555;
	/**
	 * @param configDir
	 * @throws Exception 
	 */
	public NettyServer(String configDir) throws Exception {
		config = Util.getConfig(configDir);
		port = Integer.valueOf(config.getProperty(Constants.TCP_PORT));
		group = new NioEventLoopGroup();
		workerGroup = new NioEventLoopGroup();
		
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				shutdownServer();
			}
		}));
	}
	
	public void run() throws Exception{
		forwardingHandler = new CamelNettyHandler(config);
		// The generic TCP socket server
		ServerBootstrap skBootstrap = new ServerBootstrap();
		skBootstrap.group(group, workerGroup).channel(NioServerSocketChannel.class).localAddress(port)
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel socketChannel) throws Exception {
						ChannelPipeline pipeline = socketChannel.pipeline();
						pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
						pipeline.addLast(DECODER);
						pipeline.addLast(ENCODER);
						pipeline.addLast(forwardingHandler);
						logger.info("Signal K Connection over TCP from:" + socketChannel.remoteAddress());
						
					}
					
				});
		final ChannelFuture signalkTcpFuture = skBootstrap.bind().sync();
		logger.info("Server listening on TCP " + signalkTcpFuture.channel().localAddress());
		signalkTcpFuture.channel().closeFuture();
		
		//TODO: udp server:https://github.com/netty/netty/tree/master/example/src/main/java/io/netty/example/qotm
		/*Bootstrap udpBootstrap = new Bootstrap();
		udpBootstrap.group(group).channel(NioDatagramChannel.class).localAddress(Integer.valueOf(config.getProperty(Constants.UDP_PORT)))
				.handler(new ChannelInitializer<Channel>() {
					@Override
					public void initChannel(Channel socketChannel) throws Exception {
						ChannelPipeline pipeline = socketChannel.pipeline();
						pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
						pipeline.addLast(DECODER);
						pipeline.addLast(ENCODER);
						pipeline.addLast(forwardingHandler);
					}
				});
		ChannelFuture signalkUdpFuture = udpBootstrap.bind().sync();
		logger.info("Server listening on udp " + signalkUdpFuture.channel().localAddress());
		signalkUdpFuture.channel().closeFuture().sync();*/
	}

	public void shutdownServer() {
		logger.info("Stopping ptrans...");
		Future<?> groupShutdownFuture = group.shutdownGracefully();
		Future<?> workerGroupShutdownFuture = workerGroup.shutdownGracefully();
		try {
			groupShutdownFuture.sync();
		} catch (InterruptedException ignored) {
		}
		try {
			workerGroupShutdownFuture.sync();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		logger.info("Stopped");
	}

	@Override
	public void process(Exchange exchange) throws Exception {
		logger.debug("Received msg for tcp: "+exchange.getIn().getBody());
		String msg = exchange.getIn().getBody().toString();
		if(msg!=null){
			//get the session
			String session = exchange.getIn().getHeader(WebsocketConstants.CONNECTION_KEY, String.class);
		
			if(WebsocketConstants.SEND_TO_ALL.equals(session)){
				for(String key: forwardingHandler.getContextList().keySet()){
					ChannelHandlerContext ctx = forwardingHandler.getChannel(key);
					if(ctx!=null&& ctx.channel().isWritable())ctx.pipeline().writeAndFlush(msg+"\r\n");
				}
			}else{
				ChannelHandlerContext ctx = forwardingHandler.getChannel(session);
				if(ctx!=null && ctx.channel().isWritable())ctx.pipeline().writeAndFlush(msg+"\r\n");
			}
		}
		
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

}