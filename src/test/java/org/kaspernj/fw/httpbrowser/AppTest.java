package org.kaspernj.fw.httpbrowser;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase
{
  /**
   * Create the test case
   *
   * @param testName name of the test case
   */
  public AppTest( String testName )
  {
      super( testName );
  }

  /**
   * @return the suite of tests being tested
   */
  public static Test suite()
  {
      return new TestSuite( AppTest.class );
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
		http.setDebug(false);
		http.setEncodingGZIP(true);
		http.setDebug( false );
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
		http.setDebug(false);
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
	
	public void testCookies() throws Exception{
		HttpBrowser http = new HttpBrowser();
		http.setHost("www.google.com");
		http.setDebug(true);
		
		HttpBrowserResult res = http.get("");
		HttpBrowserCookie cookie = http.getCookieByName("NID");
		String cookieValue = cookie.getValue();
	}
	
	public void testPostMultipart() throws Exception{
		System.out.println("Spawning object.");
		HttpBrowser http = new HttpBrowser();
		http.setHost("partyworm.dk");
		http.setPort(80);
		http.setDebug(false);
		http.setEncodingGZIP(true);
		
		System.out.println("Connecting.");
		http.connect();
		
		System.out.println("Generating request.");
		File curFile = new File(".");
		
		
		
		
		HttpBrowserRequestPostMultipart req = http.postMultipart();
		req.setAddress("multipart_test.php");
		req.addPost("TestArgument1", "This is a test");
		
		System.out.println("Cur path: " + curFile.getCanonicalPath());
		
		HttpBrowserRequestPostMultipartFileUpload fu = req.addFileUpload();
		fu.setPostName("some_file");
		fu.setFilePath(curFile.getCanonicalPath() + "/src/test/java/org/kaspernj/fw/httpbrowser/TestMultipartPostExampleUploadFile.txt");
		fu.setContextType("text/plain");
		
		HttpBrowserResult res = req.execute();
		if (!res.getBody().contains("{\"TestArgument1\":\"This is a test\"}")){
			throw new Exception("Unexpected body: " + res.getBody());
		}else if(!res.getBody().contains(",\"files\":{\"some_file\":{\"name\":\"TestMultipartPostExampleUploadFile.txt\"")){
			throw new Exception("Unexpected body: " + res.getBody());
		}
		
		System.out.println("Body: " + res.getBody());
		
		
		http.setDebug(false);
		
		HttpBrowserRequestPostMultipart reqJPEG = http.postMultipart();
		reqJPEG.setAddress("multipart_test.php?choice=file-test");
		reqJPEG.addPost("TestArgument1", "This is a test");
		
		HttpBrowserRequestPostMultipartFileUpload fuJPEG = reqJPEG.addFileUpload();
		fuJPEG.setPostName("file");
		fuJPEG.setFilePath(curFile.getCanonicalPath() + "/src/test/java/org/kaspernj/fw/httpbrowser/TestMultipartPostExampleUploadFile.jpeg");
		fuJPEG.setContextType("text/plain");
		
		HttpBrowserResult resJPEG = reqJPEG.execute();
		byte[] bodyBytes = resJPEG.getBodyAsByteArray();
		byte[] fileBytes = Files.readAllBytes(Paths.get(curFile.getCanonicalPath() + "/src/test/java/org/kaspernj/fw/httpbrowser/TestMultipartPostExampleUploadFile.jpeg"));
		
		if (bodyBytes.length != fileBytes.length){
			throw new Exception("The two byte-arrays did not have the same length: " + bodyBytes.length + ", " + fileBytes.length + "\n\n'" + (new String(bodyBytes)) + "'\n\n'" + (new String(fileBytes)) + "'.");
		}else if(!Arrays.equals(bodyBytes, fileBytes)){
			throw new Exception("Expected body byte array to be exactly the same as the original file byte array but it wasnt.");
		}
	}
}
