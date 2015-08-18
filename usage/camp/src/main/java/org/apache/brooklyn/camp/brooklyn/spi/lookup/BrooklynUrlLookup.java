/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.camp.brooklyn.spi.lookup;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.management.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.util.net.Urls;

import brooklyn.entity.basic.ConfigKeys;

public class BrooklynUrlLookup {

    public static ConfigKey<String> BROOKLYN_ROOT_URL = ConfigKeys.newStringConfigKey("brooklyn.root.url");
    
    public static String getUrl(ManagementContext bmc, Entity entity) {
        String root = bmc.getConfig().getConfig(BROOKLYN_ROOT_URL);
        if (root==null) return null;
        return Urls.mergePaths(root, "#/", 
                "/v1/applications/"+entity.getApplicationId()+"/entities/"+entity.getId());
    }

}
