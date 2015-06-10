package com.xdkj.readmeter.test;

import static org.junit.Assert.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.xdkj.readmeter.util.StringUtil;

public class TestString {

	@Test
	public void test() {
		
//		String x= "1234567890";
		String x= "FFFFFFFFFF";
		
		String[] xx = new String[5];
		xx[0] = x.substring(0, 2);
		xx[1] = x.substring(2, 4);
		xx[2] = x.substring(4, 6);
		xx[3] = x.substring(6, 8);
		xx[4] = x.substring(8, 10);
		byte[] addr = new byte[5];
		
		for(int i = 0;i<xx.length;i++){
			System.out.println(xx[i]);
//			addr[i] = Byte.parseByte(xx[i], 16);
			addr[i] = (byte) Integer.parseInt(xx[i], 16);
			System.out.println(addr[i]);
		}
//		
//		Arrays.
	}
	@Test
	public void test2(){
		byte[] b = new byte[10];
		for(int i = 0;i < 10;i++){
			b[i] = (byte) ((byte) i+0x10);
			System.out.println(String.format("%02x", b[i]&0xFF));;
		}
		System.out.println(StringUtil.byteArrayToHexStr(b, 10));
		
		
	}

	
	@Test
	public void testsplit() {
		String[] x = "".split(",");
		System.out.println(x.length);
		 x = "11".split(",");
		System.out.println(x.length);

	}
	
	
	@Test
	public void testday() {
		List<String> list = new ArrayList<>();
		for(int i = 1;i <32;i++){
			list.add(i+"");
		}
//		["1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26","27","28","29","30","31"]
		System.out.println(new JSONArray(list).toString());;

	}
	
	@Test
	public void testday2() {
		List<Integer> list = new ArrayList<>();
		for(int i = 1;i <32;i++){
			list.add(0);
		}
//		["1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26","27","28","29","30","31"]
		System.out.println(new JSONArray(list).toString());;

	}
	
	@Test
	public void testtobyte() throws Exception {
		System.out.println(new StringBuilder("55550000000001").reverse().toString());
		byte[] bb = StringUtil.string2Byte(new StringBuilder("55550000000001").reverse().toString());
		for(int i = 0;i < bb.length;i++){
			System.out.println(bb[i]);
		}
	}
	
}
