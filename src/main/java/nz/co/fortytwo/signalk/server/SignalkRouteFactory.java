/*
 *
 * Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
 * Web: www.42.co.nz
 * Email: robert@42.co.nz
 * Author: R T Huitema
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package nz.co.fortytwo.signalk.server;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nz.co.fortytwo.signalk.processor.AISProcessor;
import nz.co.fortytwo.signalk.processor.AlarmProcessor;
import nz.co.fortytwo.signalk.processor.AnchorWatchProcessor;
import nz.co.fortytwo.signalk.processor.DeclinationProcessor;
import nz.co.fortytwo.signalk.processor.DeltaImportProcessor;
import nz.co.fortytwo.signalk.processor.FullExportProcessor;
import nz.co.fortytwo.signalk.processor.FullImportProcessor;
import nz.co.fortytwo.signalk.processor.FullToDeltaProcessor;
import nz.co.fortytwo.signalk.processor.HeartbeatProcessor;
import nz.co.fortytwo.signalk.processor.InputFilterProcessor;
import nz.co.fortytwo.signalk.processor.JsonGetProcessor;
import nz.co.fortytwo.signalk.processor.JsonListProcessor;
import nz.co.fortytwo.signalk.processor.JsonSubscribeProcessor;
import nz.co.fortytwo.signalk.processor.MapToJsonProcessor;
import nz.co.fortytwo.signalk.processor.MqttProcessor;
import nz.co.fortytwo.signalk.processor.N2KProcessor;
import nz.co.fortytwo.signalk.processor.NMEAProcessor;
import nz.co.fortytwo.signalk.processor.OutputFilterProcessor;
import nz.co.fortytwo.signalk.processor.RestApiProcessor;
import nz.co.fortytwo.signalk.processor.RestAuthProcessor;
import nz.co.fortytwo.signalk.processor.SignalkModelProcessor;
import nz.co.fortytwo.signalk.processor.StompProcessor;
import nz.co.fortytwo.signalk.processor.StorageProcessor;
import nz.co.fortytwo.signalk.processor.ValidationProcessor;
import nz.co.fortytwo.signalk.processor.WindProcessor;
import nz.co.fortytwo.signalk.processor.WsSessionProcessor;
import nz.co.fortytwo.signalk.util.Constants;
import nz.co.fortytwo.signalk.util.JsonConstants;

import org.apache.camel.ExchangePattern;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.websocket.WebsocketConstants;
import org.apache.camel.component.websocket.WebsocketEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.log4j.Logger;



public class SignalkRouteFactory {

	private static Logger logger = Logger.getLogger(SignalkRouteFactory.class);
	private static Set<String> nameSet = new HashSet<String>();
	/**
	 * Configures a route for all input traffic, which will parse the traffic and update the signalk model
	 * @param routeBuilder
	 * @param input
	 * @param inputFilterProcessor
	 * @param nmeaProcessor
	 * @param aisProcessor
	 * @param signalkModelProcessor
	 * @throws IOException 
	 * @throws Exception 
	 */
	public static void configureInputRoute(RouteBuilder routeBuilder,String input) throws IOException {
		routeBuilder.from(input).id(getName("INPUT"))
			.onException(Exception.class).handled(true).maximumRedeliveries(0)
			.to("log:nz.co.fortytwo.signalk.model.receive?level=ERROR&showException=true&showStackTrace=true")
			.end()
		// dump misc rubbish
		.process(new InputFilterProcessor()).id(getName(InputFilterProcessor.class.getSimpleName()))
		//swap payloads to storage
		.process(new StorageProcessor()).id(getName(InputFilterProcessor.class.getSimpleName()))
		//convert NMEA to signalk
		.process(new NMEAProcessor()).id(getName(NMEAProcessor.class.getSimpleName()))
		//convert AIS to signalk
		.process(new AISProcessor()).id(getName(AISProcessor.class.getSimpleName()))
		//convert n2k
		.process(new N2KProcessor()).id(getName(N2KProcessor.class.getSimpleName()))
		//handle list
		.process(new JsonListProcessor()).id(getName(JsonListProcessor.class.getSimpleName()))
		//handle get
		.process(new JsonGetProcessor()).id(getName(JsonGetProcessor.class.getSimpleName()))
		//handle subscribe messages
		.process(new JsonSubscribeProcessor()).id(getName(JsonSubscribeProcessor.class.getSimpleName()))
		//deal with delta format
		.process(new DeltaImportProcessor()).id(getName(DeltaImportProcessor.class.getSimpleName()))
		//deal with full format
		.process(new FullImportProcessor()).id(getName(FullImportProcessor.class.getSimpleName()))
		//make sure we have timestamp/source
		.process(new ValidationProcessor()).id(getName(ValidationProcessor.class.getSimpleName()))
		//and update signalk model
		.process(new SignalkModelProcessor()).id(getName(SignalkModelProcessor.class.getSimpleName()));
		
	}
	
	/**
	 * Configures the route for output to websockets
	 * @param routeBuilder
	 * @param input
	 */
	public static void configureWebsocketTxRoute(RouteBuilder routeBuilder ,String input, int port){
		Predicate p1 = routeBuilder.header(Constants.OUTPUT_TYPE).isEqualTo(Constants.OUTPUT_WS);
		Predicate p2 = routeBuilder.header(WebsocketConstants.CONNECTION_KEY).isEqualTo(WebsocketConstants.SEND_TO_ALL);
		//from SEDA_WEBSOCKETS
			routeBuilder.from(input).id(getName("Websocket Tx"))
				.onException(Exception.class)
				.handled(true)
				.maximumRedeliveries(0)
				.to("log:nz.co.fortytwo.signalk.model.websocket.tx?level=ERROR&showException=true&showStackTrace=true")
				.end()
			.filter(PredicateBuilder.or(p1, p2))
			.to("skWebsocket://0.0.0.0:"+port+JsonConstants.SIGNALK_WS).id(getName("Websocket Client"));
		
	}
	/**
	 * Configures the route for input to websockets
	 * @param routeBuilder
	 * @param input
	 * @throws Exception 
	 */
	public static void configureWebsocketRxRoute(RouteBuilder routeBuilder ,String input, int port)  {
		
		WebsocketEndpoint wsEndpoint = (WebsocketEndpoint) routeBuilder.getContext().getEndpoint("skWebsocket://0.0.0.0:"+port+JsonConstants.SIGNALK_WS);
		wsEndpoint.setEnableJmx(true);
		wsEndpoint.setSessionSupport(true);
		
		routeBuilder.from(wsEndpoint).id(getName("Websocket Rx"))
			.onException(Exception.class)
			.handled(true).maximumRedeliveries(0)
			.to("log:nz.co.fortytwo.signalk.model.websocket.rx?level=ERROR&showException=true&showStackTrace=true")
			.end()
		.process(new WsSessionProcessor()).id(getName(WsSessionProcessor.class.getSimpleName()))
		//.to("log:nz.co.fortytwo.signalk.model.websocket.rx?level=INFO&showException=true&showStackTrace=true")
		.to(input).id(getName("SEDA_INPUT"));
		
	}
	public static void configureTcpServerRoute(RouteBuilder routeBuilder ,String input, NettyServer nettyServer, String outputType) throws Exception{
		// push out via TCPServer.
		Predicate p1 = routeBuilder.header(Constants.OUTPUT_TYPE).isEqualTo(outputType);
		Predicate p2 = routeBuilder.header(WebsocketConstants.CONNECTION_KEY).isEqualTo(WebsocketConstants.SEND_TO_ALL);
		routeBuilder.from(input).id(getName("Netty "+outputType+" Server"))
			.onException(Exception.class)
			.handled(true)
			.maximumRedeliveries(0)
			.end()
		.filter(PredicateBuilder.or(p1, p2))
		.process((Processor) nettyServer).id(getName(NettyServer.class.getSimpleName())).end();
			
	}
	
	public static void configureRestRoute(RouteBuilder routeBuilder ,String input){
		routeBuilder.from(input).id(getName("REST Api"))
			.setExchangePattern(ExchangePattern.InOut)
			.process(new RestApiProcessor()).id(getName(RestApiProcessor.class.getSimpleName()))
			.process(new OutputFilterProcessor()).id(getName(OutputFilterProcessor.class.getSimpleName()));
		}
	
	public static void configureAuthRoute(RouteBuilder routeBuilder ,String input){
		routeBuilder.from(input).id(getName("REST Authenticate"))
			.setExchangePattern(ExchangePattern.InOut)
			.process(new RestAuthProcessor()).id(getName(RestAuthProcessor.class.getSimpleName()));
		}
	
	public static void configureDeclinationTimer(RouteBuilder routeBuilder ,String input){
		routeBuilder.from(input).id(getName("Declination"))
			.process(new DeclinationProcessor()).id(getName(DeclinationProcessor.class.getSimpleName()))
			.to("log:nz.co.fortytwo.signalk.model.update?level=DEBUG").end();
	}
	
	public static void configureAlarmsTimer(RouteBuilder routeBuilder ,String input){
		routeBuilder.from(input).id(getName("Alarms"))
			.process(new AlarmProcessor()).id(getName(AlarmProcessor.class.getSimpleName()))
			.to("log:nz.co.fortytwo.signalk.model.update?level=DEBUG").end();
	}
	
	public static void configureAnchorWatchTimer(RouteBuilder routeBuilder ,String input){
		routeBuilder.from(input).id(getName("AnchorWatch"))
			.process(new AnchorWatchProcessor()).id(getName(AnchorWatchProcessor.class.getSimpleName()))
			.to("log:nz.co.fortytwo.signalk.model.update?level=DEBUG").end();
	}
	
	public static void configureWindTimer(RouteBuilder routeBuilder ,String input){
		routeBuilder.from("timer://wind?fixedRate=true&period=1000").id(getName("True Wind"))
			.process(new WindProcessor()).id(getName(WindProcessor.class.getSimpleName()))
			.to("log:nz.co.fortytwo.signalk.model.update?level=DEBUG")
			.end();
	}
	
	public static void configureCommonOut(RouteBuilder routeBuilder ) throws IOException{
		routeBuilder.from(RouteManager.SEDA_COMMON_OUT).id(getName("COMMON_OUT"))
			.onException(Exception.class).handled(true).maximumRedeliveries(0)
			.to("log:nz.co.fortytwo.signalk.model.output?level=ERROR")
			.end()
		.process(new MapToJsonProcessor()).id(getName(MapToJsonProcessor.class.getSimpleName()))
		.process(new FullToDeltaProcessor()).id(getName(FullToDeltaProcessor.class.getSimpleName()))
		.split().body()
		//swap payloads from storage
		.process(new StorageProcessor()).id(getName(InputFilterProcessor.class.getSimpleName()))
		.process(new OutputFilterProcessor()).id(getName(OutputFilterProcessor.class.getSimpleName()))
		.multicast().parallelProcessing()
			.to(RouteManager.DIRECT_TCP,
					RouteManager.SEDA_WEBSOCKETS, 
					RouteManager.DIRECT_MQTT, 
					RouteManager.DIRECT_STOMP,
					"log:nz.co.fortytwo.signalk.model.output?level=DEBUG"
					).id(getName("Multicast Outputs"))
		.end();
		routeBuilder.from(RouteManager.DIRECT_MQTT).id(getName("MQTT out"))
			.filter(routeBuilder.header(Constants.OUTPUT_TYPE).isEqualTo(Constants.OUTPUT_MQTT))
			.process(new MqttProcessor()).id(getName(MqttProcessor.class.getSimpleName()))
			.to(RouteManager.MQTT+"?publishTopicName=signalk.dlq").id(getName("MQTT Broker"));
		routeBuilder.from(RouteManager.DIRECT_STOMP).id(getName("STOMP out"))
			.filter(routeBuilder.header(Constants.OUTPUT_TYPE).isEqualTo(Constants.OUTPUT_STOMP))
			.process(new StompProcessor()).id(getName(StompProcessor.class.getSimpleName()))
			.to(RouteManager.STOMP).id(getName("STOMP Broker"));
	}
	
	public static void configureSubscribeTimer(RouteBuilder routeBuilder ,Subscription sub) throws Exception{
		String input = "timer://"+getRouteId(sub)+"?fixedRate=true&period="+sub.getPeriod();
		if(logger.isDebugEnabled())logger.debug("Configuring route "+input);
		String wsSession = sub.getWsSession();
		RouteDefinition route = routeBuilder.from(input);
			route.process(new FullExportProcessor(wsSession)).id(getName(FullExportProcessor.class.getSimpleName()))
				.onException(Exception.class).handled(true).maximumRedeliveries(0)
				.to("log:nz.co.fortytwo.signalk.model.output.subscribe?level=ERROR")
				.end()
			.setHeader(WebsocketConstants.CONNECTION_KEY, routeBuilder.constant(wsSession))
			.to(RouteManager.SEDA_COMMON_OUT).id(getName("SEDA_COMMON_OUT"))
			.end();
		route.setId(getRouteId(sub));
		sub.setRouteId(getRouteId(sub));
		((DefaultCamelContext)CamelContextFactory.getInstance()).addRouteDefinition(route);
		((DefaultCamelContext)CamelContextFactory.getInstance()).startRoute(route.getId());
		//routeBuilder.getContext().startAllRoutes();
	}

	private static String getRouteId(Subscription sub) {
		return "sub_"+sub.getWsSession();
	}


	public static void removeSubscribeTimers(RouteManager routeManager, List<Subscription> subs) throws Exception {
		for(Subscription sub : subs){
			SignalkRouteFactory.removeSubscribeTimer(routeManager, sub);
		}
		
	}
	
	public static void removeSubscribeTimer(RouteBuilder routeManager, Subscription sub) throws Exception {
			RouteDefinition routeDef = ((DefaultCamelContext)routeManager.getContext()).getRouteDefinition(getRouteId(sub));
			if(routeDef==null)return;
			if(logger.isDebugEnabled())logger.debug("Stopping sub "+getRouteId(sub)+","+routeDef);
			((DefaultCamelContext)routeManager.getContext()).stopRoute(routeDef);
			if(logger.isDebugEnabled())logger.debug("Removing sub "+getRouteId(sub));
			((DefaultCamelContext)routeManager.getContext()).removeRouteDefinition(routeDef);
			
			if(logger.isDebugEnabled())logger.debug("Done removing sub "+getRouteId(sub));
	}

	public static void configureHeartbeatRoute(RouteBuilder routeBuilder, String input) {
		
		routeBuilder.from(input).id(getName("Heartbeat"))
			.onException(Exception.class).handled(true).maximumRedeliveries(0)
			.to("log:nz.co.fortytwo.signalk.model.output.all?level=ERROR")
			.end()
		.process(new HeartbeatProcessor()).id(getName(HeartbeatProcessor.class.getSimpleName()));
		
	}

	public static String getName(String name) {
		int c = 0;
		String tmpName = name;
		while(nameSet.contains(tmpName)){
			tmpName=name+"-"+c;
			c++;
		}
		nameSet.add(tmpName);
		return tmpName;
	}
		
	
}
