package com.onelogin.saml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.codec.binary.Base64;

import com.onelogin.AccountSettings;
import com.onelogin.AppSettings;

public class AuthRequest {
	
	private String id;
	private String issueInstant;
	private AppSettings appSettings;
	private AccountSettings accountSettings;
	private static final String utf8 = "UTF-8";
	
	public AuthRequest(AppSettings appSettings, AccountSettings accountSettings){		
		this.appSettings = appSettings;
		this.accountSettings = accountSettings;
		id="_"+UUID.randomUUID().toString();		
		SimpleDateFormat simpleDf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		issueInstant = simpleDf.format(new Date());		
	}
	

	
	public String getRequest() throws XMLStreamException, IOException, InvalidKeyException, GeneralSecurityException{
		return getRequest(false, "");
	}
	
	//Returns the full URL where you should redirect to
	public String getRequest(boolean includeRequestedAuthnContext, String key) throws XMLStreamException, IOException, InvalidKeyException, GeneralSecurityException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();		
		XMLOutputFactory factory = XMLOutputFactory.newInstance();
		XMLStreamWriter writer = factory.createXMLStreamWriter(baos);
					
		writer.writeStartElement("samlp", "AuthnRequest", "urn:oasis:names:tc:SAML:2.0:protocol");
		writer.writeNamespace("samlp","urn:oasis:names:tc:SAML:2.0:protocol");
		
		writer.writeAttribute("ID", id);
		writer.writeAttribute("Version", "2.0");
		writer.writeAttribute("IssueInstant", this.issueInstant + "Z");
		writer.writeAttribute("ProtocolBinding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
		writer.writeAttribute("AssertionConsumerServiceURL", this.appSettings.getAssertionConsumerServiceUrl());
		
		writer.writeStartElement("saml","Issuer","urn:oasis:names:tc:SAML:2.0:assertion");
		writer.writeNamespace("saml","urn:oasis:names:tc:SAML:2.0:assertion");
		writer.writeCharacters(this.appSettings.getIssuer());
		writer.writeEndElement();
		
		writer.writeStartElement("samlp", "NameIDPolicy", "urn:oasis:names:tc:SAML:2.0:protocol");
		
		writer.writeAttribute("Format", this.appSettings.getNamedIdFormat());
		writer.writeAttribute("AllowCreate", "true");
		writer.writeEndElement();
		
		if(includeRequestedAuthnContext){
			
			writer.writeStartElement("samlp","RequestedAuthnContext","urn:oasis:names:tc:SAML:2.0:protocol");
			
			writer.writeAttribute("Comparison", "exact");
			writer.writeEndElement();
			
			writer.writeStartElement("saml","AuthnContextClassRef","urn:oasis:names:tc:SAML:2.0:assertion");
			writer.writeNamespace("saml", "urn:oasis:names:tc:SAML:2.0:assertion");
			writer.writeCharacters("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");
			writer.writeEndElement();

		}
		
		writer.writeEndElement();
		
		writer.flush();		
	
		// Compress the bytes		
		ByteArrayOutputStream deflatedBytes = new ByteArrayOutputStream();
		Deflater deflater = new Deflater(Deflater.DEFLATED, true);
		DeflaterOutputStream deflaterStream = new DeflaterOutputStream(deflatedBytes, deflater);
		deflaterStream.write(baos.toByteArray());
		deflaterStream.finish();
		
		// Base64 Encode the bytes
		byte[] encoded = Base64.encodeBase64Chunked(deflatedBytes.toByteArray());		
		
		// URL Encode the bytes
		String encodedRequest = URLEncoder.encode(new String(encoded, Charset.forName(utf8)), utf8);
		String finalSignatureValue = "";
		
		//If a key was provided, sign it!
		if(key.length() > 0){
			String encodedSigAlg = URLEncoder.encode("http://www.w3.org/2000/09/xmldsig#rsa-sha1", utf8);
			
			Signature signature = Signature.getInstance("SHA1withRSA");
			String strSignature = "SAMLRequest=" + getRidOfCRLF(encodedRequest) + "&SigAlg=" + encodedSigAlg;
			signature.initSign( loadPrivateKey( key ) );
			signature.update( strSignature.getBytes(utf8) );
			String encodedSignature = URLEncoder.encode( Base64.encodeBase64String( signature.sign() ) , utf8);
			
			finalSignatureValue = "&SigAlg=" + encodedSigAlg + "&Signature=" + encodedSignature;
		}
		
		return accountSettings.getIdp_sso_target_url()+"?SAMLRequest=" + getRidOfCRLF(encodedRequest) + finalSignatureValue;
	}
	
	private static PrivateKey loadPrivateKey(String key64) throws GeneralSecurityException {
	    byte[] clear = Base64.decodeBase64(key64);
	    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(clear);
	    KeyFactory fact = KeyFactory.getInstance("RSA");
	    PrivateKey priv = fact.generatePrivate(keySpec);
	    Arrays.fill(clear, (byte) 0);
	    return priv;
	}
	
	private static String getRidOfCRLF(String what) {
		String lf = "%0D";
		String cr = "%0A";
		String now = lf;

		int index = what.indexOf(now);
		StringBuffer r = new StringBuffer();

		while (index!=-1) {
			r.append(what.substring(0,index));
			what = what.substring(index+3,what.length());
			
			if (now.equals(lf)) {
				now = cr;
			} else {
				now = lf;
			}
			
			index = what.indexOf(now);
		}
		return r.toString();
	}		

}
