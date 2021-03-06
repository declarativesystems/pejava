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

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;

public interface Deploy {
    String deployCode(String puppetMasterFqdn, String token, String caCert, String[] environment) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, CertificateException;
    String deployCode(String puppetMasterFqdn, String token, String caCert, String[] environment, boolean wait) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, CertificateException;
}
