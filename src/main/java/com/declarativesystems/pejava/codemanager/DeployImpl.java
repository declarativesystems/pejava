/*
 * Copyright 2017 Declarative Systems PTY LTD
 * Copyright 2016 Puppet Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.declarativesystems.pejava.codemanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Named;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * Support for the Code Manager `deploys` Web Service:
 * /code-manager/v1/deploys
 */
@Named("deploy")
public class DeployImpl implements Deploy {
    private static Logger log = LoggerFactory.getLogger(DeployImpl.class);

    // 10 seconds
    private static final int CONNECT_TIMEOUT = 10 * 1000;

    // 10 minutes
    private static final int SOCKET_TIMEOUT = 600 * 1000;

    /**
     * Deploy code without waiting for result (we get back `queued` on success)
     * but this can fail mid-deploy on the puppet master
     * @param puppetMasterFqdn FQDN of Puppet Master
     * @param token contents of RBAC token
     * @param caCert contents of CA Cert (PEM)
     * @param environments Environments to deploy. An empty or null list means
     *                     deploy all environments, otherwise just deploy those
     *                     named only
     * @return JSON string from Puppet Enterprise Code Manager REST API
     */
    @Override
    public String deployCode(String puppetMasterFqdn,
                             String token,
                             String caCert,
                             String[] environments) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, CertificateException {
        return deployCode(puppetMasterFqdn, token, caCert, environments, false);
    }

    /**
     * Deploy puppet code and optionally wait for a result
     * @param puppetMasterFqdn FQDN of Puppet Master
     * @param token contents of RBAC token
     * @param caCert contents of CA Cert (PEM)
     * @param environments Environments to deploy. An empty or null list means
     *                     deploy all environments, otherwise just deploy those
     *                     named only
     * @param wait Wait for deployment to finish. Required if you want the real
     *             deployment status message from Puppet.
     * @return JSON string from Puppet Enterprise Code Manager REST API
     */
    @Override
    public String deployCode(String puppetMasterFqdn,
                             String token,
                             String caCert,
                             String[] environments,
                             boolean wait) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, CertificateException
    {
        /* Example curl request:
        curl -k -X POST -H 'Content-Type: application/json' \
        -H "X-Authentication: `cat ~/.puppetlabs/token`" \
        "https://${CODE_MANAGER}:8170/code-manager/v1/deploys" \
        -d '{"environments": ["production"], "wait": true}'
        */

        log.info("Starting puppet code deployment to: " + puppetMasterFqdn);
        HttpClient httpClient;
        String url = "https://" + puppetMasterFqdn + ":8170/code-manager/v1/deploys";

        // JSON payload
        Map<String, Object> payloadData = new HashMap<>();

        // deploy selected environments or all environments if none specified
        if (environments != null && environments.length > 0)
        {
            payloadData.put("environments", environments);
        }
        else
        {
            payloadData.put("deploy-all", true);
        }

        if (wait)
        {
            payloadData.put("wait", true);
        }

        Gson gson = new GsonBuilder().create();

        // build and send the REST request
        if (caCert == null || caCert.isEmpty())
        {
            httpClient = insecureSsl(); //HttpClientBuilder.create().build();
        }
        else
        {
            httpClient = secureSsl(caCert);
        }

        HttpPost request = new HttpPost(url);
        request.addHeader("accept", "application/json");
        request.addHeader("X-Authentication", token);
        String json = gson.toJson(payloadData);
        StringEntity requestEntity = new StringEntity(
                json,
                ContentType.APPLICATION_JSON);
        request.setEntity(requestEntity);

        log.debug("JSON payload: " + json);
        HttpResponse response = httpClient.execute(request);
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");
        log.info("Deployment result: " + responseString);

        // puppet deployments resulting in errors come back as 200OK but with the JSON key 'kind'
        // therefore, if we see 'kind' we encountered an error and should log it
        if (responseString.contains("\"kind\""))
        {
            log.error("Deployment error from Puppet Master: " + responseString);
        }
        return responseString;
    }

    private RequestConfig getRequestConfig()
    {
        // 10 seconds should be PLENTY to CONNECT a request - beyond this, suspect firewall or broken servers
        RequestConfig.Builder rcb = RequestConfig.custom();
        rcb.setConnectTimeout(CONNECT_TIMEOUT);

        // The socket timeout is how long to wait for the request to be processed... since puppet
        // deploys during flight this can take a LONG time (eg slow forge, slow git etc)...
        rcb.setSocketTimeout(SOCKET_TIMEOUT);

        return rcb.build();
    }

    /**
     * http://stackoverflow.com/questions/18513792/using-sslcontext-with-just-a-ca-certificate-and-no-keystore
     */
    private HttpClient secureSsl(String caCert) throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, KeyManagementException
    {
        log.debug("SSL mode, Processing cert" + caCert);
        InputStream is = new ByteArrayInputStream(caCert.getBytes());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCertX509 = (X509Certificate) cf.generateCertificate(is);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null); // You don't need the KeyStore instance to come from a file.
        ks.setCertificateEntry("caCert", caCertX509);

        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return HttpClients.custom()
                .setSSLContext(sslContext)
                .setDefaultRequestConfig(getRequestConfig())
                .build();
    }

    private HttpClient insecureSsl() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException
    {
        log.debug("insecure mode, no SSL certificate");
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                builder.build());
        return HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .setDefaultRequestConfig(getRequestConfig())
                .build();
    }


}
