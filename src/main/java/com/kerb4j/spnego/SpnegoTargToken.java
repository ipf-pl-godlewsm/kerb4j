package com.kerb4j.spnego;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Enumeration;

import com.kerb4j.Kerb4JException;
import org.bouncycastle.asn1.*;

public class SpnegoTargToken extends SpnegoToken {

    public static final int UNSPECIFIED_RESULT = -1;
    public static final int ACCEPT_COMPLETED = 0;
    public static final int ACCEPT_INCOMPLETE = 1;
    public static final int REJECTED = 2;

    private int result = UNSPECIFIED_RESULT;

    public SpnegoTargToken(byte[] token) throws Kerb4JException {
        ASN1InputStream stream = new ASN1InputStream(new ByteArrayInputStream(token));
        ASN1TaggedObject tagged;
        try {
            tagged = DecodingUtil.as(ASN1TaggedObject.class, stream);
        } catch(IOException e) {
            throw new Kerb4JException("spnego.token.malformed", null, e);
        }

        ASN1Sequence sequence = ASN1Sequence.getInstance(tagged, true);
        Enumeration<?> fields = sequence.getObjects();
        while(fields.hasMoreElements()) {
            tagged = DecodingUtil.as(ASN1TaggedObject.class, fields);
            switch (tagged.getTagNo()) {
            case 0:
                DEREnumerated enumerated = (DEREnumerated) ASN1Enumerated.getInstance(tagged, true);
                result = enumerated.getValue().intValue();
                break;
            case 1:
                ASN1ObjectIdentifier mechanismOid = ASN1ObjectIdentifier.getInstance(tagged, true);
                mechanism = mechanismOid.getId();
                break;
            case 2:
                ASN1OctetString mechanismTokenString = ASN1OctetString.getInstance(tagged, true);
                mechanismToken = mechanismTokenString.getOctets();
                break;
            case 3:
                ASN1OctetString mechanismListString = ASN1OctetString.getInstance(tagged, true);
                mechanismList = mechanismListString.getOctets();
                break;
            default:
                Object[] args = new Object[]{tagged.getTagNo()};
                throw new Kerb4JException("spnego.field.invalid", args, null);
            }
        }
    }

    public int getResult() {
        return result;
    }

}