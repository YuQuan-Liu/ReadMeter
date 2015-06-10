package com.xdkj.readmeter.test;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Properties;

import org.junit.Test;

public class TestMessage {

	@Test
	public void test() throws Exception {
		Properties props = new Properties();
		
		InputStream is = new FileInputStream(System.getProperty("user.dir")+"/messages_zh_CN.properties");
		props.load(is);
		is.close();
		
		for(Entry entry:props.entrySet()){
			String key = entry.getKey().toString();
			String value = entry.getValue().toString();
			
			System.out.println(key+"="+value);
		}
		
	}

}
