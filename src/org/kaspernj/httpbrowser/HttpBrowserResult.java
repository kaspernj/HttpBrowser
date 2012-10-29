package org.kaspernj.httpbrowser;

import java.util.HashMap;

//This class contains various data for a HTTP-result.
public class HttpBrowserResult {
	private byte[] bodyByteArray;
	private HashMap<String, String> headers;
	
	//Sets the body-byte-array.
	public void setBodyByteArray(byte[] inBody){
		bodyByteArray = inBody;
	}
	
	//Returns the body of the result as a string.
	public String getBody(){
		return new String(bodyByteArray);
	}
	
	//Returns the body of the result as a byte-array.
	public byte[] getBodyAsByteArray(){
		return bodyByteArray;
	}
	
	//Sets the headers for the result.
	public void setHeaders(HashMap<String, String> inHeaders){
		headers = inHeaders;
	}
	
	//Returns the HashMap containing the headers of the result.
	public HashMap<String, String> getHeaders(){
		return headers;
	}
}
