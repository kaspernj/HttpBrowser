package org.kaspernj.httpbrowser;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import org.junit.Test;

public class TestNormalHttpConnection {

	@Test
	public void test() {
		try{
			testGetRequests();
			testPostRequest();
			testKeepAliveTimeout();
			testMultipleForKeepAlive();
			testThreadSafety();
		}catch(Exception e){
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	public void testGetRequests() throws Exception{
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
	}
	
	public void testPostRequest() throws Exception{
		System.out.println("Spawning object.");
		HttpBrowser http = new HttpBrowser();
		http.setHost("partyworm.dk");
		http.setPort(80);
		http.setDebug(false);
		http.setEncodingGZIP(true);
		http.connect();
		
		System.out.println("Testing post-requests.");
		HashMap<String, String> postData = new HashMap<String, String>();
		postData.put("test argument 1", "test wee 1");
		postData.put("test argument 2", "test wee 2");
		
		HttpBrowserResult res3 = http.post("multipart_test.php", postData);
		String expectedStr = "{\"get\":[],\"post\":{\"test_argument_1\":\"test wee 1\",\"test_argument_2\":\"test wee 2\"},\"files\":[],\"files_data\":[]}";
		
		if (!res3.getBody().equals(expectedStr)){
			throw new Exception("Unexpected content when doing a post-request:\n" + res3.getBody() + "\n" + expectedStr);
		}
	}
	
	public void testMultipleForKeepAlive() throws Exception{
		HttpBrowser http = new HttpBrowser();
		http.setHost("partyworm.dk");
		http.setPort(80);
		http.setDebug(true);
		http.setEncodingGZIP(true);
		http.connect();
		
		HttpBrowserResult resFirst = http.get("robots.txt");
		int amountOfRequests = resFirst.keepAliveMax + 2;
		
		for(int i = 1; i <= amountOfRequests; i++){
			System.out.println("Executing request " + i);
			HttpBrowserResult res = http.get("robots.txt");
		}
	}
	
	public void testKeepAliveTimeout() throws Exception{
		HttpBrowser http = new HttpBrowser();
		http.setHost("partyworm.dk");
		http.setPort(80);
		http.setDebug(true);
		http.setEncodingGZIP(true);
		http.connect();
		
		HttpBrowserResult res = http.get("robots.txt");
		int keepAliveTimeout = res.keepAliveTimeout;
		
		System.out.println("Sleeping for " + (keepAliveTimeout + 1) + " seconds to test keep-alive-timeout.");
		Thread.sleep((keepAliveTimeout + 1) * 1000);
		
		HttpBrowserResult res2 = http.get("robots.txt");
	}
	
	public void testThreadSafety() throws Exception{
		System.out.println("Testing thread safety.");
		
		ArrayList<Thread> threads = new ArrayList<Thread>();
		
		final HttpBrowser http = new HttpBrowser();
		http.setHost("partyworm.dk");
		http.setPort(80);
		http.setDebug(false);
		http.setEncodingGZIP(true);
		http.connect();
		
		for(int i = 0; i < 10; i++){
			Thread thread = new Thread(){
				public void run(){
					try{
						HttpBrowserResult res = http.get("robots.txt");
					}catch(Exception e){
						System.err.println("Error in thread-safety: " + e.getMessage());
						e.printStackTrace();
					}
				}
			};
			
			threads.add(thread);
			thread.start();
		}
		
		int count = 0;
		for(Thread thread: threads){
			count += 1;
			
			System.out.println("Joining thread " + count);
			thread.join();
		}
	}
}
