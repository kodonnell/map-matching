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
package com.graphhopper.matching;

import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.GPXEntry;

/**
 *
 * @author Peter Karich
 */
public class GPXExtension {
    final GPXEntry entry;
    final QueryResult queryResult;
    private boolean incoming;
    private boolean directed;

    public GPXExtension(GPXEntry entry, QueryResult queryResult) {
    	this.entry = entry;
        this.queryResult = queryResult;
        this.directed = false;
    }
    
    public GPXExtension(GPXEntry entry, QueryResult queryResult, boolean incoming) {
        this(entry, queryResult);
        this.incoming = incoming;
        this.directed = true;
    }

    public boolean isDirected() {
    	return directed;
    }
    
    public boolean isIncoming() {
    	return incoming;
    }
    
    @Override
    public String toString() {
        return "entry:" + entry + ", query distance:" + queryResult.getQueryDistance();
    }

    public QueryResult getQueryResult() {
        return this.queryResult;
    }

    public GPXEntry getEntry() {
        return entry;
    }
}
