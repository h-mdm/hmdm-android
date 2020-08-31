package com.hmdm.launcher.server;

import java.net.MalformedURLException;
import java.net.URL;

public class ServerUrl {
    public String baseUrl;
    public String serverProject;

    public ServerUrl(String serverUrl) throws MalformedURLException {
        URL url;
         url = new URL(serverUrl);

        baseUrl = url.getProtocol() + "://" + url.getHost();
        if (url.getPort() != -1) {
            baseUrl += ":" + url.getPort();
        }
        serverProject = url.getPath();
        if (serverProject.endsWith("/")) {
            serverProject = serverProject.substring(0, serverProject.length() - 1);
        }
        if (serverProject.startsWith("/")) {
            serverProject = serverProject.substring(1);
        }
    }
}
