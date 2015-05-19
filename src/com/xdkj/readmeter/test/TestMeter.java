package com.xdkj.readmeter.test;

import static org.junit.Assert.*;

import org.junit.Test;

import com.xdkj.readmeter.read.ReadMeter;

public class TestMeter {

	@Test
	public void test() {
		
		
		new ReadMeter(1, 1).start();
		
	}

}
