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
import com.xdkj.readmeter.dao.ValveConfLogDao;
import com.xdkj.readmeter.dao.ValveLogDao;
import com.xdkj.readmeter.db.DBPool;
import com.xdkj.readmeter.obj.CustomerWarn;
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

}
