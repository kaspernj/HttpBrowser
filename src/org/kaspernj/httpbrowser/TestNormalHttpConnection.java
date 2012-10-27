package org.kaspernj.httpbrowser;

import static org.junit.Assert.*;

import java.util.HashMap;

import org.junit.Test;

public class TestNormalHttpConnection {

	@Test
	public void test() {
		try{
			HashMap<String, String> args = new HashMap<String, String>();
			args.put("host", "partyworm.dk");
			args.put("port", "80");
			args.put("debug", "1");
			
			System.out.println("Spawning object.");
			HttpBrowser http = new HttpBrowser();
			http.setHost("partyworm.dk");
			http.setPort(80);
			http.setDebug(false);
			http.setEncodingGZIP(true);
			
			System.out.println("Connecting.");
			http.connect();
			
			System.out.println("Requesting page.");
			HttpBrowserResult res = http.get("multipart_test.php");
			
			System.out.println("Validating result.");
			System.out.println("Got body: " + res.getBody());
			
			if (res.getBody().trim().isEmpty()){
				throw new Exception("The body was empty: " + res.getBody());
			}else if(!res.getBody().equals("{\"get\":[],\"post\":[],\"files\":[],\"files_data\":[]}")){
				throw new Exception("Unexpected result: " + res.getBody());
			}
			
			HttpBrowserResult res2 = http.get("");
			System.out.println("Got second body: " + res2.getBody());
			
			//FIXME Code some validation.
		}catch(Exception e){
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
