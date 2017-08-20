/**
 * Copyright (C) 2009 "Darwin V. Felix" <darwinfelix@users.sourceforge.net>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package com.kerb4j.common.util;

import com.kerb4j.client.SpnegoClient;
import com.kerb4j.common.marshall.spnego.SpnegoConstants;
import org.ietf.jgss.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import java.net.URL;
import java.util.Arrays;

/**
 * This is a Utility Class that can be used for finer grained control 
 * over message integrity, confidentiality and mutual authentication.
 * 
 * <p>
 * This Class is exposed for developers who want to implement a custom 
 * HTTP client.
 * </p>
 * 
 * <p>
 * Take a look at the {@link SpnegoClient} class before
 * attempting to implement your own HTTP client.
 * </p>
 * 
 * <p>For more example usage, see the documentation at 
 * <a href="http://spnego.sourceforge.net" target="_blank">http://spnego.sourceforge.net</a>
 * </p>
 * 
 * @author Darwin V. Felix
 * 
 */
public final class SpnegoProvider {

    /** Default LOGGER. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SpnegoProvider.class);

    /** Factory for GSS-API mechanism. */
    public static final GSSManager GSS_MANAGER = GSSManager.getInstance();

    /** GSS-API mechanism "1.3.6.1.5.5.2". */
    public static final Oid SPNEGO_OID = SpnegoProvider.getSpnegoOid();
	/** GSS-API mechanism "1.2.840.113554.1.2.2". */
    public static final Oid KERBEROS_V5_OID = SpnegoProvider.getKerberosV5Oid();
	/**
	 * Note: The MIT Kerberos V5 mechanism OID is added for compatibility with
	 *		 Chromium-based browsers on POSIX OSes. On these OSes, Chromium erroneously
	 *		 responds to an SPNEGO request with a GSS-API MIT Kerberos V5 mechanism
	 *		 answer (instead of a MIT Kerberos V5 token inside an SPNEGO mechanism answer).
	 */
    public static final Oid[] SUPPORTED_OIDS = new Oid[]{SPNEGO_OID, KERBEROS_V5_OID};

    /*
     * This is a utility class (not a Singleton).
     */
    private SpnegoProvider() {
        // default private
    }

    /**
     * Returns the {@link SpnegoAuthScheme} or null if header is missing.
     * 
     * <p>
     * Throws UnsupportedOperationException if header is NOT Negotiate 
     * or Basic. 
     * </p>
     * 
     * @param header ex. Negotiate or Basic
     * @return null if header missing/null else the auth scheme
     */
    public static SpnegoAuthScheme getAuthScheme(final String header) {

        if (null == header || header.isEmpty()) {
            LOGGER.trace("authorization header was missing/null");
            return null;
            
        } else if (header.startsWith(Constants.NEGOTIATE_HEADER)) {
            final String token = header.substring(Constants.NEGOTIATE_HEADER.length() + 1);
            return new SpnegoAuthScheme(Constants.NEGOTIATE_HEADER, token);
            
        } else if (header.startsWith(Constants.BASIC_HEADER)) {
            final String token = header.substring(Constants.BASIC_HEADER.length() + 1);
            return new SpnegoAuthScheme(Constants.BASIC_HEADER, token);
            
        } else {
            throw new UnsupportedOperationException("Negotiate or Basic Only:" + header);
        }
    }

    /**
     * Returns the Universal Object Identifier representation of 
     * the SPNEGO mechanism.
     * 
     * @return Object Identifier of the GSS-API mechanism
     */
    private static Oid getSpnegoOid() {
        Oid oid = null;
        try {
            oid = new Oid(SpnegoConstants.SPNEGO_OID);
        } catch (GSSException gsse) {
            LOGGER.error("Unable to create OID " + SpnegoConstants.SPNEGO_OID + " !", gsse);
        }
        return oid;
    }

    /**
     * Returns the Universal Object Identifier representation of
     * the MIT Kerberos V5 mechanism.
	 *
     * @return Object Identifier of the GSS-API mechanism
     */
    private static Oid getKerberosV5Oid() {
        Oid oid = null;
        try {
            oid = new Oid(SpnegoConstants.KERBEROS_MECHANISM);
        } catch (GSSException gsse) {
            LOGGER.error("Unable to create OID " + SpnegoConstants.KERBEROS_MECHANISM + " !", gsse);
        }
        return oid;
    }

    /**
     * Returns the {@link GSSName} constructed out of the passed-in 
     * URL object.
     * 
     * @param url HTTP address of server
     * @return GSSName of URL.
     */
    public static GSSName getServerName(final URL url) throws GSSException {
        return GSS_MANAGER.createName("HTTP@" + url.getHost(),
            GSSName.NT_HOSTBASED_SERVICE, SpnegoProvider.SPNEGO_OID);
    }

    /**
     * Used by the BASIC Auth mechanism for establishing a LoginContext 
     * to authenticate a client/caller/request.
     * 
     * @param username client username
     * @param password client password
     * @return CallbackHandler to be used for establishing a LoginContext
     */
    public static CallbackHandler getUsernameAndPasswordHandler(String username, String password) {

        LOGGER.trace("username=" + username + "; password=" + password.hashCode());

        return callbacks -> Arrays.stream(callbacks).forEach(callback -> {
            if (callback instanceof NameCallback) {
                final NameCallback nameCallback = (NameCallback) callback;
                nameCallback.setName(username);
            } else if (callback instanceof PasswordCallback) {
                final PasswordCallback passCallback = (PasswordCallback) callback;
                passCallback.setPassword(password.toCharArray());
            } else {
                LOGGER.warn("Unsupported Callback class=" + callback.getClass().getName());
            }
        });

    }

}
