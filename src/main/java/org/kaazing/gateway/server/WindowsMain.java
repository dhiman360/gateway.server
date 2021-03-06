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

package org.kaazing.gateway.server;

import org.apache.commons.cli.HelpFormatter;

/**
 * Main entry point for gateway process (at least when being called by the start scripts--it's possible to call Gateway directly
 * from other Java code as well.) This particular version is for Windows, where the directories are set up to be directly under
 * GATEWAY_HOME, and we preprocess the argument list to 'canonicalize' it so it looks like the standard command line, but also so
 * we specialize help messages.
 * <p/>
 * As of 3.2 we support two arguments to main: /config=<configFile> /help (we also support the form /config:configFile and
 * -config=configFile and -config:configFile The value of <configFile> is considered as a path to the gateway configuration
 * file--NOT just a file name. If provided by the caller, the property is converted to a system property and passed to Gateway,
 * rather than requiring Gateway to handle a new input vector.
 */
public class WindowsMain extends Main {

    /**
     * @param args
     */
    public static void main(String... args) throws Exception {
        // Rather than writing a completely separate parser, we're going to
        // transform the arguments that we have so they appear to be in the
        // right format.  Specifically, if we get an argument of the form
        // '/xxx=yyy', we'll substitute '--' for '/'.  It turns out that .bat
        // files actually replace '=' with space, which is what we want.
        // Unfortunately we can't easily handle the ':' case quite so easily,
        // (though product management has said they're okay with that.)
        HelpFormatter formatter = new HelpFormatter();
        formatter.setLongOptPrefix("/");
        formatter.setOptPrefix("/");

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("/")) {
                if (arg.length() == 1) {
                    arg = "-";
                } else {
                    arg = "--" + arg.substring(1);
                }
            }

            args[i] = arg;
        }

        GatewayCommandProcessor commandProcessor = new GatewayCommandProcessor(formatter);
        commandProcessor.launchGateway(args);
    }
}
