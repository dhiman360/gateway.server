/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.server.context.resolve;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Map;
import junit.framework.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kaazing.gateway.server.config.parse.GatewayConfigParser;
import org.kaazing.gateway.server.config.sep2014.GatewayConfigDocument;
import org.kaazing.gateway.server.config.sep2014.ServiceConnectOptionsType;
import org.kaazing.gateway.service.ConnectOptionsContext;
import org.kaazing.gateway.service.TransportOptionNames;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for resolving gateway-config.xml.
 */
public class ConnectOptionsTest {
    private static GatewayConfigParser parser;
    private static GatewayContextResolver resolver;

    @BeforeClass
    public static void init() {
        parser = new GatewayConfigParser();

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            File keyStoreFile = new File(classLoader.getResource("keystore.db").toURI());

            resolver = new GatewayContextResolver(new File(keyStoreFile.getParent()), null, null);
        } catch (Exception ex) {
            Assert.fail("Failed to load keystore.db, unable to init test due to exception: " + ex);
        }
    }

    @Test
    public void testSslCiphersOption() throws Exception {
        expectSuccess("ssl.ciphers", "  FOO,BAR ", "ssl.ciphers", new String[]{"FOO", "BAR"});

        expectParseFailure("ssl.ciphers", "FOO, BAR");
        expectParseFailure("ssl.ciphers", "");
    }

    @Test
    public void testTcpTransportOption() throws Exception {
        expectSuccess("tcp.transport", "ws://localhost:4444", TransportOptionNames.TCP_TRANSPORT, URI
                .create("ws://localhost:4444"));
        expectSuccess("tcp.transport", "ws://localhost", TransportOptionNames.TCP_TRANSPORT, URI.create("ws://localhost"));

        expectValidateFailure("tcp.transport", "//not.absolute");
        expectValidateFailure("tcp.transport", "-1");
        expectValidateFailure("tcp.transport", "");
        expectValidateFailure("tcp.transport", null);
    }

    @Test
    public void testHttpTransportOption() throws Exception {
        expectSuccess("http.transport", "tcp://127.0.0.1:80", "http[http/1.1].transport", URI.create("tcp://127.0.0.1:80"));

        expectValidateFailure("tcp.transport", "//not.absolute");
        expectValidateFailure("tcp.transport", "-1");
        expectValidateFailure("tcp.transport", "");
        expectValidateFailure("tcp.transport", null);
    }

    @Test
    public void testSslTransportOption() throws Exception {
        expectSuccess("ssl.transport", "tcp://127.0.0.1:443", TransportOptionNames.SSL_TRANSPORT, URI
                .create("tcp://127.0.0.1:443"));

        expectValidateFailure("ssl.transport", "//not.absolute");
        expectValidateFailure("ssl.transport", "-1");
        expectValidateFailure("ssl.transport", "");
        expectValidateFailure("ssl.transport", null);
    }

    @Test
    public void testSslEncryptionOption() throws Exception {
        expectSuccess("ssl.encryption", "enabled", TransportOptionNames.SSL_ENCRYPTION_ENABLED, Boolean.TRUE);
        expectSuccess("ssl.encryption", "disabled", TransportOptionNames.SSL_ENCRYPTION_ENABLED, Boolean.FALSE);

        expectParseFailure("ssl.encryption", "junk");
    }

    //
    // Test ware beneath
    //

    enum TestResult {
        PARSE_FAILURE,
        VALIDATE_FAILURE,
        SUCCESS
    }

    void expectSuccess(String optionName,
                       String optionValue,
                       String optionsMapKey,
                       Object expectedValue,
                       Object... extras) throws Exception {
        runTestCase(optionName, optionValue, TestResult.SUCCESS, optionsMapKey, expectedValue, extras);
    }

    void expectParseFailure(String optionName,
                            String optionValue) throws Exception {
        runTestCase(optionName, optionValue, TestResult.PARSE_FAILURE, null, null, null);
    }

    void expectValidateFailure(String optionName,
                               String optionValue) throws Exception {
        runTestCase(optionName, optionValue, TestResult.VALIDATE_FAILURE, null, null, null);
    }

    void runTestCase(String optionName,
                     String optionValue,
                     TestResult expectedResult,
                     String expectedKey,
                     Object expectedValue,
                     Object... extras) throws Exception {


        File configFile = null;
        configFile =
                createTempFileFromResource("org/kaazing/gateway/server/config/parse/data/gateway-config-connect-options" +
                                "-template.xml",
                        optionName, optionValue);

        GatewayConfigDocument doc = null;
        try {
            doc = parser.parse(configFile);
        } catch (Exception e) {
            if (expectedResult == TestResult.PARSE_FAILURE) {
//                System.out.println("Caught expected parse exception " + e);
                return;
            } else {
                throw e;
            }
        }
        Assert.assertNotNull(doc);
        ServiceConnectOptionsType connectOptionsType = doc.getGatewayConfig().getServiceArray(0).getConnectOptions();
        ConnectOptionsContext connectOptionsContext = null;
        try {
            connectOptionsContext = new DefaultConnectOptionsContext(connectOptionsType);
        } catch (Exception e) {
            if (expectedResult == TestResult.VALIDATE_FAILURE) {
//                System.out.println("Caught expected validate exception " + e);
                return;
            } else {
                throw e;
            }
        }
        Map<String, Object> optionsMap = connectOptionsContext.asOptionsMap();
        if (expectedValue instanceof Object[]) {
            assertArrayEquals((Object[]) expectedValue, (Object[]) optionsMap.get(expectedKey));
        } else {
            assertEquals(expectedValue, optionsMap.get(expectedKey));
        }
        if (extras != null) {
            if (extras.length % 2 != 0) {
                throw new IllegalStateException("Test case error: must provide key and value for options map validation.");
            } else {
                for (int i = 0; i < extras.length; i += 2) {
                    String k = (String) extras[i];
                    Object v = extras[i + 1];
                    assertEquals(v, optionsMap.get(k));
                }
            }
        }
        if (expectedResult != TestResult.SUCCESS) {
            throw new IllegalStateException(
                    "Unexpected successful valid accept/connect option " + optionName + " with value " + optionValue);
        }

    }

    private File createTempFileFromResource(String resourceName, String... values) throws IOException {
        File file = File.createTempFile("gateway-config", "xml");
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream is = classLoader.getResource(resourceName).openStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(is.available());
        int datum;
        while ((datum = is.read()) != -1) {
            bos.write(datum);
        }
        final String replacedContent = MessageFormat.format(bos.toString("UTF-8"), values);
//        System.out.println(replacedContent);
        ByteArrayInputStream bis = new ByteArrayInputStream(replacedContent.getBytes("UTF-8"));
        FileOutputStream fos = new FileOutputStream(file);
        while ((datum = bis.read()) != -1) {
            fos.write(datum);
        }
        fos.flush();
        fos.close();
        return file;
    }

    private File createTempFileFromResource(String resourceName) throws IOException {
        File file = File.createTempFile("gateway-config", "xml");
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream is = classLoader.getResource(resourceName).openStream();
        FileOutputStream fos = new FileOutputStream(file);
        int datum;
        while ((datum = is.read()) != -1) {
            fos.write(datum);
        }
        fos.flush();
        fos.close();
        return file;
    }

}
