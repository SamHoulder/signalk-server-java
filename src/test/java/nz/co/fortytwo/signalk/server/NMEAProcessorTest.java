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

import static nz.co.fortytwo.signalk.server.util.JsonConstants.SELF;
import static nz.co.fortytwo.signalk.server.util.JsonConstants.VESSELS;
import static nz.co.fortytwo.signalk.server.util.JsonConstants.env_depth_belowTransducer;
import static nz.co.fortytwo.signalk.server.util.JsonConstants.nav_position_latitude;
import static nz.co.fortytwo.signalk.server.util.JsonConstants.propulsion_engineTemperature;
import static nz.co.fortytwo.signalk.server.util.JsonConstants.propulsion_oilPressure;
import static nz.co.fortytwo.signalk.server.util.JsonConstants.propulsion_rpm;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

import mjson.Json;
import nz.co.fortytwo.signalk.model.SignalKModel;
import nz.co.fortytwo.signalk.model.impl.SignalKModelFactory;
import nz.co.fortytwo.signalk.server.NMEAProcessor;
import nz.co.fortytwo.signalk.server.util.Constants;
import nz.co.fortytwo.signalk.server.util.Util;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class NMEAProcessorTest {

	private static Logger logger = Logger.getLogger(NMEAProcessorTest.class);
	private SignalKModel signalkModel=SignalKModelFactory.getInstance();
	
	@Before
	public void setUp() throws Exception {
	
		File jsonFile = new File("./conf/self.json");
		System.out.println(jsonFile.getAbsolutePath());
		try{
			Json temp = Json.read(jsonFile.toURI().toURL());
			signalkModel.merge(temp);
		}catch(Exception ex){
			System.out.println(ex.getMessage());
		}
	}

	@After
	public void tearDown() throws Exception {
	}
	@Test
	public void shouldPassJson(){
		 String jStr = "{\"vessels\":{\"self\":{\"environment\":{\"wind\":{\"directionApparent\":0.0000000000,\"directionTrue\":0.0000000000,\"speedApparent\":0.0000000000,\"speedTrue\":20.0000000000}}}}}";
		 NMEAProcessor processor = new NMEAProcessor();
		
		 String json = (String) processor.handle(jStr);
		 logger.debug(json);
		 assertEquals(jStr, json);
		 
	}
	@Test
	
	public void shouldHandleGPRMC(){
		 String nmea1 = "$GPRMC,144629.20,A,5156.91111,N,00434.80385,E,0.295,,011113,,,A*78";
		// String nmea2 = "$GPRMC,144629.30,A,5156.91115,N,00434.80383,E,1.689,,011113,,,A*73";
		// String nmea3 = "$GPRMC,144629.50,A,5156.91127,N,00434.80383,E,1.226,,011113,,,A*75";
		 NMEAProcessor processor = new NMEAProcessor();
		
		 Json json = (Json) processor.handle(nmea1);
		 logger.debug(json);
		 assertEquals(51.9485185d,signalkModel.findValue(json.at(VESSELS).at(SELF), nav_position_latitude).asDouble(),0.00001);
		 logger.debug("Lat :"+signalkModel.findValue(json, nav_position_latitude));
	}
	@Test
	@Ignore
	public void shouldHandleCruzproXDR() throws FileNotFoundException, IOException {
		NMEAProcessor processor = new NMEAProcessor();
		Json json = (Json) processor.handle("$YXXDR,G,0004,,G,12.27,,G,,,G,003.3,,G,0012,,MaxVu110*4E");
		//RPM,EVV,DBT,EPP,ETT
		assertEquals(4.0,signalkModel.findValue(json, propulsion_rpm).asDouble(),0.0001);
		//assertEquals(12.27,signalkModel.findValue(json, propulsion_).asDouble());
		assertEquals(null,signalkModel.findValue(json, env_depth_belowTransducer));
		assertEquals(3.3,signalkModel.findValue(json, propulsion_oilPressure).asDouble(),0.0001);
		assertEquals(12.0,signalkModel.findValue(json, propulsion_engineTemperature).asDouble(),0.0001);
	}
	
	@Test
	@Ignore
	public void shouldHandleSkipValue() throws FileNotFoundException, IOException {
		NMEAProcessor processor = new NMEAProcessor();
	
		//freeboard.nmea.YXXDR.MaxVu110=RPM,EVV,DBT,EPP,ETT
		Util.getConfig(null).setProperty("freeboard.nmea.YXXDR.MaxVu110", "RPM,EVV,SKIP,EPP,ETT");
		
		Json json = (Json) processor.handle("$YXXDR,G,0004,,G,12.27,,G,,,G,003.3,,G,0012,,MaxVu110*4E");
		//RPM,EVV,DBT,EPP,ETT
		assertEquals(4.0,signalkModel.findValue(json, propulsion_rpm).asDouble(),0.0001);
		//assertEquals(12.27,map.get(Constants.ENGINE_VOLTS));
		assertTrue(signalkModel.findValue(json, env_depth_belowTransducer)==null);
		assertEquals(3.3,signalkModel.findValue(json, propulsion_oilPressure).asDouble(),0.0001);
		assertEquals(12.0,signalkModel.findValue(json, propulsion_engineTemperature).asDouble(),0.0001);
	}
	@Test
	@Ignore
	public void shouldRejectMismatchedValues() throws FileNotFoundException, IOException {
		NMEAProcessor processor = new NMEAProcessor();
		//HashMap<String, Object> map = new HashMap<String, Object>();
		//freeboard.nmea.YXXDR.MaxVu110=RPM,EVV,DBT,EPP,ETT
		Util.getConfig(null).setProperty("freeboard.nmea.YXXDR.MaxVu110", "RPM,EVV,SKIP,EPP");
		//map.put(Constants.NMEA, "$YXXDR,G,0004,,G,12.27,,G,,,G,003.3,,G,0012,,MaxVu110*4E");
		Json json = (Json) processor.handle("$YXXDR,G,0004,,G,12.27,,G,,,G,003.3,,G,0012,,MaxVu110*4E");
		//RPM,EVV,DBT,EPP,ETT
		assertTrue(signalkModel.findValue(json, propulsion_rpm)==null);
		//assertTrue(signalkModel.findValue(json, propulsion_rpm)==null);
		assertTrue(signalkModel.findValue(json, env_depth_belowTransducer)==null);
		assertTrue(signalkModel.findValue(json, propulsion_oilPressure)==null);
		assertTrue(signalkModel.findValue(json, propulsion_engineTemperature)==null);
	}

}
