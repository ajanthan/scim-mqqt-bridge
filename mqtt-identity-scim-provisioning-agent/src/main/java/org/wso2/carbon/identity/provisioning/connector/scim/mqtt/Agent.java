package org.wso2.carbon.identity.provisioning.connector.scim.mqtt;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.hawtbuf.codec.ObjectCodec;
import org.fusesource.mqtt.client.*;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningException;
import org.wso2.carbon.identity.provisioning.ProvisioningEntity;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Agent {
    private static Log log = LogFactory.getLog(Agent.class);

    public static void main(String[] args) {
        // String filePath= Thread.currentThread().getContextClassLoader().getResource("client-truststore.jks").getFile();


        if (args.length != 1) {
            log.error("Config file is missing");
            System.exit(-1);
        }

        Properties configProperties = getConfig(args[0]);

        String truststore = configProperties.getProperty(Constants.SSL_TRUSTSTORE);
        String truststorePassword = configProperties.getProperty(Constants.SSL_TRUSTSTORE_PASSWORD);
        System.setProperty("javax.net.ssl.trustStore", truststore);
        System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);

        List<Property> properties = getSCIMConfig(configProperties);

        log.info("Starting the agent....");

        MQTTBasedProvisioningConnector provisioningConnector = new MQTTBasedProvisioningConnector();
        try {
            provisioningConnector.init(properties.toArray(new Property[0]));
        } catch (IdentityProvisioningException e) {
            log.error("Unable to initialize SCIM connector ", e);
            System.exit(-1);
        }
        String host = configProperties.getProperty(Constants.MQTT_HOST);
        if (host == null || "".equals(host)) {
            log.warn("MQTT Host not valid. Using localhost");
            host = "localhost";
        }
        int port = 1883;
        try {
            port = Integer.parseInt(configProperties.getProperty(Constants.MQTT_PORT));
        } catch (NumberFormatException e) {
            log.warn("MQTT Port not valid. Using 1883");
            //port=1883
        }


        MQTT mqtt = new MQTT();
        try {
            mqtt.setHost(host, port);
        } catch (URISyntaxException e) {
            log.error(e);
        }
        String username = configProperties.getProperty(Constants.MQTT_USERNAME);
        String password = configProperties.getProperty(Constants.MQTT_PASSWORD);
        if (username != null && !"".equals(username) && password != null) {
            mqtt.setUserName(username);
            mqtt.setPassword(password);
        }

        final CallbackConnection connection = mqtt.callbackConnection();
        connection.listener(new Listener() {

            public void onDisconnected() {
                log.info("Disconnected");
            }

            public void onConnected() {
                log.info("Connected");
            }

            public void onPublish(UTF8Buffer topic, Buffer payload, Runnable ack) {
                ObjectCodec<ProvisioningEntity> codec = new ObjectCodec<>();
                ProvisioningEntity msg;
                try {
                    msg = codec.decode(new DataInputStream(payload.in()));
                    try {
                        log.info("Received entity: [op=" + msg.getOperation().name() + ",type=" + msg.getEntityType().name() + "]");
                        provisioningConnector.provision(msg);
                    } catch (IdentityProvisioningException e) {
                        log.error(e);
                    }
                } catch (IOException e) {
                    log.error(e);
                }
                ack.run();
            }

            public void onFailure(Throwable value) {
                log.error(value);
            }
        });
        connection.connect(new Callback<Void>() {
            public void onFailure(Throwable value) {
                log.error(value);
            }

            // Once we connect..
            public void onSuccess(Void v) {

                // Subscribe to a topic
                String topicName = configProperties.getProperty(Constants.MQTT_TOPIC);
                if ("".equals(topicName) || topicName == null) {
                    log.warn("Invalid topic name. Using mqtt-scim-bridge");
                    topicName = "mqtt-scim-bridge";
                }
                Topic[] topics = { new Topic(topicName, QoS.AT_LEAST_ONCE) };
                connection.subscribe(topics, new Callback<byte[]>() {
                    public void onSuccess(byte[] qoses) {
                        log.info("Subscribed");
                    }

                    public void onFailure(Throwable value) {
                        log.error(value);
                    }
                });


            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                connection.disconnect(new Callback<Void>() {
                    public void onSuccess(Void v) {
                        log.info("Disconnected bye!");
                    }

                    public void onFailure(Throwable value) {
                        log.error(value);
                    }
                });
            }
        });

        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error(e);
            }

        }

    }

    private static Properties getConfig(String configFile) {
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(configFile);
        } catch (FileNotFoundException e) {
            log.error("Config file not found", e);
            System.exit(-1);
        }
        Properties configProperties = new Properties();
        try {
            configProperties.load(fileReader);
        } catch (IOException e) {
            log.error("Could not read config file", e);
            System.exit(-1);
        }

        return configProperties;
    }

    private static List<Property> getSCIMConfig(Properties configProperties) {
        List<Property> properties = new ArrayList<>();

        Property scimUsername = new Property();
        scimUsername.setName(Constants.SCIM_USERNAME);
        scimUsername.setDisplayName("Username");
        scimUsername.setDisplayOrder(1);
        scimUsername.setRequired(true);
        scimUsername.setValue(configProperties.getProperty(Constants.SCIM_USERNAME));

        Property scimUserPassword = new Property();
        scimUserPassword.setName(Constants.SCIM_PASSWORD);
        scimUserPassword.setDisplayName("Password");
        scimUserPassword.setConfidential(true);
        scimUserPassword.setDisplayOrder(2);
        scimUserPassword.setRequired(true);
        scimUserPassword.setValue(configProperties.getProperty(Constants.SCIM_PASSWORD));

        Property userEndpoint = new Property();
        userEndpoint.setName(Constants.SCIM_USER_EP);
        userEndpoint.setDisplayName("User Endpoint");
        userEndpoint.setDisplayOrder(3);
        userEndpoint.setRequired(true);
        userEndpoint.setValue(configProperties.getProperty(Constants.SCIM_USER_EP));

        Property groupEndpoint = new Property();
        groupEndpoint.setName(Constants.SCIM_GROUP_EP);
        groupEndpoint.setDisplayName("Group Endpoint");
        groupEndpoint.setDisplayOrder(4);
        groupEndpoint.setValue(configProperties.getProperty(Constants.SCIM_GROUP_EP));

        Property userStoreDomain = new Property();
        userStoreDomain.setName(Constants.SCIM_USERSTORE_DOMAIN);
        userStoreDomain.setDisplayName("User Store Domain");
        userStoreDomain.setDisplayOrder(5);
        userStoreDomain.setValue("SHIPUPDATE");

        Property passwordProvisioning = new Property();
        passwordProvisioning.setName(Constants.SCIM_ENABLE_PASSWORD_PROVISIONING);
        passwordProvisioning.setDisplayName("Enable Password Provisioning");
        passwordProvisioning.setDescription("Enable User password provisioning to a SCIM2 domain");
        passwordProvisioning.setDisplayOrder(6);
        passwordProvisioning.setValue("true");

        Property defaultPassword = new Property();
        defaultPassword.setName(Constants.SCIM_DEFAULT_PASSWORD);
        defaultPassword.setDisplayName("Default Password");
        defaultPassword.setDisplayOrder(7);
        defaultPassword.setValue("Admin@123");


        properties.add(scimUsername);
        properties.add(scimUserPassword);
        properties.add(userEndpoint);
        properties.add(groupEndpoint);
        properties.add(userStoreDomain);
        properties.add(passwordProvisioning);
        properties.add(defaultPassword);


        return properties;
    }
}
