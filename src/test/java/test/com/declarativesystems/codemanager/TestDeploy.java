package test.com.declarativesystems.codemanager;

import com.declarativesystems.pejava.codemanager.Deploy;
import com.declarativesystems.pejava.codemanager.DeployImpl;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;

import static org.junit.Assert.*;

public class TestDeploy {
    private Deploy deploy = new DeployImpl();
    private final static String PUPPET_DEPLOY_OK = "PUPPET_DEPLOY_OK";
    private static String PUPPET_MASTER_FQDN = "not ready yet";
    private final static String[] ENVIRONMENT = {"production"};
    private final static String CA_CERT_FILE = "/tmp/mock_code_manager_ssl/ca.crt";
    private static String CA_CERT;
    private static Process mockCodeManager;

    @BeforeClass
    public static void setup() {
        try {
            PUPPET_MASTER_FQDN = InetAddress.getLocalHost().getHostName();
            mockCodeManager = Runtime.getRuntime().exec("mock_code_manager");
            // wait for server to become ready
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // CA cert is generated in /tmp by the above command
            CA_CERT = IOUtils.toString(new FileInputStream(CA_CERT_FILE));
        } catch (IOException e) {
            // JRuby was too hard so just shell out and run the mock_code_manager rubygem
            System.err.println("************************************************************");
            System.err.println("* Your system is not setup for testing!                    *");
            System.err.println("*                                                          *");
            System.err.println("* Please install ruby, then 'gem install mock_code_manager *");
            System.err.println("************************************************************");
        }


    }

    @AfterClass
    public static void cleanup() {
        if (mockCodeManager != null) {
            mockCodeManager.destroyForcibly();
        }
    }

    @Test
    public void testOkWithCaCert() throws Exception {
        String ret = deploy.deployCode(PUPPET_MASTER_FQDN, PUPPET_DEPLOY_OK, CA_CERT, ENVIRONMENT);
        assertTrue(ret.contains("queued"));
    }

    @Test
    public void testOkWithoutCaCert() throws Exception {
        String ret = deploy.deployCode(PUPPET_MASTER_FQDN, PUPPET_DEPLOY_OK, null, ENVIRONMENT);
        assertTrue(ret.contains("queued"));
    }

    @Test
    public void testOkDeploysAllEnvironmentsWhenNotSpecified() throws Exception {
        String ret = deploy.deployCode(PUPPET_MASTER_FQDN, PUPPET_DEPLOY_OK, null, null);
        assertTrue(ret.contains("queued"));
    }

    public void testOkDeploysSpecificEnvironment() throws Exception {
        String ret = deploy.deployCode(PUPPET_MASTER_FQDN, PUPPET_DEPLOY_OK, null, ENVIRONMENT);
        assertTrue(ret.contains("queued"));
    }

    @Test(expected=javax.net.ssl.SSLHandshakeException.class)
    public void testWithWrongCaCert() throws Exception {
        String cert = IOUtils.toString(this.getClass().getResourceAsStream("/ca.pem"));
        deploy.deployCode(PUPPET_MASTER_FQDN, PUPPET_DEPLOY_OK, cert, ENVIRONMENT);
    }


    @Test
    public void testBadTokenWithCaCert() throws Exception {
        String ret = deploy.deployCode(PUPPET_MASTER_FQDN, "BAD TOKEN", CA_CERT, ENVIRONMENT);
        assertTrue(ret.contains("kind"));
    }


    @Test
    public void testBadTokenWithoutCaCert() throws Exception {
        String ret = deploy.deployCode(PUPPET_MASTER_FQDN, "BAD TOKEN", null, ENVIRONMENT);
        assertTrue(ret.contains("kind"));
    }

    @Test(expected=UnknownHostException.class)
    public void testGarbageHostname() throws Exception {
        deploy.deployCode("invalid.host.fqdn", "BAD TOKEN", null, ENVIRONMENT);
    }

    @Test(expected= CertificateException.class)
    public void testGarbageCaCert() throws Exception {
        deploy.deployCode("invalid.host.fqdn", "BAD TOKEN", "this is not a valid cert", ENVIRONMENT);
    }
}
