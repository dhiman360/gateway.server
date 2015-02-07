/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.server.util.gcm;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public final class GCMHttpSender {

    private static final String GCM_URL = "https://android.googleapis.com/gcm/send";
    private static final String GCM_SEND_FAILURE = "\"failure\":1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String API_KEY = "AIzaSyCOjKapErQ993LXZ-m-xXZTq9AahjD_8Dg";
    private static final String DEMO_REG_ID = "APA91bHM9oXWg2zHz43UBbmYwv2UoYJFrLyJqQA86v"
            + "_QoEuo3UgCeflIElrtfd8W83ICQLX8gwovqACy7AKE540OOke-UsvFQY0NOydrSAKdCnHjIBZR"
            + "DpyHP4oLfrbrLFulw3VJJOHmlujRg8XTg_vtQ3yE2UMTp1glg4Fzg4bfs-TNEtS3GGg";

    private static GCMHttpSender sender = new GCMHttpSender();

    private GCMHttpSender() {
    }

    public static GCMHttpSender getInstance() {
        return sender;
    }

    /**
     * Use this API to send the message to GCM cloud using a POST Http request
     * @param registrationId This parameter specifies the device registration ID receiving the message. MUST
     * @param notificationKey This parameter specifies a string that maps a single user to multiple registration IDs
     * associated with that user. OPTIONAL
     * @param collapseKey This parameter specifies an arbitrary string (such as "Updates Available") that is used to
     * collapse a group of like messages when the device is offline, so that only the last message gets sent to the
     * client. This is intended to avoid sending too many messages to the phone when it comes back online.
     * @param data This parameter specifies a JSON object whose fields represents the key-value pairs of the message's
     * payload data. If present, the payload data will be included in the Intent as application data, with the key being
     * the extra's name, e.g. "data":{"jmsmessage":"This is your message"}. There is no limit on the number of key/value
     * pairs, though there is a limit on the total size of the message (4kb)
     * @param delayWhileIdle This parameter indicates that the message should not be sent immediately if the device is
     * idle.
     * @param timeToLive This parameter specifies how long (in seconds) the message should be kept on GCM storage if the
     * device is offline.
     * @param restrictedPackageName This parameter specifies a string containing the package name of gateway client
     * code. When set, messages are only sent to registration IDs that match the package name.
     * @param dryRun This parameter allows developers to test a request without actually sending a message. Optional.
     * The default value is false, and must be a JSON boolean.
     * @return
     */
    public boolean send(String registrationId,
                        String notificationKey,
                        String collapseKey,
                        String data,
                        boolean delayWhileIdle,
                        int timeToLive,
                        String restrictedPackageName,
                        boolean dryRun) throws Exception {

        if (null == registrationId || "".equals(registrationId)) {
            return false;
        }

        URL obj = new URL(GCM_URL);
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

        // add reuqest header

        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Authorization", "key=" + API_KEY);
        con.setRequestProperty("Content-Type", "application/json");

        System.out.println("\nTesting 123 - Send Http POST request");
        StringBuilder dataBuilder = new StringBuilder();
        dataBuilder.append("{ ");

        if (null != collapseKey && !"".equals(collapseKey)) {
            dataBuilder.append("\"collapse_key\": \"");
            dataBuilder.append(collapseKey);
            dataBuilder.append("\",");
        }

        if (null != notificationKey && !"".equals(notificationKey)) {
            dataBuilder.append("\"notification_key\": \"");
            dataBuilder.append(notificationKey);
            dataBuilder.append("\",");
        }

        if (null != data && !"".equals(data)) {
            dataBuilder.append("\"data\": ");
            dataBuilder.append(data);
            dataBuilder.append(",");
        }

        if (delayWhileIdle) {
            dataBuilder.append("\"delay_while_idle\": true,");
            dataBuilder.append("\",");
        }

        if (timeToLive > 0) {
            dataBuilder.append("\"time_to_live\": \"");
            dataBuilder.append(timeToLive);
            dataBuilder.append("\",");
        }

        if (null != restrictedPackageName && !"".equals(restrictedPackageName)) {
            dataBuilder.append("\"restricted_package_name\": \"");
            dataBuilder.append(restrictedPackageName);
            dataBuilder.append("\",");
        }

        if (dryRun) {
            dataBuilder.append("\"dry_run\": true,");
            dataBuilder.append("\",");
        }

        dataBuilder.append("\"registration_ids\": [\"");
        dataBuilder.append(registrationId);
        dataBuilder.append("\"]");
        dataBuilder.append(" } ");

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(dataBuilder.toString());
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("Post data : " + dataBuilder.toString());
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        String responseStr = response.toString();
        // print result
        System.out.println(responseStr);

        if (responseStr.contains(GCM_SEND_FAILURE)) {
            return false;
        }
        return true;
    }

    /**
     * Use this API to send the message to GCM cloud using a POST Http request, without any fancy delay, time to live
     * etc.
     * @param registrationId This parameter specifies the device registration ID receiving the message. MUST
     * @param data This parameter specifies a JSON object whose fields represents the key-value pairs of the message's
     * payload data. If present, the payload data will be included in the Intent as application data, with the key being
     * the extra's name, e.g. "data":{"jmsmessage":"This is your message"}. There is no limit on the number of key/value
     * pairs, though there is a limit on the total size of the message (4kb)
     */
    public boolean send(String registrationId, String data) throws Exception {
        return send(registrationId, null, null, data, false, 0, null, false);
    }

    public static void main(String[] args) throws Exception {

        StringBuilder data = new StringBuilder();
        data.append("{ ");
        data.append("\"quote\": \"You will have a wonderful day!\"");
        data.append(" } ");
        GCMHttpSender.getInstance().send(DEMO_REG_ID,
                        data.toString());
    }

}
