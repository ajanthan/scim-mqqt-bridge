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
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.provisioning.AbstractProvisioningConnectorFactory;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author
 */
public class MQTTBasedProvisioningConnectorFactory extends AbstractProvisioningConnectorFactory {

    public static final String MQTT = "mqtt";
    private static final Log log = LogFactory.getLog(MQTTBasedProvisioningConnectorFactory.class);

    @Override
    /**
     * @throws IdentityProvisioningException
     */
    protected MQTTBasedProvisioningConnector buildConnector(Property[] provisioningProperties)
            throws IdentityProvisioningException {
        MQTTBasedProvisioningConnector scimProvisioningConnector = new MQTTBasedProvisioningConnector();
        scimProvisioningConnector.init(provisioningProperties);

        if (log.isDebugEnabled()) {
            log.debug("Created new connector of type : " + MQTT);
        }
        return scimProvisioningConnector;
    }

    @Override
    public String getConnectorType() {
        return MQTT;
    }

    @Override
    public List<Property> getConfigurationProperties() {
        List<Property> properties = new ArrayList<>();

        Property username = new Property();
        username.setName(MQTTBasedProvisioningConnectorConstants.MQTT_USERNAME);
        username.setDisplayName("Connection Username");
        username.setDisplayOrder(1);
        username.setRequired(true);

        Property userPassword = new Property();
        userPassword.setName(MQTTBasedProvisioningConnectorConstants.MQTT_PASSWORD);
        userPassword.setDisplayName("Connection Password");
        userPassword.setConfidential(true);
        userPassword.setDisplayOrder(2);
        userPassword.setRequired(true);

        Property host = new Property();
        host.setName(MQTTBasedProvisioningConnectorConstants.MQTT_HOST);
        host.setDisplayName("MQTT Server Host");
        host.setDisplayOrder(3);
        host.setRequired(true);

        Property port = new Property();
        port.setName(MQTTBasedProvisioningConnectorConstants.MQTT_PORT);
        port.setDisplayName("MQTT Server Port");
        port.setDisplayOrder(4);
        port.setRequired(true);


        Property topic = new Property();
        topic.setName(MQTTBasedProvisioningConnectorConstants.MQTT_TOPIC);
        topic.setDisplayName("MQTT Topic");
        topic.setDisplayOrder(5);

        Property passwordProvisioning = new Property();
        passwordProvisioning.setName(MQTTBasedProvisioningConnectorConstants.SCIM_ENABLE_PASSWORD_PROVISIONING);
        passwordProvisioning.setDisplayName("Enable Password Provisioning");
        passwordProvisioning.setDescription("Enable User password provisioning to a SCIM2 domain");
        passwordProvisioning.setDisplayOrder(6);

        Property defaultPassword = new Property();
        defaultPassword.setName(MQTTBasedProvisioningConnectorConstants.SCIM_DEFAULT_PASSWORD);
        defaultPassword.setDisplayName("Default Password");
        defaultPassword.setDisplayOrder(7);

        properties.add(username);
        properties.add(userPassword);
        properties.add(host);
        properties.add(port);
        properties.add(topic);
        properties.add(passwordProvisioning);
        properties.add(defaultPassword);

        return properties;
    }
}
