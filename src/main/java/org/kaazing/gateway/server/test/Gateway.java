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

package org.kaazing.gateway.server.test;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.KeyStore;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import javax.management.MBeanServer;
import org.kaazing.gateway.server.Launcher;
import org.kaazing.gateway.server.config.sep2014.AuthenticationType;
import org.kaazing.gateway.server.config.sep2014.AuthenticationType.AuthorizationMode;
import org.kaazing.gateway.server.config.sep2014.AuthenticationType.HttpChallengeScheme;
import org.kaazing.gateway.server.config.sep2014.AuthorizationConstraintType;
import org.kaazing.gateway.server.config.sep2014.ClusterConnectOptionsType;
import org.kaazing.gateway.server.config.sep2014.ClusterType;
import org.kaazing.gateway.server.config.sep2014.CrossSiteConstraintType;
import org.kaazing.gateway.server.config.sep2014.GatewayConfigDocument;
import org.kaazing.gateway.server.config.sep2014.GatewayConfigDocument.GatewayConfig;
import org.kaazing.gateway.server.config.sep2014.LoginModuleOptionsType;
import org.kaazing.gateway.server.config.sep2014.LoginModuleType;
import org.kaazing.gateway.server.config.sep2014.LoginModulesType;
import org.kaazing.gateway.server.config.sep2014.MimeMappingType;
import org.kaazing.gateway.server.config.sep2014.RealmType;
import org.kaazing.gateway.server.config.sep2014.SecurityStoreType;
import org.kaazing.gateway.server.config.sep2014.SecurityStoreType.Type;
import org.kaazing.gateway.server.config.sep2014.SecurityType;
import org.kaazing.gateway.server.config.sep2014.ServiceAcceptOptionsType;
import org.kaazing.gateway.server.config.sep2014.ServiceConnectOptionsType;
import org.kaazing.gateway.server.config.sep2014.ServiceDefaultsType;
import org.kaazing.gateway.server.config.sep2014.ServicePropertiesType;
import org.kaazing.gateway.server.config.sep2014.ServiceType;
import org.kaazing.gateway.server.config.sep2014.SuccessType;
import org.kaazing.gateway.server.context.GatewayContext;
import org.kaazing.gateway.server.context.resolve.ContextResolver;
import org.kaazing.gateway.server.context.resolve.DefaultSecurityContext;
import org.kaazing.gateway.server.context.resolve.GatewayContextResolver;
import org.kaazing.gateway.server.test.config.AuthorizationConstraintConfiguration;
import org.kaazing.gateway.server.test.config.ClusterConfiguration;
import org.kaazing.gateway.server.test.config.CrossOriginConstraintConfiguration;
import org.kaazing.gateway.server.test.config.GatewayConfiguration;
import org.kaazing.gateway.server.test.config.LoginModuleConfiguration;
import org.kaazing.gateway.server.test.config.NestedServicePropertiesConfiguration;
import org.kaazing.gateway.server.test.config.RealmConfiguration;
import org.kaazing.gateway.server.test.config.SecurityConfiguration;
import org.kaazing.gateway.server.test.config.ServiceConfiguration;
import org.kaazing.gateway.server.test.config.ServiceDefaultsConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import static java.beans.Introspector.getBeanInfo;
import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;

public class Gateway {

    private enum State {
        STARTING, STARTED, STOPPING, STOPPED
    }

    private final Launcher launcher = new Launcher();
    private volatile State state = State.STOPPED;

    public void start(GatewayConfiguration configuration) throws Exception {
        switch (state) {
            case STOPPED:
                state = State.STARTING;
                break;
        }

        switch (state) {
            case STARTING:
                GatewayContext context = createGatewayContext(configuration);
                launcher.init(context);
                state = State.STARTED;
                break;
        }
    }

    GatewayContext createGatewayContext(GatewayConfiguration configuration) throws Exception {
        GatewayConfigDocument gatewayConfigDocument = GatewayConfigDocument.Factory.newInstance();
        GatewayConfig gatewayConfig = gatewayConfigDocument.addNewGatewayConfig();

        setCluster(gatewayConfig, configuration.getCluster());
        setServiceDefaults(gatewayConfig, configuration.getServiceDefaults());
        appendServices(gatewayConfig, configuration.getServices());
        if (configuration.getSecurity() != null) {
            appendRealms(gatewayConfig, configuration.getSecurity().getRealms());
            appendKeyStoreTrustStore(gatewayConfig, configuration.getSecurity());
        }

        SecurityContextResolver securityResolver = new SecurityContextResolver(configuration.getSecurity());

        File webRootDir = configuration.getWebRootDirectory();
        File tempDir = configuration.getTempDirectory();

        MBeanServer jmxMBeanServer = configuration.getJmxMBeanServer();

        GatewayContextResolver resolver = new GatewayContextResolver(securityResolver, webRootDir, tempDir,
                jmxMBeanServer);
        GatewayContext context = resolver.resolve(gatewayConfigDocument,
                asProperties(configuration.getProperties()));
        return context;
    }

    @SuppressWarnings("deprecation")
    private void appendKeyStoreTrustStore(GatewayConfig gatewayConfig, SecurityConfiguration securityConfiguration) {
        SecurityType security = gatewayConfig.getSecurityArray(0);
        // trustStore
        if (securityConfiguration.getTrustStore() != null) {
            SecurityStoreType trustStore = security.addNewTruststore();
            trustStore.setFile(securityConfiguration.getTrustStoreFile());
            if (securityConfiguration.getTrustStore().getType().equalsIgnoreCase("JCECKS")) {
                trustStore.setType(Type.JCEKS);
            } else if (securityConfiguration.getTrustStore().getType().equalsIgnoreCase("JKS")) {
                trustStore.setType(Type.JKS);
            }
            trustStore.setPasswordFile(securityConfiguration.getTrustStorePasswordFile());
        }

        // keyStore
        if (securityConfiguration.getKeyStore() != null) {
            SecurityStoreType keyStore = security.addNewKeystore();
            keyStore.setFile(securityConfiguration.getKeyStoreFile());
            if (securityConfiguration.getKeyStore().getType().equalsIgnoreCase("JKS")) {
                keyStore.setType(Type.JKS);
            } else {
                keyStore.setType(Type.JCEKS);
            }
            keyStore.setPasswordFile(securityConfiguration.getKeyStorePasswordFile());
        }
    }

    private void appendRealms(GatewayConfig gatewayConfig, List<RealmConfiguration> realms) {
        SecurityType security = gatewayConfig.addNewSecurity();
        for (RealmConfiguration realm : realms) {
            RealmType newRealm = security.addNewRealm();
            newRealm.setName(realm.getName());
            if (realm.getDescription() != null) {
                newRealm.setDescription(realm.getDescription());
            }

            AuthenticationType authenticationType = newRealm.addNewAuthentication();
            if (realm.getHttpChallengeScheme() != null) {
                authenticationType.setHttpChallengeScheme(HttpChallengeScheme.Enum.forString(realm
                        .getHttpChallengeScheme()));
            }
            for (String httpHeader : realm.getHttpHeaders()) {
                authenticationType.addHttpHeader(httpHeader);
            }
            for (String httpQueryParameter : realm.getHttpQueryParameters()) {
                authenticationType.addHttpQueryParameter(httpQueryParameter);
            }
            for (String httpCookie : realm.getHttpCookies()) {
                authenticationType.addHttpCookie(httpCookie);
            }
            String authenticationMode = realm.getAuthorizationMode();
            if (authenticationMode != null) {
                authenticationType.setAuthorizationMode(AuthorizationMode.Enum.forString(authenticationMode));
            }

            String sessionTimeout = realm.getSessionTimeout();
            if (sessionTimeout != null) {
                authenticationType.setSessionTimeout(sessionTimeout);
            }

            // if there are login modules initialize them
            LoginModulesType newLoginModules = null;
            if (!realm.getLoginModules().isEmpty()) {
                newLoginModules = authenticationType.addNewLoginModules();
            }
            for (LoginModuleConfiguration loginModuleConfig : realm.getLoginModules()) {

                LoginModuleType loginModule = newLoginModules.addNewLoginModule();
                loginModule.setType(loginModuleConfig.getType());
                if (loginModuleConfig.getSuccess() != null) {
                    loginModule.setSuccess(SuccessType.Enum.forString(loginModuleConfig.getSuccess()));
                }
                Node domNode = null;
                Document ownerDocument = null;
                LoginModuleOptionsType newOptions = null;
                Map<String, String> options = loginModuleConfig.getOptions();

                if (!options.isEmpty()) {
                    newOptions = loginModule.addNewOptions();
                    domNode = loginModule.getDomNode();
                    ownerDocument = domNode.getOwnerDocument();

                    Iterator<Entry<String, String>> optionsIter = options.entrySet().iterator();
                    while (optionsIter.hasNext()) {
                        Entry<String, String> option = optionsIter.next();
                        Element newElement = ownerDocument.createElementNS(domNode.getNamespaceURI(), option.getKey());
                        Text newTextNode = ownerDocument.createTextNode(option.getValue());
                        newElement.appendChild(newTextNode);
                        newOptions.getDomNode().appendChild(newElement);
                    }
                }
            }
        }
    }

    private void setServiceDefaults(GatewayConfig gatewayConfig,
                                    ServiceDefaultsConfiguration serviceDefaultsConfiguration) {
        if (serviceDefaultsConfiguration == null) {
            return;
        }
        try {
            ServiceDefaultsType serviceDefaults = gatewayConfig.addNewServiceDefaults();

            // accept options
            Map<String, String> configuredAcceptOptions = serviceDefaultsConfiguration.getAcceptOptions();
            if (!configuredAcceptOptions.isEmpty()) {
                ServiceAcceptOptionsType newAcceptOption = serviceDefaults.addNewAcceptOptions();
                appendAcceptOptions(newAcceptOption, configuredAcceptOptions);
            }

            // mime-mappings
            Map<String, String> mimeMappings = serviceDefaultsConfiguration.getMimeMappings();
            if (!mimeMappings.isEmpty()) {
                for (Entry<String, String> mimeMappingEntry : mimeMappings.entrySet()) {
                    MimeMappingType mimeMapping = serviceDefaults.addNewMimeMapping();
                    mimeMapping.setExtension(mimeMappingEntry.getKey());
                    mimeMapping.setMimeType(mimeMappingEntry.getValue());
                }
            }
        } catch (Exception e) {
            // TODO
            e.printStackTrace();
        }
    }

    private void appendAcceptOptions(ServiceAcceptOptionsType newAcceptOptions,
                                     Map<String, String> configuredAcceptOptions) throws Exception {
        BeanInfo acceptOptionsBeanInfo = getBeanInfo(ServiceAcceptOptionsType.class,
                ServiceAcceptOptionsType.class.getSuperclass());
        PropertyDescriptor[] acceptOptionsPropertiesInfo = acceptOptionsBeanInfo.getPropertyDescriptors();
        for (PropertyDescriptor acceptOptionPropertyInfo : acceptOptionsPropertiesInfo) {
            String acceptOptionPropertyName = acceptOptionPropertyInfo.getName();
            if (acceptOptionPropertyInfo.getReadMethod().getName().startsWith("isSet")) {
                // skip boolean isSetXXX methods
                continue;
            }
            String acceptOptionName = camelCaseToDottedLowerCase(acceptOptionPropertyName);
            String acceptOptionValue = configuredAcceptOptions.get(acceptOptionName);
            if (acceptOptionValue != null) {
                setAcceptOption(newAcceptOptions, acceptOptionPropertyInfo, acceptOptionValue);
            }
        }
    }

    private static Properties asProperties(Map<String, String> propertiesMap) {
        Properties properties = new Properties();
        for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
            String propertyName = entry.getKey();
            String propertyValue = entry.getValue();
            properties.setProperty(propertyName, propertyValue);
        }
        return properties;
    }

    public void stop() throws Exception {
        switch (state) {
            case STARTED:
                state = State.STOPPING;
        }

        switch (state) {
            case STOPPING:
                launcher.destroy();
                state = State.STOPPED;
                break;
        }
    }

    private void appendServices(GatewayConfig newGatewayConfig, Collection<ServiceConfiguration> services) throws Exception {
        // services
        for (ServiceConfiguration service : services) {
            ServiceType newService = newGatewayConfig.addNewService();

            setType(service, newService);
            setRealmName(service, newService);

            appendBalances(newService, service);

            appendAccepts(newService, service);
            appendAcceptOptions(newService, service);

            appendConnects(newService, service);
            appendConnectOptions(newService, service);

            appendProperties(newService, service);

            appendAuthorizationConstraints(newService, service);

            appendCrossOriginConstraints(newService, service);

            appendMimeMappings(newService, service);
        }
    }

    private void setRealmName(ServiceConfiguration service, ServiceType newService) {
        String realmName = service.getRealmName();
        if (realmName != null) {
            newService.setRealmName(realmName);
        }
    }

    private void setType(ServiceConfiguration service, ServiceType newService) {
        String type = service.getType();
        if (type != null) {
            newService.setType(type);
        }
    }

    private void appendBalances(ServiceType newService, ServiceConfiguration service) {
        // balances
        Set<URI> balances = service.getBalances();
        String[] newBalances = new String[balances.size()];
        int i = 0;
        for (URI balance : balances) {
            newBalances[i++] = balance.toASCIIString();
        }
        newService.setBalanceArray(newBalances);
    }

    private void appendAccepts(ServiceType newService, ServiceConfiguration service) {
        // accepts
        Set<URI> accepts = service.getAccepts();
        if (!accepts.isEmpty()) {
            String[] newAccepts = new String[accepts.size()];
            int i = 0;
            for (URI accept : accepts) {
                newAccepts[i++] = accept.toASCIIString();
            }
            newService.setAcceptArray(newAccepts);
        }
    }

    private void appendAcceptOptions(ServiceType newService, ServiceConfiguration service) throws Exception {
        // accept-options
        try {
            Map<String, String> acceptOptions = service.getAcceptOptions();
            if (!acceptOptions.isEmpty()) {
                ServiceAcceptOptionsType newAcceptOptions = newService.addNewAcceptOptions();
                appendAcceptOptions(newAcceptOptions, acceptOptions);
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void setAcceptOption(ServiceAcceptOptionsType newAcceptOptions,
                                 PropertyDescriptor acceptOptionPropertyInfo,
                                 String acceptOptionValue) throws Exception {

        Method setterMethod = acceptOptionPropertyInfo.getWriteMethod();
        Class<?> acceptOptionPropertyType = acceptOptionPropertyInfo.getPropertyType();
        if (acceptOptionPropertyType == String.class) {
            setterMethod.invoke(newAcceptOptions, acceptOptionValue);
        } else {
            // assumes Enum-style naming convention for static String -> XmlObject value type
            Method forString = acceptOptionPropertyType.getDeclaredMethod("forString", String.class);
            Object parsedAcceptOptionValue = forString.invoke(null, acceptOptionValue);
            setterMethod.invoke(newAcceptOptions, parsedAcceptOptionValue);
        }
    }

    private void appendConnects(ServiceType newService, ServiceConfiguration service) {
        // connects
        Set<URI> connects = service.getConnects();
        if (!connects.isEmpty()) {
            String[] newConnects = new String[connects.size()];
            int i = 0;
            for (URI connect : connects) {
                newConnects[i++] = connect.toASCIIString();
            }
            newService.setConnectArray(newConnects);
        }
    }

    public void appendConnectOptions(ServiceType newService, ServiceConfiguration service) throws Exception {
        try {
            Map<String, String> connectOptions = service.getConnectOptions();
            if (!connectOptions.isEmpty()) {
                ServiceConnectOptionsType newConnectOptions = newService.addNewConnectOptions();
                appendConnectOptions(newConnectOptions, connectOptions);
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void appendConnectOptions(ServiceConnectOptionsType newConnectOptions, Map<String, String> connectOptions) throws
            Exception {
        BeanInfo connectOptionsBeanInfo = getBeanInfo(ServiceConnectOptionsType.class,
                ServiceConnectOptionsType.class.getSuperclass());
        PropertyDescriptor[] connectOptionsPropertiesInfo = connectOptionsBeanInfo.getPropertyDescriptors();
        for (PropertyDescriptor connectOptionPropertyInfo : connectOptionsPropertiesInfo) {
            String connectOptionPropertyName = connectOptionPropertyInfo.getName();
            if (connectOptionPropertyInfo.getReadMethod().getName().startsWith("isSet")) {
                // skip boolean isSetXXX methods
                continue;
            }
            String connectOptionName = camelCaseToDottedLowerCase(connectOptionPropertyName);
            String connectOptionValue = connectOptions.get(connectOptionName);
            if (connectOptionValue != null) {
                setConnectOption(newConnectOptions, connectOptionPropertyInfo, connectOptionValue);
            }
        }
    }

    private void setConnectOption(ServiceConnectOptionsType newConnectOptions,
                                  PropertyDescriptor connectOptionPropertyInfo,
                                  String connectOptionValue) throws Exception {
        Method setterMethod = connectOptionPropertyInfo.getWriteMethod();
        Class<?> connectOptionPropertyType = connectOptionPropertyInfo.getPropertyType();
        if (connectOptionPropertyType == String.class) {
            setterMethod.invoke(newConnectOptions, connectOptionValue);
        } else {
            // assumes Enum-style naming convention for static String -> XmlObject value type
            Method forString = connectOptionPropertyType.getDeclaredMethod("forString", String.class);
            Object parsedConnectOptionValue = forString.invoke(null, connectOptionValue);
            setterMethod.invoke(newConnectOptions, parsedConnectOptionValue);
        }
    }

    private void appendProperties(ServiceType newService, ServiceConfiguration service) {
        // simple properties
        ServicePropertiesType newProperties = newService.addNewProperties();
        Node domNode = newProperties.getDomNode();
        Document ownerDocument = domNode.getOwnerDocument();
        appendSimpleProperties(service.getProperties(), domNode, ownerDocument);

        // nested properties
        for (NestedServicePropertiesConfiguration nestedProperty : service.getNestedProperties()) {
            Element newElement = ownerDocument.createElementNS(domNode.getNamespaceURI(),
                    nestedProperty.getConfigElementName());
            appendNestedProperties(nestedProperty, newElement, ownerDocument);
            domNode.appendChild(newElement);
        }
    }

    private void appendSimpleProperties(Map<String, String> properties, Node domNode, Document ownerDocument) {
        for (Entry<String, String> property : properties.entrySet()) {
            Element newElement = ownerDocument.createElementNS(domNode.getNamespaceURI(), property.getKey());
            Text newTextNode = ownerDocument.createTextNode((String) property.getValue());
            newElement.appendChild(newTextNode);
            domNode.appendChild(newElement);
        }
    }

    private void appendNestedProperties(NestedServicePropertiesConfiguration nestedPropertyConfig,
                                        Node domNode,
                                        Document ownerDocument) {
        appendSimpleProperties(nestedPropertyConfig.getSimpleProperties(), domNode, ownerDocument);
        for (NestedServicePropertiesConfiguration nestedProperty : nestedPropertyConfig.getNestedProperties()) {
            Element newElement = ownerDocument.createElementNS(domNode.getNamespaceURI(),
                    nestedProperty.getConfigElementName());
            appendNestedProperties(nestedProperty, newElement, ownerDocument);
        }
    }

    private void appendAuthorizationConstraints(ServiceType newService, ServiceConfiguration service) {
        // authorization-constraints
        List<AuthorizationConstraintConfiguration> constraints = service.getAuthorizationConstraints();
        if (!constraints.isEmpty()) {
            for (AuthorizationConstraintConfiguration constraint : constraints) {
                AuthorizationConstraintType newConstraint = newService.addNewAuthorizationConstraint();
                Set<String> requiredRoles = constraint.getRequiredRoles();
                String[] array = new String[requiredRoles.size()];
                int i = 0;
                for (String requiredRole : requiredRoles) {
                    array[i++] = requiredRole;
                }
                // TODO: multiple requireRole's per authorization constraint?
                newConstraint.setRequireRoleArray(array);
            }
        }
    }

    private void appendCrossOriginConstraints(ServiceType newService, ServiceConfiguration service) {
        // cross-site-constraints
        List<CrossOriginConstraintConfiguration> constraints = service.getCrossOriginConstraints();
        if (!constraints.isEmpty()) {
            for (CrossOriginConstraintConfiguration constraint : constraints) {
                CrossSiteConstraintType newConstraint = newService.addNewCrossSiteConstraint();
                String allowOrigin = constraint.getAllowOrigin();
                if (allowOrigin != null) {
                    newConstraint.setAllowOrigin(allowOrigin);
                }
                String allowHeaders = constraint.getAllowHeaders();
                if (allowHeaders != null) {
                    newConstraint.setAllowHeaders(allowHeaders);
                }
                String allowMethods = constraint.getAllowMethods();
                if (allowMethods != null) {
                    newConstraint.setAllowMethods(allowMethods);
                }
            }
        }
    }

    private void appendMimeMappings(ServiceType newService, ServiceConfiguration service) {
        // mime-mappings
        Map<String, String> mimeMappings = service.getMimeMappings();
        if (!mimeMappings.isEmpty()) {
            for (Entry<String, String> entry : mimeMappings.entrySet()) {
                MimeMappingType newMimeMapping = newService.addNewMimeMapping();
                String extension = entry.getKey();
                String mimeType = entry.getValue();
                newMimeMapping.setExtension(extension);
                newMimeMapping.setMimeType(mimeType);
            }
        }
    }

    private void setCluster(GatewayConfig gatewayConfig, ClusterConfiguration cluster) {
        if (cluster == null) {
            return;
        }
        ClusterType newCluster = gatewayConfig.addNewCluster();
        Collection<URI> accepts = cluster.getAccepts();
        Collection<URI> connects = cluster.getConnects();

        for (URI accept : accepts) {
            newCluster.addAccept(accept.toASCIIString());
        }

        for (URI connect : connects) {
            newCluster.addConnect(connect.toASCIIString());
        }

        String name = cluster.getName();
        newCluster.setName(name);

        ClusterConnectOptionsType connectOptions = newCluster.getConnectOptions();
        String awsAccessKeyId = cluster.getAwsAccessKeyId();
        if (awsAccessKeyId != null) {
            connectOptions.setAwsAccessKeyId(awsAccessKeyId);
        }
        String awsSecretKey = cluster.getAwsSecretKeyId();
        if (awsSecretKey != null) {
            connectOptions.setAwsSecretKey(awsSecretKey);
        }

    }

    private static String camelCaseToDottedLowerCase(String camelCaseName) {
        StringBuilder dottedLowerCaseName = new StringBuilder();

        for (int i = 0; i < camelCaseName.length(); i++) {
            char camelCaseChar = camelCaseName.charAt(i);
            // note: http.keepaliveTimeout, not http.keepalive.timeout
            if (i < 8 && isUpperCase(camelCaseChar)) {
                dottedLowerCaseName.append('.');
                dottedLowerCaseName.append(toLowerCase(camelCaseChar));
            } else {
                dottedLowerCaseName.append(camelCaseChar);
            }
        }

        return dottedLowerCaseName.toString();
    }

    private static final class SecurityContextResolver implements ContextResolver<SecurityType, DefaultSecurityContext> {
        private KeyStore keyStore;
        private char[] keyStorePassword;
        private KeyStore trustStore;
        private String keyStoreFile;
        private char[] trustStorePassword;

        @SuppressWarnings("deprecation")
        SecurityContextResolver(SecurityConfiguration configuration) {
            if (configuration == null) {
                keyStore = null;
                keyStorePassword = null;
                trustStore = null;
                keyStoreFile = null;
            } else {
                if (configuration.getKeyStore() != null) {
                    keyStore = configuration.getKeyStore();
                    keyStoreFile = configuration.getKeyStoreFile();
                }
                if (configuration.getKeyStorePassword() != null) {
                    keyStorePassword = configuration.getKeyStorePassword();
                }
                if (configuration.getTrustStore() != null) {
                    trustStore = configuration.getTrustStore();
                }
                if (configuration.getTrustStorePassword() != null) {
                    trustStorePassword = configuration.getTrustStorePassword();
                }
            }
        }

        @Override
        public DefaultSecurityContext resolve(SecurityType config) throws Exception {
            String keyStoreFilePath = null;
            String keyStorePasswordFile = null;
            String trustStoreFile = null;
            String trustStoreFilePath = null;
            return new DefaultSecurityContext(keyStore, keyStoreFile, keyStoreFilePath, keyStorePassword,
                    keyStorePasswordFile, trustStore, trustStoreFile, trustStoreFilePath, trustStorePassword);
        }
    }
}
