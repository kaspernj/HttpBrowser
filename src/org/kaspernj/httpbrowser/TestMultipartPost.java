package org.kaspernj.httpbrowser;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

public class TestMultipartPost {

	@Test
	public void test() {
		try{
			System.out.println("Spawning object.");
			HttpBrowser http = new HttpBrowser();
			http.setHost("partyworm.dk");
			http.setPort(80);
			http.setDebug(false);
			http.setEncodingGZIP(true);
			
			System.out.println("Connecting.");
			http.connect();
			
			System.out.println("Generating request.");
			
			HttpBrowserRequestPostMultipart req = http.postMultipart();
			req.setAddress("multipart_test.php");
			req.addPost("TestArgument1", "This is a test");
			
			File curFile = new File(".");
			System.out.println("Cur path: " + curFile.getCanonicalPath());
			
			HttpBrowserRequestPostMultipartFileUpload fu = req.addFileUpload();
			fu.setPostName("some_file");
			fu.setFilePath(curFile.getCanonicalPath() + "/src/org/kaspernj/httpbrowser/TestMultipartPostExampleUploadFile.txt");
			fu.setContextType("text/plain");
			
			HttpBrowserResult res = req.execute();
			if (!res.getBody().contains("{\"TestArgument1\":\"This is a test\"}")){
				throw new Exception("Unexpected body: " + res.getBody());
			}else if(!res.getBody().contains(",\"files\":{\"some_file\":{\"name\":\"TestMultipartPostExampleUploadFile.txt\"")){
				throw new Exception("Unexpected body: " + res.getBody());
			}
			
			System.out.println("Body: " + res.getBody());
		}catch(Exception e){
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
