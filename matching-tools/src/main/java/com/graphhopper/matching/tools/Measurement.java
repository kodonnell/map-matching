/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.matching.tools;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.matching.LocationIndexMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;

/**
 * @author Peter Karisch
 * @author kodonnell
 */
public class Measurement {
    private static final Logger logger = LoggerFactory.getLogger(Measurement.class);
    private final Map<String, String> properties = new TreeMap<String, String>();
    private long seed;
    private int count;
    private BBox bbox;
    private DistanceCalcEarth distCalc = new DistanceCalcEarth();

    public static void main(String[] strs) throws Exception {
        new Measurement().start(CmdArgs.read(strs));
    }

    // creates measurement result file in the format <measurement property>=<value>
    void start(CmdArgs args) throws Exception {
    	
    	// read and initialise arguments:
        String graphLocation = args.get("graph.location", "");
        String propLocation = args.get("measurement.location", "");
        if (Helper.isEmpty(propLocation)) {
        	throw new Exception("You must provide an output location via the 'measurement.location' argument");
        }
        seed = args.getLong("measurement.seed", 123);
        count = args.getInt("measurement.count", 5000);

        // create hopper instance
        GraphHopper hopper = new GraphHopperOSM();
        hopper.init(args).forDesktop();
        hopper.getCHFactoryDecorator().setEnabled(true);
        hopper.getCHFactoryDecorator().setDisablingAllowed(true);
        hopper.importOrLoad();
        String vehicleStr = args.get("graph.flag_encoders", "car");
        FlagEncoder encoder = hopper.getEncodingManager().getEncoder(vehicleStr);
        GraphHopperStorage graph = hopper.getGraphHopperStorage();
        bbox = graph.getBounds();
        LocationIndexMatch locationIndex = new LocationIndexMatch(graph, (LocationIndexTree) hopper.getLocationIndex());
        MapMatching mapMatching = new MapMatching(graph, locationIndex, encoder);
        
        // start tests:
        StopWatch sw = new StopWatch().start();
        try {
            printLocationIndexMatchQuery(locationIndex);
            printTimeOfMapMatchQuery(hopper, mapMatching);
            System.gc();
            logger.info("store into " + propLocation);
        } catch (Exception ex) {
            logger.error("Problem while measuring " + graphLocation, ex);
            put("error", ex.toString());
        } finally {
            put("measurement.count", count);
            put("measurement.seed", seed);
            put("measurement.time", sw.stop().getTime());
            System.gc();
            put("measurement.totalMB", Helper.getTotalMB());
            put("measurement.usedMB", Helper.getUsedMB());
            try {
                store(new FileWriter(propLocation));
            } catch (IOException ex) {
                logger.error("Problem while storing properties " + graphLocation + ", " + propLocation, ex);
            }
        }
    }
    
    /**
     * Test the performance of finding candidate points for the index (which is run for every GPX
     * entry).
     * 
     */
    private void printLocationIndexMatchQuery(final LocationIndexMatch idx) {
        final double latDelta = bbox.maxLat - bbox.minLat;
        final double lonDelta = bbox.maxLon - bbox.minLon;
        final Random rand = new Random(seed);
        MiniPerfTest miniPerf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                double lat = rand.nextDouble() * latDelta + bbox.minLat;
                double lon = rand.nextDouble() * lonDelta + bbox.minLon;
                int val = idx.findNClosest(lat, lon, EdgeFilter.ALL_EDGES, rand.nextDouble() * 500).size();
                return val;
            }
        }.setIterations(count).start();
        print("location_index_match", miniPerf);
    }

    /**
     * Test the time taken for map matching on random routes. Note that this includes the index 
     * lookups (previous tests), so will be affected by those. Otherwise this is largely testing
     * the routing and HMM performance.
     */
    private void printTimeOfMapMatchQuery(final GraphHopper hopper, final MapMatching mapMatching) {
    	
    	// pick random start/end points to create a route, then pick random points from the route,
    	// and then run the random points through map-matching.
        final double latDelta = bbox.maxLat - bbox.minLat;
        final double lonDelta = bbox.maxLon - bbox.minLon;
        final Random rand = new Random(seed);
        mapMatching.setMaxVisitedNodes((int) 1e10); // need to set this high to handle long gaps
        MiniPerfTest miniPerf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
            	boolean foundPath = false;
            	
            	// keep going until we find a path (which we may not for certain start/end points)
            	while (!foundPath) {
            		
            		// create random points and find route between:
		            double lat0 = bbox.minLat + rand.nextDouble() * latDelta;
		            double lon0 = bbox.minLon + rand.nextDouble() * lonDelta;
		            double lat1 = bbox.minLat + rand.nextDouble() * latDelta;
		            double lon1 = bbox.minLon + rand.nextDouble() * lonDelta;
		            GHResponse r = hopper.route(new GHRequest(lat0, lon0, lat1, lon1));
		            
		            // if found, use it for map mathching:
		            if (!r.hasErrors()) {
		            	foundPath = true;
		            	long time = 0;
			            double sampleProportion = rand.nextDouble();
		                GHPoint prev = null;
		                List<GPXEntry> mock = new ArrayList<GPXEntry>();
		                PointList points = r.getBest().getPoints();
		                // loop through points and add (approximately) sampleProportion of them:
		            	for (GHPoint p : points) {
		            		if (null != prev && rand.nextDouble() < sampleProportion) {
		            			// estimate a reasonable time taken since the last point, so we
		            			// can give the GPXEntry a time. Use the distance between the
		            			// points and a random speed to estimate a time.
		            			double dx = distCalc.calcDist(prev.lat, prev.lon, p.lat, p.lon);
		            			double speedKPH = rand.nextDouble() * 100;
		            			double dt = (dx / 1000) / speedKPH * 3600000;
		            			time += (long) dt;
		            			// randomise the point lat/lon (i.e. so it's not exactly on the route):
		            			GHPoint randomised = distCalc.projectCoordinate(p.lat, p.lat, 20 * rand.nextDouble(), 360 * rand.nextDouble());
		            			mock.add(new GPXEntry(randomised, time));
		            		}
		            	}		            	
		            	// now match, provided there are enough points
		            	if (mock.size() > 2) {
		            		mapMatching.doWork(mock);		            		
		            	} else {
		            		foundPath = false; // retry
		            	}
		            	
		            	// TODO: do we need to return something non-trivial?
		            	return 0;
		            }		            
            	}
				return 0;
            }
        }.setIterations(count).start();
        print("map_match", miniPerf);
    	
    }
 

    void print(String prefix, MiniPerfTest perf) {
        logger.info(prefix + ": " + perf.getReport());
        put(prefix + ".sum", perf.getSum());
        put(prefix + ".min", perf.getMin());
        put(prefix + ".mean", perf.getMean());
        put(prefix + ".max", perf.getMax());
    }

    void put(String key, Object val) {
        // convert object to string to make serialization possible
        properties.put(key, "" + val);
    }

    private void store(FileWriter fileWriter) throws IOException {
        for (Entry<String, String> e : properties.entrySet()) {
            fileWriter.append(e.getKey());
            fileWriter.append("=");
            fileWriter.append(e.getValue());
            fileWriter.append("\n");
        }
        fileWriter.flush();
    }
}
