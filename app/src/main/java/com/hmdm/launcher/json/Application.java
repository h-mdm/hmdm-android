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

@JsonIgnoreProperties( ignoreUnknown = true )
public class Application {

    public static final String TYPE_APP = "app";
    public static final String TYPE_WEB = "web";
    public static final String TYPE_INTENT = "intent";

    private String type;
    private String name;
    private String pkg;
    private String version;
    private Integer code;
    private String url;
    private boolean useKiosk;
    private boolean showIcon;
    private boolean remove;
    private boolean runAfterInstall;
    private boolean runAtBoot;
    private boolean skipVersion;
    private String iconText;
    private String icon;
    private Integer screenOrder;
    private Integer keyCode;
    private boolean bottom;
    private boolean longTap;
    private String intent;

    public Application() {}

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPkg() {
        return pkg;
    }

    public void setPkg(String pkg) {
        this.pkg = pkg;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isUseKiosk() {
        return useKiosk;
    }

    public void setUseKiosk(boolean useKiosk) {
        this.useKiosk = useKiosk;
    }

    public boolean isShowIcon() {
        return showIcon;
    }

    public void setShowIcon(boolean showIcon) {
        this.showIcon = showIcon;
    }

    public boolean isRemove() {
        return remove;
    }

    public void setRemove(boolean remove) {
        this.remove = remove;
    }

    public boolean isRunAfterInstall() {
        return runAfterInstall;
    }

    public void setRunAfterInstall(boolean runAfterInstall) {
        this.runAfterInstall = runAfterInstall;
    }

    public boolean isRunAtBoot() {
        return runAtBoot;
    }

    public void setRunAtBoot(boolean runAtBoot) {
        this.runAtBoot = runAtBoot;
    }

    public boolean isSkipVersion() {
        return skipVersion;
    }

    public void setSkipVersion(boolean skipVersion) {
        this.skipVersion = skipVersion;
    }

    public String getIconText() {
        return iconText;
    }

    public void setIconText(String iconText) {
        this.iconText = iconText;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getScreenOrder() {
        return screenOrder;
    }

    public void setScreenOrder(Integer screenOrder) {
        this.screenOrder = screenOrder;
    }

    public Integer getKeyCode() {
        return keyCode;
    }

    public void setKeyCode(Integer keyCode) {
        this.keyCode = keyCode;
    }

    public boolean isBottom() {
        return bottom;
    }

    public void setBottom(boolean bottom) {
        this.bottom = bottom;
    }

    public boolean isLongTap() {
        return longTap;
    }

    public void setLongTap(boolean longTap) {
        this.longTap = longTap;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }
}
