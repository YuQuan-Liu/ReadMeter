package com.xdkj.readmeter.test;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.xdkj.readmeter.dao.MeterDeductionDao;
import com.xdkj.readmeter.dao.ReadMeterLogDao;
import com.xdkj.readmeter.dao.ValveConfLogDao;
import com.xdkj.readmeter.dao.ValveLogDao;
import com.xdkj.readmeter.db.DBPool;
import com.xdkj.readmeter.obj.Collector;
import com.xdkj.readmeter.obj.CustomerWarn;
import com.xdkj.readmeter.obj.GPRS;
import com.xdkj.readmeter.obj.Valvelog;
import com.xdkj.readmeter.read.ReadMeter;
import com.xdkj.readmeter.read.ValveControl;
import com.xdkj.readmeter.read.WarnSender;

public class TestMeter {

	@Test
	public void test() {
		
		List<CustomerWarn> list = MeterDeductionDao.warnCustomer(1, 0, 0);
		
		CustomerWarn warn = null;
		for(int i = 0;list != null && i <list.size();i++){
			warn = list.get(i);
			System.out.println(warn.toString());
		}
	}
	
	@Test
	public void test2() throws Exception {
		List<CustomerWarn> list = MeterDeductionDao.warnCustomer(1, 0, 0);
		
		new WarnSender(list).run();
	}
	@Test
	public void testbreakdown() throws Exception {
		ReadMeterLogDao.addBreakdown(96, null, 1);
	}
	@Test
	public void testbreakdowns() throws Exception {
		GPRS g = new GPRS();
		g.setPid(1);
		Collector col = new Collector();
		col.setColAddr(1);
		col.setMeterNums(3);
		ReadMeterLogDao.addBreakdown(96, g, col);
	}
	
	
	@Test
	public void adddatas() throws Exception {
		GPRS g = new GPRS();
		g.setPid(1);
		Collector col = new Collector();
		col.setColAddr(1);
		col.setMeterNums(3);
		
		byte[] deal = new byte[30];
		deal[0]= 0x0E;
		deal[1]= 0x0D;
		deal[2]= 0x0C;
		deal[3]= 0x0E;
		deal[4]= 0x01;
		deal[5]= 0x0E;
		deal[6]= 0x0E;
		deal[7]= 0x01;
		deal[8]= 0x01;
		deal[9]= 0x00;
		
		
		deal[10]= 0x0E;
		deal[11]= 0x0D;
		deal[12]= 0x0C;
		deal[13]= 0x0E;
		deal[14]= 0x02;
		deal[15]= 0x0E;
		deal[16]= 0x0E;
		deal[17]= (byte) 0xAA;
		deal[18]= (byte) 0xAA;
		deal[19]= 0x03;
		
		
		deal[20]= 0x0E;
		deal[21]= 0x0D;
		deal[22]= 0x0C;
		deal[23]= 0x0E;
		deal[24]= 0x03;
		deal[25]= 0x0E;
		deal[26]= 0x0E;
		deal[27]= (byte) 0xBB;
		deal[28]= (byte) 0xBB;
		deal[29]= 0x02;

		
		ReadMeterLogDao.addReadMeterLogs(96, g, col,deal);
	}
	
	
	@Test
	public void out() throws Exception {
		for(int i = 0;i < 30;i++){
			System.out.println("deal["+i+"]= 0x0E;");
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
