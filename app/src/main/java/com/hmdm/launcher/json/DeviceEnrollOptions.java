/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmdm.launcher.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties( ignoreUnknown = true )
public class DeviceEnrollOptions {
    private String customer;
    private String configuration;
    private List<String> groups;

    public DeviceEnrollOptions() {}

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public void setGroups(String[] groups) {
        if (groups == null) {
            this.groups = null;
            return;
        }
        this.groups = new LinkedList<>();
        for (String group : groups) {
            this.groups.add(group);
        }
    }

    public void setGroups(Set<String> groups) {
        if (groups == null) {
            this.groups = null;
            return;
        }
        this.groups = new LinkedList<>();
        for (String group : groups) {
            this.groups.add(group);
        }
    }

    public void setGroups(String groups) {
        if (groups == null) {
            this.groups = null;
            return;
        }
        String[] groupArray = groups.split(",");
        setGroups(groupArray);
    }

    public Set<String> getGroupSet() {
        if (groups == null) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (String group : groups) {
            result.add(group);
        }
        return result;
    }
}
