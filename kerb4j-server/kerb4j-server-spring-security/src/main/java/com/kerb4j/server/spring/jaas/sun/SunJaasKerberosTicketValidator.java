/*
 * Copyright 2009-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kerb4j.server.spring.jaas.sun;

import com.kerb4j.client.SpnegoClient;
import com.kerb4j.client.SpnegoContext;
import com.kerb4j.server.spring.KerberosTicketValidator;
import com.kerb4j.server.spring.SpnegoAuthenticationToken;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.Assert;

import java.io.IOException;
import java.security.PrivilegedActionException;

/**
 * Implementation of {@link KerberosTicketValidator} which uses the SUN JAAS
 * login module, which is included in the SUN JRE, it will not work with an IBM JRE.
 * The whole configuration is done in this class, no additional JAAS configuration
 * is needed.
 *
 * @author Mike Wiesner
 * @author Jeremy Stone
 * @since 1.0
 */
public class SunJaasKerberosTicketValidator implements KerberosTicketValidator, InitializingBean {

    private static final Log LOG = LogFactory.getLog(SunJaasKerberosTicketValidator.class);

    private String servicePrincipal;
    private Resource keyTabLocation;

    private SpnegoClient spnegoClient;

    private boolean holdOnToGSSContext;

    @Override
    public SpnegoAuthenticationToken validateTicket(byte[] token) {

        token = tweakJdkRegression(token);

        try {
            SpnegoContext acceptContext = spnegoClient.createAcceptContext();
            byte[] responseToken = acceptContext.acceptToken(token);
            GSSName srcName = acceptContext.getSrcName();

            if (null == srcName) {
                throw new BadCredentialsException("Kerberos validation not successful");
            }

            if (!holdOnToGSSContext) {
                acceptContext.close();
            }

            return new SpnegoAuthenticationToken(token, srcName.toString(), responseToken);

        } catch (IOException | GSSException | PrivilegedActionException e) {
            throw new BadCredentialsException("Kerberos validation not successful", e);
        }

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(this.servicePrincipal, "servicePrincipal must be specified");
        Assert.notNull(this.keyTabLocation, "keyTab must be specified");
        if (keyTabLocation instanceof ClassPathResource) {
            LOG.warn("Your keytab is in the classpath. This file needs special protection and shouldn't be in the classpath. JAAS may also not be able to load this file from classpath.");
        }
        String keyTabLocationAsString = this.keyTabLocation.getURL().toExternalForm();
        // We need to remove the file prefix (if there is one), as it is not supported in Java 7 anymore.
        // As Java 6 accepts it with and without the prefix, we don't need to check for Java 7
        if (keyTabLocationAsString.startsWith("file:"))
        {
            keyTabLocationAsString = keyTabLocationAsString.substring(5);
        }

        spnegoClient = SpnegoClient.loginWithKeyTab(servicePrincipal, keyTabLocationAsString);
    }

    /**
     * The service principal of the application.
     * For web apps this is <code>HTTP/full-qualified-domain-name@DOMAIN</code>.
     * todo: add warning on UPN
     * The keytab must contain the key for this principal.
     *
     * @param servicePrincipal service principal to use
     * @see #setKeyTabLocation(Resource)
     */
    public void setServicePrincipal(String servicePrincipal) {
        this.servicePrincipal = servicePrincipal;
    }

    /**
     * <p>The location of the keytab. You can use the normale Spring Resource
     * prefixes like <code>file:</code> or <code>classpath:</code>, but as the
     * file is later on read by JAAS, we cannot guarantee that <code>classpath</code>
     * works in every environment, esp. not in Java EE application servers. You
     * should use <code>file:</code> there.
     *
     * This file also needs special protection, which is another reason to
     * not include it in the classpath but rather use <code>file:/etc/http.keytab</code>
     * for example.
     *
     * @param keyTabLocation The location where the keytab resides
     */
    public void setKeyTabLocation(Resource keyTabLocation) {
        this.keyTabLocation = keyTabLocation;
    }

    /**
     * Determines whether to hold on to the {@link GSSContext GSS security context} or
     * otherwise {@link GSSContext#dispose() dispose} of it immediately (the default behaviour).
     * <p>Holding on to the GSS context allows decrypt and encrypt operations for subsequent
     * interactions with the principal.
     *
     * @param holdOnToGSSContext true if should hold on to context
     */
    public void setHoldOnToGSSContext(boolean holdOnToGSSContext) {
        this.holdOnToGSSContext = holdOnToGSSContext;
    }

    private static byte[] tweakJdkRegression(byte[] token) {

//    	Due to regression in 8u40/8u45 described in
//    	https://bugs.openjdk.java.net/browse/JDK-8078439
//    	try to tweak token package if it looks like it has
//    	OID's in wrong order
//
//      0000: 60 82 06 5C 06 06 2B 06   01 05 05 02 A0 82 06 50
//      0010: 30 82 06 4C A0 30 30 2E  |06 09 2A 86 48 82 F7 12
//      0020: 01 02 02|06 09 2A 86 48   86 F7 12 01 02 02 06|0A
//      0030: 2B 06 01 04 01 82 37 02   02 1E 06 0A 2B 06 01 04
//      0040: 01 82 37 02 02 0A A2 82   06 16 04 82 06 12 60 82
//
//    	In above package first token is in position 24 and second
//    	in 35 with both having size 11.
//
//    	We simple check if we have these two in this order and swap
//
//    	Below code would create two arrays, lets just create that
//    	manually because it doesn't change
//      Oid GSS_KRB5_MECH_OID = new Oid("1.2.840.113554.1.2.2");
//      Oid MS_KRB5_MECH_OID = new Oid("1.2.840.48018.1.2.2");
//		byte[] der1 = GSS_KRB5_MECH_OID.getDER();
//		byte[] der2 = MS_KRB5_MECH_OID.getDER();

//		0000: 06 09 2A 86 48 86 F7 12   01 02 02
//		0000: 06 09 2A 86 48 82 F7 12   01 02 02

        if (token == null || token.length < 48) {
            return token;
        }

        int[] toCheck = new int[] { 0x06, 0x09, 0x2A, 0x86, 0x48, 0x82, 0xF7, 0x12, 0x01, 0x02, 0x02, 0x06, 0x09, 0x2A,
                0x86, 0x48, 0x86, 0xF7, 0x12, 0x01, 0x02, 0x02 };

        for (int i = 0; i < 22; i++) {
            if ((byte) toCheck[i] != token[i + 24]) {
                return token;
            }
        }

        byte[] nt = new byte[token.length];
        System.arraycopy(token, 0, nt, 0, 24);
        System.arraycopy(token, 35, nt, 24, 11);
        System.arraycopy(token, 24, nt, 35, 11);
        System.arraycopy(token, 46, nt, 46, token.length - 24 - 11 - 11);
        return nt;
    }

}