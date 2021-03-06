package org.kaspernj.fw.httpbrowser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** This class can connect to HTTP-servers and get results from them. It supports keep-alive, chunked encoding, file-uploading and GZIP compression. */
public class HttpBrowser {
	//Socket-connection to the host.
	private Socket sock;
	
	//Used to send data to the host. Public because MultipartPost-class is using it.
	public OutputStream sockOut;
	
	//Used for thread safety.
	public ReentrantLock lock = new ReentrantLock();
	
	//Used to get data from the host.
	private InputStream sockIn;
	
	//Used to extract data from headers.
	private Pattern patternHeader = Pattern.compile("^(.+?)\\s*:\\s*(.+)$");
	
	//Used to extract data from the status-line.
	private Pattern patternStatusLine = Pattern.compile("^HTTP/1\\.1\\s+(\\d+)\\s+(.+)$");
	
	//Given by headers and used to read data if we know the exact length.
	private Integer cLength;
	
	//A string containing the host or IP that should be connected to.
	private String host;
	
	//An integer of what port that should be used (default is 80).
	private Integer port = 80;
	
	//A string containing the content-encoding of the result.
	private String cEnc;
	
	//A string containing the transfer-encoding of the result (if it is chunked).
	private String tEnc;
	
	//If sat to true various debugging messages will be printed to stdout.
	public Boolean doDebug = false;
	
	//If sat to true the object will tell the host, that GZIP compression is supported. Results will automatically be decompressed.
	private Boolean encodingGZIP = true;
	
	private Integer keepaliveMax;
	private Integer keepaliveTimeout;
	private Long keepaliveInvalidAfter;
	private Pattern patternKeepAliveMax = Pattern.compile("max=(\\d+)");
	private Pattern patternKeepAliveTimeout = Pattern.compile("timeout=(\\d+)");
	private Integer requestsExecutedOnCurrectConnection;
	private ArrayList<HttpBrowserCookie> cookies = new ArrayList<HttpBrowserCookie>();
	
	//Be sure to close all connections.
	protected void finalize(){
		try{
			this.close( );
		}catch(Exception e){
			//ignore.
		}
	}
	
	//Connects to the server and sets various variables that will be used.
	public void connect() throws Exception{
		debug( "Connecting.\n" );
		lock.lock();
		
		try{
			//Close the connection if already connected to avoid leaking memory.
			close();
			
			debug("Connecting.\n");
			sock = new Socket(host, port);
			sockOut = sock.getOutputStream();
			sockIn = sock.getInputStream();
			
			requestsExecutedOnCurrectConnection = 0;
		}finally{
			lock.unlock();
		}
	}
	
	//Returns true if the socket is successfully connected. Otherwise false.
	public boolean isConnected(){
		lock.lock();
		
		try{
			if (sock == null || sockOut == null || sockIn == null || sock.isClosed() || !sock.isConnected() || sock.isInputShutdown() || sock.isOutputShutdown()){
				debug("The socket-objects has not been created or something is wrong with them.\n");
				return false;
			}else if(keepaliveInvalidAfter != null && keepaliveInvalidAfter <= System.currentTimeMillis()){
				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				Date date = new Date(keepaliveInvalidAfter);
				debug("Too much time has passed according to the keep-alive-max (" + keepaliveInvalidAfter + ", " + format.format( date ) + ") - the connection has been closed by the host.\n");
				return false;
			}else if (keepaliveMax != null && requestsExecutedOnCurrectConnection >= keepaliveMax){
				debug("We have made the maximum number of requests per connection and should reconnect (keep-alive-max: " + keepaliveMax + ", requests-executed: " + requestsExecutedOnCurrectConnection + ".\n");
				return false;
			}
			
			return true;
		}finally{
			lock.unlock();
		}
	}
	
	//Connects to the host if not already connected.
	public void ensureConnected() throws Exception{
		if (!isConnected()){
			connect();
		}
	}
	
	//Sets the hostname or IP that the object should connect to.
	public void setHost(String inHost){
		host = inHost;
	}
	
	//Sets the port that should be used for the connection.
	public void setPort(Integer inPort){
		port = inPort;
	}
	
	//If GZIP compression should be used for the request.
	public void setEncodingGZIP(Boolean inVal){
		encodingGZIP = inVal;
	}
	
	//If debug-messages should be written to stdout.
	public void setDebug(Boolean inVal){
		doDebug = inVal;
	}
	
	public ArrayList<HttpBrowserCookie> getCookies(){
		return cookies;
	}
	
	//Closes the connection to the server.
	public void close() throws Exception{
		lock.lock();
		
		try{
			debug("Closing connection.\n");
			
			try{
				if (sockIn != null){
					sockIn.close();
				}
			}finally{
				sockIn = null;
				
				try{
					if (sockOut != null){
						sockOut.close();
					}
				}finally{
					sockOut = null;
					
					try{
						if (sock != null){
							sock.close();
						}
					}finally{
						sock = null;
					}
				}
			}
			
			requestsExecutedOnCurrectConnection = null;
			keepaliveInvalidAfter = null;
		}finally{
			lock.unlock();
		}
	}
	
	//Executes a get-request and returns the result.
	public HttpBrowserResult get(String addr) throws Exception{
		lock.lock();
		
		try{
			ensureConnected();
			
			String requestLine = "GET /" + addr + " HTTP/1.1\r\n";
			HashMap<String, String> headers = defaultHeaders();
			
			debug("Sending request-line: " + requestLine + "\n");
			sockWrite(requestLine);
			writeHeaders(headers);
			
			debug("Sending end-of-headers.\n");
			sockWrite("\r\n");
			
			return readResult();
		}finally{
			lock.unlock();
		}
	}
	
	public HttpBrowserResult post(String addr, HashMap<String, String> postData) throws Exception{
		lock.lock();
		
		try{
			ensureConnected();
			
			Boolean first = true;
			String postDataStr = "";
			for(String key: postData.keySet()){
				if (first){
					first = false;
				}else{
					postDataStr += "&";
				}
				
				postDataStr += URLEncoder.encode(key, "UTF-8");
				postDataStr += "=";
				postDataStr += URLEncoder.encode(postData.get(key), "UTF-8");
			}
			
			String requestLine = "POST /" + addr + " HTTP/1.1\r\n";
			sockWrite(requestLine);
			
			HashMap<String, String> headers = defaultHeaders();
			headers.put("Content-Length", String.valueOf(postDataStr.getBytes().length));
			headers.put("Content-Type", "application/x-www-form-urlencoded");
			writeHeaders(headers);
			
			sockWrite("\r\n");
			sockWrite(postDataStr);
			sockWrite("\r\n");
			
			return readResult();
		}finally{
			lock.unlock();
		}
	}
	
	public HttpBrowserRequestPostMultipart postMultipart(){
		HttpBrowserRequestPostMultipart httpReqMp = new HttpBrowserRequestPostMultipart();
		httpReqMp.setHttpBrowser(this);
		
		return httpReqMp;
	}
	
	//Returns default headers based on various options and more.
	public HashMap<String, String> defaultHeaders() throws UnsupportedEncodingException{
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Connection", "Keep-Alive");
		headers.put("User-Agent", "Mozilla/4.0 (compatible; Java; HttpBrowser)");
		
		if (encodingGZIP){
			headers.put("Accept-Encoding", "gzip");
		}
		
		if (!cookies.isEmpty()){
			String cstr = "";
			Boolean first = true;
			
			for(HttpBrowserCookie cookie: cookies){
				if (first){
					first = false;
				}else{
					cstr += "; ";
				}
				
				cstr += URLEncoder.encode(cookie.getName(), "UTF-8");
				cstr += "=";
				cstr += URLEncoder.encode(cookie.getValue(), "UTF-8");
			}
			
			headers.put("Cookie", cstr);
		}
		
		headers.put("Host", host);
		
		return headers;
	}
	
	//Writes the given headers-HashMap to the socket.
	public void writeHeaders(HashMap<String, String> headers) throws IOException{
		for(String key: headers.keySet()){
			debug("Sending header: " + key + ": " + headers.get(key) + "\n");
			sockWrite(key + ": " + headers.get(key) + "\r\n");
		}
	}
	
	//Reads the result from the server and returns it as a result-object.
	public HttpBrowserResult readResult() throws Exception{
		debug("Reading result.\n");
		
		HttpBrowserResult res = new HttpBrowserResult();
		cLength = null;
		cEnc = null;
		tEnc = null;
		keepaliveMax = null;
		keepaliveTimeout = null;
		
		String statusLine = sockReadLine().trim();
		Matcher matcherStatusLine = patternStatusLine.matcher(statusLine);
		
		if (!matcherStatusLine.find()){
			throw new Exception("Could not understand the status-line: " + statusLine);
		}
		
		res.setStatusCode(Integer.parseInt( matcherStatusLine.group(1) ) );
		
		HashMap<String, String> headersRec = new HashMap<String, String>();
		debug("Starting to read headers.\n");
		readResultHeaders(headersRec);
		
		//Set various variables.
		res.setHeaders(headersRec);
		res.keepAliveMax = keepaliveMax;
		res.keepAliveTimeout = keepaliveTimeout;
		res.contentEncoding = cEnc;
		res.transferEncoding = tEnc;
		
		byte[] bodyByteArray;
		
		if (tEnc != null && tEnc.equals("chunked")){
			debug("Reading chunked body.\n");
			bodyByteArray = readResultBodyChunked();
		}else if(cLength != null){
			debug("Reading body from content-length.\n");
			bodyByteArray = readResultBodyFromContentLength();
		}else{
			debug("Didnt know how to read body.\n");
			throw new Exception("Dont know how to read result from that encoding: '" + tEnc + "'.");
		}
		
		if (cEnc != null && cEnc.equals("gzip")){
			//Decompress the body if it has been compressed with GZip.
			debug("Converting body to string from GZip.\n");
			bodyByteArray = decompressGZIPByteArray(bodyByteArray);
		}
		
		res.setBodyByteArray(bodyByteArray);
		requestsExecutedOnCurrectConnection += 1;
		
		return res;
	}
	
	//Decompresses a GZIP byte-array and returns the orignal uncompress byte-array.
	private byte[] decompressGZIPByteArray(byte[] gzippedByteArray) throws IOException{
		ByteArrayInputStream bais = new ByteArrayInputStream(gzippedByteArray);
		GZIPInputStream gz = new GZIPInputStream(bais);
		BufferedInputStream gzIn = new BufferedInputStream(gz);
		ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		
		int len;
		byte[] buf = new byte[4096];
		
		while((len = gzIn.read(buf)) > 0){
			for(int i = 0; i < len; i++){
				bytesOut.write(buf[i]);
			}
		}
		
		bytesOut.close();
		gzIn.close();
		gz.close();
		bais.close();
		
		return bytesOut.toByteArray();
	}
	
	//Reads the header-part of the result from the server and adds those headers to the given HashMap.
	private void readResultHeaders(HashMap<String, String> headersRec) throws Exception{
		String line;
		
		while(true){
			debug("Trying to read header-line.\n");
			line = sockReadLine().trim();
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
					}else if(key.equals("keep-alive")){
						Matcher matchMax = patternKeepAliveMax.matcher(val);
						if (matchMax.find()){
							debug("New keepalive max: " + matchMax.group(1) + "\n");
							keepaliveMax = Integer.parseInt(matchMax.group(1));
						}else{
							debug("Could not match max from keepalive header.\n");
						}
						
						Matcher matchTimeout = patternKeepAliveTimeout.matcher(val);
						if (matchTimeout.find()){
							debug("New keepalive timeout: " + matchTimeout.group(1) + "\n");
							keepaliveTimeout = Integer.parseInt(matchTimeout.group(1));
							
							debug("Time millis: " + System.currentTimeMillis() + "\n");
							debug("Invalid millis: " + (System.currentTimeMillis() + (keepaliveTimeout * 1000)) + "\n");
							keepaliveInvalidAfter = System.currentTimeMillis() + (keepaliveTimeout * 1000);
						}else{
							debug("Could not match timeout from keepalive header.\n");
						}
					}else if(key.equals("set-cookie")){
						debug("Found set-cookie!\n");
						
						HttpBrowserCookie cookie = HttpBrowserCookie.parseFromStr(val);
						this.addCookie(cookie);
						
						debug("New cookie found. Name: '" + cookie.getName() + "', Value: '" + cookie.getValue() + "'.\n");
					}
				}else{
					throw new Exception("Could not match header from line: '" + line + "'.");
				}
			}
		}
	}
	
	//Reads the body based on continuous content given by chunked encoding until the chunked end-content is given.
	private byte[] readResultBodyChunked() throws Exception{
		Integer len;
		String line;
		ArrayList<byte[]> parts = new ArrayList<byte[]>();
		int totalCharLength = 0;
		
		while(true){
			line = sockReadLine();
			debug("Length-teller: '" + line + "'.\n");
			
			len = Integer.parseInt(line.trim(), 16);
			debug("Got new length to receive for body: " + len + "\n");
			
			//This will happen when there is no more content to be received.
			if (len == 0){
				line = sockReadLine();
				if (line.equals("\r\n") || line.equals("\n")){
					break;
				}else{
					throw new Exception("Expected empty read: '" + debugStr(line) + "'.\n");
				}
			}
			
			byte[] part = sockReadLengthAsByteArray(len);
			parts.add(part);
			totalCharLength += part.length;
			
			debug("Received part of " + part.length + ".\n");
			
			line = sockReadLine();
			if (!line.equals("\r\n") && !line.equals( "\n" )){
				throw new Exception("Expected newline: '" + debugStr(line) + "'.\n");
			}
		}
		
		byte[] total = new byte[totalCharLength];
		int count = 0;
		for(byte[] item: parts){
			for(byte char_i: item){
				total[count] = char_i;
				count += 1;
			}
		}
		
		return total;
	}
	
	//Reads the body based on the content-length given by headers.
	private byte[] readResultBodyFromContentLength() throws Exception{
		return sockReadLengthAsByteArray(cLength);
	}
	
	//Used to write out debugging-messages to stdout if the debug-argument is given.
	private void debug(String str){
		if (doDebug){
			System.out.print(str);
		}
	}
	
	//Returns a changed version of the string exposing various special characters.
	private String debugStr(String str){
		return str.replaceAll("\\r", "\\\\r").replaceAll("\\n", "\\\\n");
	}
	
	//Writes the given string to the socket.
	public void sockWrite(String str) throws IOException{
		debug("Writing string to socket: '" + str + "'.\n");
		sockOut.write(str.getBytes());
	}
	
	//Writes the given byte-array to the socket.
	public void sockWrite(byte[] byteArr) throws IOException{
		debug("Writing string to socket: '" + byteArr.toString() + "'.\n");
		sockOut.write(byteArr);
	}
	
	//Reads a line from the socket and returns it as a string.
	private String sockReadLine() throws IOException{
		StringBuffer sb = new StringBuffer();
		int chInt;
		
		while(true){
			chInt = sockIn.read();
			if (chInt == -1){
				throw new IOException("Socket seems to have closed on us?");
			}
			
			sb.append((char) chInt);
			
			if (chInt == 10){
				break;
			}
		}
		
		debug("Read line: '" + debugStr(sb.toString()) + "'.\n");
		return sb.toString();
	}
	
	//Reads a given length from the socket as a byte-array.
	private byte[] sockReadLengthAsByteArray(int length) throws IOException{
		byte[] buffer = new byte[length];
		int readTotal = 0;
		
		while(readTotal < length){
			buffer[readTotal] = (byte) sockIn.read();
			readTotal += 1;
			
			if (readTotal > length){
				break;
			}
		}
		
		return buffer;
	}
	
	public void addCookie(HttpBrowserCookie cookie){
		try{
			HttpBrowserCookie existingCookie = this.getCookieByName(cookie.getName());
			cookies.remove(existingCookie);
		}catch(NoSuchFieldException e){
			//Ignore - a cookie with the given name does not exist..
		}
		
		cookies.add(cookie);
	}
	
	public HttpBrowserCookie getCookieByName(String name) throws NoSuchFieldException{
		ArrayList<String> cookieNames = new ArrayList<String>();
		
		for(HttpBrowserCookie cookie: cookies){
			if (cookie.getName().equals(name)){
				return cookie;
			}else{
				cookieNames.add(cookie.getName());
			}
		}
		
		throw new NoSuchFieldException("No cookies by that name: '" + name + "' (" + cookieNames.toString() + ").");
	}
}
