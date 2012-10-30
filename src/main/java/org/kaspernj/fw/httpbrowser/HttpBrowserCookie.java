package org.kaspernj.fw.httpbrowser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpBrowserCookie {
	public String name;
	public String value;
	
	final static private Pattern patternParseFromStr = Pattern.compile("(.+?)=(.*?)(;\\s*|$)");
	
	public static HttpBrowserCookie parseFromStr(String str) throws Exception{
		Matcher matcherStr = patternParseFromStr.matcher(str);
		if (!matcherStr.find()){
			throw new Exception("Could not parse cookie from that string: '" + str + "'.");
		}
		
		HttpBrowserCookie cookie = new HttpBrowserCookie();
		cookie.name = matcherStr.group(1);
		cookie.value = matcherStr.group(2);
		
		return cookie;
	}
	
	public String getName(){
		return name;
	}
	
	public String getValue(){
		return value;
	}
	
	@Override public String toString(){
		String str = "";
		
		str = this.getClass().getName() + " Object {\n";
		str += " Name: " + name + "\n";
		str += " Value: " + value + "\n";
		str += "}";
		
		return str;
	}
}
