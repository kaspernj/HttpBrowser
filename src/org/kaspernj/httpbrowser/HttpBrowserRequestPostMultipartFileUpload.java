package org.kaspernj.httpbrowser;

import java.io.File;

public class HttpBrowserRequestPostMultipartFileUpload {
	private String postName;
	private String fileName;
	private String filePath;
	private String contextType;
	
	public HttpBrowserRequestPostMultipartFileUpload(){
		contextType = "text/plain";
	}
	
	public void setPostName(String inPostName){
		postName = inPostName;
	}
	
	public String getPostName(){
		return postName;
	}
	
	public void setFileName(String inFileName){
		fileName = inFileName;
	}
	
	public String getFileName(){
		return fileName;
	}
	
	public void setFilePath(String inFilePath) throws Exception{
		File fp = new File(inFilePath);
		if (!fp.exists()){
			throw new Exception("That path does not exist: '" + inFilePath + "'.");
		}
		
		filePath = inFilePath;
		
		//Auto-set file-name based on given file.
		if (fileName == null){
			fileName = fp.getName();
		}
	}
	
	public String getFilePath(){
		return filePath;
	}
	
	public long getFileSize(){
		File fp = new File(filePath);
		return fp.length();
	}
	
	public void setContextType(String inContextType){
		contextType = inContextType;
	}
	
	public String getContextType(){
		return contextType;
	}
}
