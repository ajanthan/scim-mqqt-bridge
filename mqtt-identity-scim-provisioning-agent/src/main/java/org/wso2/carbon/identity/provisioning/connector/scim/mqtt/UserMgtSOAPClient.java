package org.wso2.carbon.identity.provisioning.connector.scim.mqtt;

import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.transport.http.HTTPConstants;
import org.wso2.carbon.authenticator.stub.AuthenticationAdminStub;
import org.wso2.carbon.authenticator.stub.LoginAuthenticationExceptionException;
import org.wso2.carbon.um.ws.api.WSUserStoreManager;
import org.wso2.carbon.user.api.UserStoreException;

import java.rmi.RemoteException;

public class UserMgtSOAPClient {
    private  String serverUrl;


    private AuthenticationAdminStub authstub = null;
    private ConfigurationContext ctx;
    private String authCookie = null;
    private WSUserStoreManager remoteUserStoreManager = null;

    public void AddUser(String userName, String password, String [] roles)  {
        try {
            remoteUserStoreManager.addUser(userName, password, roles, null, null);
        } catch (org.wso2.carbon.user.core.UserStoreException e) {
            e.printStackTrace();
        }
    }
    /**
     * Authenticate to carbon as admin user and obtain the authentication cookie
     *
     * @param username
     * @param password
     * @return
     * @throws Exception
     */
    public String login(String username, String password) throws RemoteException, LoginAuthenticationExceptionException {
        //String cookie = null;
        boolean loggedIn = authstub.login(username, password, "localhost");
        if (loggedIn) {

            authCookie = (String) authstub._getServiceClient().getServiceContext().getProperty(
                HTTPConstants.COOKIE_STRING);
        }
        return authCookie;
    }

    /**
     * create web service client for RemoteUserStoreManager service from the wrapper api provided
     * in carbon - remote-usermgt component
     *
     * @throws org.wso2.carbon.user.core.UserStoreException
     */
    public void createRemoteUserStoreManager() throws UserStoreException {

        remoteUserStoreManager = new WSUserStoreManager(serverUrl, authCookie, ctx);
    }

    public void init(String serverUrl,String username,String password) throws RemoteException, LoginAuthenticationExceptionException, UserStoreException {
        this.serverUrl=serverUrl;
        String authEPR = serverUrl + "AuthenticationAdmin";
        authstub = new AuthenticationAdminStub(ctx, authEPR);
        ServiceClient client = authstub._getServiceClient();
        Options options = client.getOptions();
        options.setManageSession(true);
        options.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING, authCookie);

//        //set trust store properties required in SSL communication.
//        System.setProperty("javax.net.ssl.trustStore", truststore);
//        System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);


        //log in as admin user and obtain the cookie
        this.login(username, password);

        //create web service client
        this.createRemoteUserStoreManager();
    }

}
