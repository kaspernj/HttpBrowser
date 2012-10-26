package org.kaspernj.httpbrowser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpBrowser {
	private HashMap<String, String> args;
	private Socket sock;
	private BufferedWriter sockOut;
	private BufferedReader sockIn;
	private Pattern patternHeader = Pattern.compile("^(.+)\\s*:\\s*(.+)$");
	private Pattern patternStatusLine = Pattern.compile("^HTTP/1\\.1\\s+(\\d+)\\s+(.+)$");
	private Integer statusCode;
	private Integer cLength;
	private String cEnc;
	private String tEnc;
	private Boolean doDebug = false;
	
	public HttpBrowser(HashMap<String, String> in_args){
		args = in_args;
	}
	
	public HttpBrowser(String in_host, Integer in_port){
		args = new HashMap<String, String>();
		args.put("host", in_host);
		args.put("port", in_port.toString());
	}
	
	public void connect() throws NumberFormatException, UnknownHostException, IOException{
		if (args.containsKey("debug") && args.get("debug").equals("1")){
			doDebug = true;
			debug("Enabled debugging.\n");
		}else{
			doDebug = false;
		}
		
		sock = new Socket(args.get("host"), Integer.parseInt(args.get("port")));
		sockOut = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
		sockIn = new BufferedReader(new InputStreamReader(sock.getInputStream()));
	}
	
	public void close() throws Exception{
		if (sock == null){
			throw new Exception("Not connect yet.");
		}
		
		sock.close();
	}
	
	public HttpBrowserResult get(String addr) throws Exception{
		String requestLine = "GET /" + addr + " HTTP/1.1\r\n";
		
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Connection", "Keep-Alive");
		headers.put("User-Agent", "Mozilla/4.0 (compatible; Java; HttpBrowser)");
		headers.put("Accept-Encoding", "gzip");
		headers.put("Host", args.get("host"));
		
		debug("Sending request-line: " + requestLine + "\n");
		sockOut.write(requestLine);
		
		for(String key: headers.keySet()){
			debug("Sending header: " + key + ": " + headers.get(key) + "\n");
			sockOut.write(key + ": " + headers.get(key) + "\r\n");
		}
		
		debug("Sending end-of-headers.");
		sockOut.write("\r\n");
		sockOut.flush();
		
		return readResult();
	}
	
	private HttpBrowserResult readResult() throws Exception{
		debug("Reading result.\n");
		
		HttpBrowserResult res = new HttpBrowserResult();
		String body;
		cLength = null;
		cEnc = null;
		tEnc = null;
		
		String statusLine = sockIn.readLine();
		Matcher matcherStatusLine = patternStatusLine.matcher(statusLine);
		
		if (!matcherStatusLine.find()){
			throw new Exception("Could not understand the status-line: " + statusLine);
		}
		
		HashMap<String, String> headersRec = new HashMap<String, String>();
		
		debug("Starting to read headers.\n");
		readResultHeaders(headersRec);
		res.setHeaders(headersRec);
		
		if (tEnc == "chunked"){
			debug("Reading chunked body.\n");
			body = readResultBodyChunked();
		}else if(cLength != null){
			debug("Reading body from content-length.\n");
			body = readResultBodyFromContentLength();
		}else{
			debug("Didnt know how to read body.\n");
			throw new Exception("Dont know how to read result from that encoding: '" + tEnc + "'.");
		}
		
		res.setBody(body);
		return res;
	}
	
	private void readResultHeaders(HashMap<String, String> headersRec) throws Exception{
		String line;
		
		while(true){
			debug("Trying to read header-line.\n");
			line = sockIn.readLine();
			debug("Read header-line: '" + line + "'.\n");
			
			if (line.equals("")){
				break;
			}else{
				Matcher matchHeader = patternHeader.matcher(line);
				
				if (matchHeader.find()){
					String key = matchHeader.group(1).toLowerCase().trim();
					String val = matchHeader.group(2);
					
					debug("Matched new header: '" + key + "': '" + val + "'.\n");
					
					headersRec.put(key, val);
					
					if (key.equals("content-length")){
						cLength = Integer.parseInt(val);
					}else if(key.equals("content-encoding")){
						cEnc = val.toLowerCase().trim();
					}else if(key.equals("transfer-encoding")){
						tEnc = val.toLowerCase().trim();
					}
				}else{
					throw new Exception("Could not match header from line: '" + line + "'.");
				}
			}
		}
	}
	
	private String readResultBodyChunked() throws Exception{
		String body = "";
		Integer len;
		Integer readTotalLength = 0;
		Integer readLength;
		char[] partBytes;
		
		while(true){
			len = Integer.parseInt(sockIn.readLine(), 16);
			debug("Got new length to receive for body: " + len + "\n");
			
			//This will happen when there is no more content to be recieved.
			if (len == 0){
				String nl = sockIn.readLine();
				if (nl.equals("")){
					break;
				}
			}
			
			partBytes = new char[len];
			readLength = sockIn.read(partBytes, readTotalLength, len);
			
			debug("Got new body-part: " + partBytes.toString());
			body += partBytes.toString();
			
			readTotalLength += readLength;
		}
		
		return body;
	}
	
	private String readResultBodyFromContentLength() throws Exception{
		String body = "";
		Integer len;
		Integer readTotalLength = 0;
		Integer readLength;
		char[] partBytes;
		Integer lengthPerRead = 4096;
		
		while(true){
			if (readTotalLength + lengthPerRead > cLength){
				lengthPerRead = cLength - readTotalLength;
			}
			
			partBytes = new char[lengthPerRead];
			readLength = sockIn.read(partBytes, readTotalLength, lengthPerRead);
			readTotalLength += readLength;
			
			debug("Adding new body-part: '" + partBytes.toString() + "'.\n");
			body += partBytes.toString();
			
			if (readTotalLength >= cLength){
				debug("Total length more than content-length: " + readTotalLength + ", " + cLength + ".\n");
				break;
			}
		}
		
		return body;
	}
	
	private void debug(String str){
		if (doDebug){
			System.out.print(str);
		}
	}
}
