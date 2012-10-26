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
			HttpBrowser http = new HttpBrowser(args);
			
			System.out.println("Connecting.");
			http.connect();
			
			System.out.println("Requesting page.");
			HttpBrowserResult res = http.get("");
			
			System.out.println("Validating result.");
			System.out.println("Got body: " + res.getBody());
			
			if (res.getBody().trim().isEmpty()){
				throw new Exception("The body was empty: " + res.getBody());
			}
			
			//FIXME Code some validation.
		}catch(Exception e){
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			
			fail(e.getMessage());
		}
	}

}
