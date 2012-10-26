package org.kaspernj.httpbrowser;

import java.util.HashMap;

public class HttpBrowserResult {
	private String body;
	private HashMap<String, String> headers;
	
	public void setBody(String inBody){
		body = inBody;
	}
	
	public String getBody(){
		return body;
	}
	
	public void setHeaders(HashMap<String, String> inHeaders){
		headers = inHeaders;
	}
	
	public HashMap<String, String> getHeaders(){
		return headers;
	}
}
