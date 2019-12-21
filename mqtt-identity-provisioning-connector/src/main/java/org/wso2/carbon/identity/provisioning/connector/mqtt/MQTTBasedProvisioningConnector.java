/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.provisioning.connector.mqtt;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.hawtbuf.codec.ObjectCodec;
import org.fusesource.mqtt.client.Callback;
import org.fusesource.mqtt.client.CallbackConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.provisioning.*;
import org.wso2.carbon.identity.scim.common.group.SCIMGroupHandler;
import org.wso2.carbon.identity.scim.common.utils.IdentitySCIMException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

public class MQTTBasedProvisioningConnector extends AbstractOutboundProvisioningConnector {

    private static final long serialVersionUID = -2800777564581005554L;
    private static Log log = LogFactory.getLog(MQTTBasedProvisioningConnector.class);
    private static String userStoreDomain;
    private String mqttTopic;
    private MQTT mqtt;

    @Override
    public void init(Property[] provisioningProperties) throws IdentityProvisioningException {
        String host = null;
        int port = 0;
        String mqttUsername = null;
        String mqttPassword = null;
        if (provisioningProperties != null && provisioningProperties.length > 0) {

            for (Property property : provisioningProperties) {

                if (MQTTBasedProvisioningConnectorConstants.MQTT_HOST.equals(property.getName())) {
                    host = property.getValue();
                    if (host == null || "".equals(host)) {
                        host = "localhost";
                    }
                } else if (MQTTBasedProvisioningConnectorConstants.MQTT_PORT.equals(property.getName())) {
                    try {
                        port = Integer.parseInt(property.getValue());
                    } catch (NumberFormatException e) {
                        port = 1883;
                    }
                    if (port <= 0) {
                        port = 1883;
                    }

                } else if (MQTTBasedProvisioningConnectorConstants.MQTT_USERNAME.equals(property.getName())) {
                    mqttUsername = property.getValue();

                } else if (MQTTBasedProvisioningConnectorConstants.MQTT_PASSWORD.equals(property.getName())) {
                    mqttPassword = property.getValue();
                } else if (MQTTBasedProvisioningConnectorConstants.USERSTORE_DOMAIN.equals(property.getName())) {
                    userStoreDomain = property.getValue();

                } else if (MQTTBasedProvisioningConnectorConstants.MQTT_TOPIC.equals(property.getName())) {
                    mqttTopic = property.getValue();
                    if ("".equals(mqttTopic) || mqttTopic == null) {
                        log.warn("Invalid topic name. Using mqtt-scim-bridge");
                        mqttTopic = "mqtt-scim-bridge";
                    }
                }

                if (IdentityProvisioningConstants.JIT_PROVISIONING_ENABLED.equals(property
                    .getName()) && "1".equals(property.getValue())) {
                    jitProvisioningEnabled = true;
                }
            }
        }

        mqtt = new MQTT();
        try {
            mqtt.setHost(host, port);
        } catch (URISyntaxException e) {
            log.error(e);
            throw new IdentityProvisioningException(e);
        }
        if (mqttUsername != null && !"".equals(mqttUsername) && mqttPassword != null) {
            mqtt.setUserName(mqttUsername);
            mqtt.setPassword(mqttPassword);
        }
    }

    @Override
    public ProvisionedIdentifier provision(final ProvisioningEntity provisioningEntity)
        throws IdentityProvisioningException {
        if (provisioningEntity != null && userStoreDomain.equals(getDomainFromEntityName(provisioningEntity.getEntityName()))) {
            if (provisioningEntity.isJitProvisioning() && !isJitProvisioningEnabled()) {
                log.debug("JIT provisioning disabled for MQTT connector");
                return null;
            }
            log.info("Entity name: " + provisioningEntity.getEntityName());
            if (provisioningEntity.getEntityType() == ProvisioningEntityType.GROUP) {
                String roleNameWithDomain = "SHIPUPDATE" + "/" + provisioningEntity.getEntityName();
                if (provisioningEntity.getOperation() == ProvisioningOperation.DELETE) {
                    //
                } else if (provisioningEntity.getOperation() == ProvisioningOperation.POST) {
                    SCIMGroupHandler scimGroupHandler = new SCIMGroupHandler(CarbonContext.getThreadLocalCarbonContext().getTenantId());
                    try {
                        if (!scimGroupHandler.isGroupExisting(roleNameWithDomain)) {
                            //if no attributes - i.e: group added via mgt console, not via SCIM endpoint
                            //add META
                            scimGroupHandler.addMandatoryAttributes(roleNameWithDomain);
                        }
                    } catch (IdentitySCIMException e) {
                        throw new IdentityProvisioningException("Error retrieving group information from SCIM Tables.", e);
                    }

                }
            } //else {
//                log.info("SCIM group endpoint is not configured in Identity Provider configurations. Skip " +
//                    "performing " + provisioningEntity.getOperation() + " for outbound group resource.");
//            }

            final CallbackConnection connection = mqtt.callbackConnection();
            connection.connect(new Callback<Void>() {
                public void onFailure(Throwable value) {
                    // result.failure(value); // If we could not connect to the server.
                    log.error("Failed to connect to broker", value);
                }

                // Once we connect..
                public void onSuccess(Void v) {
                    log.info("Connected to broker");
                    // Subscribe to a topic
                    ObjectCodec<String> codec = new ObjectCodec<>();

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    try {
                        codec.encode(provisioningEntity, new DataOutputStream(outputStream));

                    } catch (IOException e) {
                        log.error("Failed to encode provisioning entity", e);
                    }
                    Buffer buffer = new Buffer(outputStream.toByteArray());

                    // Send a message to a topic
                    connection.publish(UTF8Buffer.utf8(mqttTopic), buffer, QoS.AT_LEAST_ONCE, false, new Callback<Void>() {
                        public void onSuccess(Void v) {
                            log.info("Published provisioning event to broker");
                        }

                        public void onFailure(Throwable value) {
                            log.error("Failed to publish to broker", value);
                        }
                    });
                    // To disconnect..
                    connection.disconnect(new Callback<Void>() {
                        public void onSuccess(Void v) {
                            // called once the connection is disconnected.
                        }

                        public void onFailure(Throwable value) {
                            // Disconnects never fail.
                        }
                    });
                }
            });
        }

        return null;

    }

    private String getDomainFromEntityName(String username) {
        int index;
        if ((index = username.indexOf("/")) > 0) {
            String domain = username.substring(0, index);
            return domain;
        } else {
            return "PRIMARY";
        }
    }
}
