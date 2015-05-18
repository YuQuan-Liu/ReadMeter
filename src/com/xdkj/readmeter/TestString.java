package com.xdkj.readmeter;

import static org.junit.Assert.*;

import java.lang.reflect.Array;

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

}
