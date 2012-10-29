package org.kaspernj.httpbrowser;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
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
			File curFile = new File(".");
			
			
			
			
			HttpBrowserRequestPostMultipart req = http.postMultipart();
			req.setAddress("multipart_test.php");
			req.addPost("TestArgument1", "This is a test");
			
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
			
			
			http.setDebug(false);
			
			HttpBrowserRequestPostMultipart reqJPEG = http.postMultipart();
			reqJPEG.setAddress("multipart_test.php?choice=file-test");
			reqJPEG.addPost("TestArgument1", "This is a test");
			
			HttpBrowserRequestPostMultipartFileUpload fuJPEG = reqJPEG.addFileUpload();
			fuJPEG.setPostName("file");
			fuJPEG.setFilePath(curFile.getCanonicalPath() + "/src/org/kaspernj/httpbrowser/TestMultipartPostExampleUploadFile.jpeg");
			fuJPEG.setContextType("text/plain");
			
			HttpBrowserResult resJPEG = reqJPEG.execute();
			byte[] bodyBytes = resJPEG.getBodyAsByteArray();
			byte[] fileBytes = Files.readAllBytes(Paths.get(curFile.getCanonicalPath() + "/src/org/kaspernj/httpbrowser/TestMultipartPostExampleUploadFile.jpeg"));
			
			if (bodyBytes.length != fileBytes.length){
				throw new Exception("The two byte-arrays did not have the same length: " + bodyBytes.length + ", " + fileBytes.length + "\n\n'" + (new String(bodyBytes)) + "'\n\n'" + (new String(fileBytes)) + "'.");
			}else if(!Arrays.equals(bodyBytes, fileBytes)){
				throw new Exception("Expected body byte array to be exactly the same as the original file byte array but it wasnt.");
			}
		}catch(Exception e){
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
