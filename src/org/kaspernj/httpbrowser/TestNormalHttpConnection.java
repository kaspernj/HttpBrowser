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
			
			if (res.getBody().trim().isEmpty()){
				throw new Exception("The body was empty: " + res.getBody());
			}else if(!res.getBody().equals("{\"get\":[],\"post\":[],\"files\":[],\"files_data\":[]}")){
				throw new Exception("Unexpected result: " + res.getBody());
			}
			
			//Test a large request.
			System.out.println("Getting a large page.");
			
			HttpBrowserResult res2 = http.get("");
			String body = res2.getBody();
			if (!body.contains("<html>") || !body.contains("</html>")){
				throw new Exception("Body for request didnt contain HTML tags.");
			}
			
			
			//Test post-request.
			System.out.println("Testing post-requests.");
			HashMap<String, String> postData = new HashMap<String, String>();
			postData.put("test argument 1", "test wee 1");
			postData.put("test argument 2", "test wee 2");
			
			HttpBrowserResult res3 = http.post("multipart_test.php", postData);
			String expectedStr = "{\"get\":[],\"post\":{\"test_argument_1\":\"test wee 1\",\"test_argument_2\":\"test wee 2\"},\"files\":[],\"files_data\":[]}";
			
			if (!res3.getBody().equals(expectedStr)){
				throw new Exception("Unexpected content when doing a post-request:\n" + res3.getBody() + "\n" + expectedStr);
			}
		}catch(Exception e){
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
