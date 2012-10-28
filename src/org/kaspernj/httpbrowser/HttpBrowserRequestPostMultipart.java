package org.kaspernj.httpbrowser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

//This class handles file-uploads to a HTTP-host.
public class HttpBrowserRequestPostMultipart {
	private String boundaryStr = UUID.randomUUID().toString();
	private File tempFile;
	private String addr;
	private HttpBrowser http;
	private ArrayList<HttpBrowserRequestPostMultipartFileUpload> fileUploads = new ArrayList<HttpBrowserRequestPostMultipartFileUpload>();
	private HashMap<String, String> postValues = new HashMap<String, String>();
	
	public void setHttpBrowser(HttpBrowser inHttp){
		http = inHttp;
	}
	
	public void setAddress(String inAddr){
		addr = inAddr;
	}
	
	public HttpBrowserRequestPostMultipartFileUpload addFileUpload(){
		HttpBrowserRequestPostMultipartFileUpload fileupload = new HttpBrowserRequestPostMultipartFileUpload();
		fileUploads.add(fileupload);
		return fileupload;
	}
	
	public void addPost(String name, String value) throws Exception{
		if (postValues.containsKey(name)){
			throw new Exception("That post-value already exists: '" + name + "'.");
		}
		
		postValues.put(name, value);
	}
	
	public HttpBrowserResult execute() throws Exception{
		if (addr == null){
			throw new Exception("Please set an address before calling 'execute'.");
		}
		
		File tempFile = createTempData();
		
		String requestLine = "POST /" + addr + " HTTP/1.1\r\n";
		http.sockWrite(requestLine);
		
		HashMap<String, String> headers = http.defaultHeaders();
		headers.put("Content-Length", String.valueOf(tempFile.length()));
		headers.put("Content-Type", "multipart/form-data; boundary=" + boundaryStr);
		http.writeHeaders(headers);
		
		http.sockWrite("\r\n");
		
		int fileSize = (int) tempFile.length();
		int readSize = 0;
		int curReadSize = 0;
		int defReadSize = 4096;
		int actualReadSize = 0;
		BufferedInputStream input = new BufferedInputStream(new FileInputStream(tempFile));
		
		while(readSize < fileSize){
			curReadSize = (int) (fileSize - readSize);
			if (curReadSize > defReadSize){
				curReadSize = defReadSize;
			}
			
			byte[] buffer = new byte[curReadSize];
			actualReadSize = input.read(buffer, readSize, curReadSize);
			
			readSize += actualReadSize;
			http.sockWrite(new String(buffer));
			
			System.out.print(new String(buffer));
		}
		
		System.out.println("");
		http.sockWrite("\r\n");
		
		return http.readResult();
	}
	
	//Writes a temporary file with the entire post-data (this does not include headers). It is done this way to spare memory and to calculate the size for the "Content-Length"-header.
	private File createTempData() throws IOException{
		tempFile = File.createTempFile("java_httpbrowser_request_post_multipart", ".temp");
		tempFile.deleteOnExit();
		
		FileOutputStream fw = new FileOutputStream(tempFile);
		String value;
		for(String key: postValues.keySet()){
			value = postValues.get(key);
			
			fw.write(("--" + boundaryStr + "\r\n").getBytes());
			fw.write(("Content-Disposition: form-data; name=\"" + URLEncoder.encode(key) + "\";\r\n").getBytes());
			fw.write(("Content-Length: " + value.getBytes().length + "\r\n").getBytes());
			fw.write(("Content-Type: text/plain\r\n").getBytes());
			fw.write(("\r\n").getBytes());
			fw.write((value.getBytes()));
			fw.write(("\r\n").getBytes());
		}
		
		for(HttpBrowserRequestPostMultipartFileUpload fileUpload: fileUploads){
			fw.write(("--" + boundaryStr + "\r\n").getBytes());
			fw.write(("Content-Disposition: form-data; name=\"" + URLEncoder.encode(fileUpload.getPostName()) + "\"; filename=\"" + fileUpload.getFileName() + "\";\r\n").getBytes());
			fw.write(("Content-Length: " + fileUpload.getFileSize() + "\r\n").getBytes());
			fw.write(("\r\n").getBytes());
			
			int fileSize = (int) fileUpload.getFileSize();
			int readSize = 0;
			int curReadSize = 0;
			int defReadSize = 4096;
			int actualReadSize = 0;
			BufferedInputStream input = new BufferedInputStream(new FileInputStream(fileUpload.getFilePath()));
			
			while(readSize < fileSize){
				curReadSize = (int) (fileSize - readSize);
				if (curReadSize > defReadSize){
					curReadSize = defReadSize;
				}
				
				byte[] buffer = new byte[curReadSize];
				actualReadSize = input.read(buffer, readSize, curReadSize);
				
				readSize += actualReadSize;
				fw.write(buffer);
			}
			
			fw.write(("\r\n").getBytes());
		}
		
		fw.write(("\r\n--" + boundaryStr + "--").getBytes());
		fw.close();
		
		return tempFile;
	}
}
