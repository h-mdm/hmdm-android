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

package com.hmdm.launcher.server;


import com.hmdm.launcher.json.DetailedInfo;
import com.hmdm.launcher.json.DetailedInfoConfigResponse;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.json.PushResponse;
import com.hmdm.launcher.json.RemoteLogConfigResponse;
import com.hmdm.launcher.json.RemoteLogItem;
import com.hmdm.launcher.json.ServerConfigResponse;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface ServerService {

    @GET("{project}/rest/public/sync/configuration/{number}")
    Call<ServerConfigResponse> getServerConfig(@Path("project") String project, @Path("number") String number, @Header("X-Request-Signature") String signature);

    @POST("{project}/rest/public/sync/info")
    @Headers("Content-Type: application/json")
    Call<ResponseBody> sendDevice(@Path("project") String project, @Body DeviceInfo deviceInfo);

    @GET("{project}/rest/notifications/device/{number}")
    Call<PushResponse> queryPushNotifications(@Path("project") String project, @Path("number") String number);

    @GET( "{project}/rest/plugins/devicelog/log/rules/{number}" )
    Call<RemoteLogConfigResponse> getRemoteLogConfig(@Path("project") String project, @Path("number") String number);

    @POST("{project}/rest/plugins/devicelog/log/list/{number}")
    @Headers("Content-Type: application/json")
    Call<ResponseBody> sendLogs(@Path("project") String project, @Path("number") String number, @Body List<RemoteLogItem> logItems);

    @PUT("{project}/rest/plugins/deviceinfo/deviceinfo/public/{number}")
    @Headers("Content-Type: application/json")
    Call<ResponseBody> sendDetailedInfo(@Path("project") String project, @Path("number") String number, @Body List<DetailedInfo> infoItems);

    @GET( "{project}/rest/plugins/deviceinfo/deviceinfo-plugin-settings/device/{number}" )
    Call<DetailedInfoConfigResponse> getDetailedInfoConfig(@Path("project") String project, @Path("number") String number);

    @POST("{project}/rest/plugins/devicereset/public/{number}")
    @Headers("Content-Type: application/json")
    Call<ResponseBody> confirmDeviceReset(@Path("project") String project, @Path("number") String number, @Body DeviceInfo deviceInfo);

    @POST("{project}/rest/plugins/devicereset/public/reboot/{number}")
    @Headers("Content-Type: application/json")
    Call<ResponseBody> confirmReboot(@Path("project") String project, @Path("number") String number, @Body DeviceInfo deviceInfo);

    @POST("{project}/rest/plugins/devicereset/public/password/{number}")
    @Headers("Content-Type: application/json")
    Call<ResponseBody> confirmPasswordReset(@Path("project") String project, @Path("number") String number, @Body DeviceInfo deviceInfo);

}
