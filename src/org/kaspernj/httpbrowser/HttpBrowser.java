package org.kaspernj.httpbrowser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** This class can connect to HTTP-servers and get results from them. It supports keep-alive, chunked encoding and GZIP compression. */
public class HttpBrowser {
	//Socket-connection to the host.
	private Socket sock;
	
	//Used to send data to the host.
	private OutputStream sockOut;
	
	//Used to get data from the host.
	private InputStream sockIn;
	
	//Used to extract data from headers.
	private Pattern patternHeader = Pattern.compile("^(.+)\\s*:\\s*(.+)$");
	
	//Used to extract data from the status-line.
	private Pattern patternStatusLine = Pattern.compile("^HTTP/1\\.1\\s+(\\d+)\\s+(.+)$");
	
	//Given by headers and used to read data if we know the exact length.
	private Integer cLength;
	
	//A string containing the host or IP that should be connected to.
	private String host;
	private Integer port;
	
	//A string containing the content-encoding of the result.
	private String cEnc;
	
	//A string containing the transfer-encoding of the result (if it is chunked).
	private String tEnc;
	
	//If sat to true various debugging messages will be printed to stdout.
	private Boolean doDebug = false;
	
	//If sat to true the object will tell the host, that GZIP compression is supported. Results will automatically be decompressed.
	private Boolean encodingGZIP = true;
	
	//If sat to true the object will tell the host, that chunked transfer-encoding is supported. The result will automatically be decoded.
	private Boolean transferEncodingChunked = true;
	
	//Connects to the server and sets various variables that will be used.
	public void connect() throws NumberFormatException, UnknownHostException, IOException{
		sock = new Socket(host, port);
		sockOut = sock.getOutputStream();
		sockIn = sock.getInputStream();
	}
	
	//Sets the hostname or IP that the object should connect to.
	void setHost(String inHost){
		host = inHost;
	}
	
	//Sets the port that should be used for the connection.
	void setPort(Integer inPort){
		port = inPort;
	}
	
	//If GZIP compression should be used for the request.
	void setEncodingGZIP(Boolean inVal){
		encodingGZIP = inVal;
	}
	
	void setTransferEncodingChunked(Boolean inVal){
		transferEncodingChunked = inVal;
	}
	
	//If debug-messages should be written to stdout.
	void setDebug(Boolean inVal){
		doDebug = inVal;
	}
	
	//Closes the connection to the server.
	public void close() throws Exception{
		if (sock == null){
			throw new Exception("Not connect yet.");
		}
		
		sock.close();
	}
	
	//Executes a get-request and returns the result.
	public HttpBrowserResult get(String addr) throws Exception{
		String requestLine = "GET /" + addr + " HTTP/1.1\r\n";
		
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Connection", "Keep-Alive");
		headers.put("User-Agent", "Mozilla/4.0 (compatible; Java; HttpBrowser)");
		
		if (encodingGZIP){
			headers.put("Accept-Encoding", "gzip");
		}
		
		headers.put("Host", host);
		
		debug("Sending request-line: " + requestLine + "\n");
		sockWrite(requestLine);
		
		for(String key: headers.keySet()){
			debug("Sending header: " + key + ": " + headers.get(key) + "\n");
			sockWrite(key + ": " + headers.get(key) + "\r\n");
		}
		
		debug("Sending end-of-headers.");
		sockWrite("\r\n");
		
		return readResult();
	}
	
	//Reads the result from the server and returns it as a result-object.
	private HttpBrowserResult readResult() throws Exception{
		debug("Reading result.\n");
		
		HttpBrowserResult res = new HttpBrowserResult();
		cLength = null;
		cEnc = null;
		tEnc = null;
		
		String statusLine = sockReadLine().trim();
		Matcher matcherStatusLine = patternStatusLine.matcher(statusLine);
		
		if (!matcherStatusLine.find()){
			throw new Exception("Could not understand the status-line: " + statusLine);
		}
		
		HashMap<String, String> headersRec = new HashMap<String, String>();
		
		debug("Starting to read headers.\n");
		readResultHeaders(headersRec);
		res.setHeaders(headersRec);
		
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
		
		debug("Converting body to string.");
		
		if (cEnc != null && cEnc.equals("gzip")){
			//Decompress the body if it has been compressed with GZip.
			bodyByteArray = decompressGZIPByteArray(bodyByteArray);
		}
		
		res.setBodyByteArray(bodyByteArray);
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
				if (line.equals("\r\n")){
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
			if (!line.equals("\r\n")){
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
	private void sockWrite(String str) throws IOException{
		sockOut.write(str.getBytes());
	}
	
	//Reads a line from the socket and returns it as a string.
	private String sockReadLine() throws IOException{
		StringBuffer sb = new StringBuffer();
		int chInt;
		
		while(true){
			chInt = sockIn.read();
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
}
