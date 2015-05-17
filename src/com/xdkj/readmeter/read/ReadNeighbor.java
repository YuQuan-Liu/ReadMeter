package com.xdkj.readmeter.read;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

import com.xdkj.readmeter.dao.GPRSDao;
import com.xdkj.readmeter.obj.GPRS;

public class ReadNeighbor extends Thread{
	
	private int nid;
	private int readlogid;
	public ReadNeighbor(int nid, int readlogid) {
		super();
		this.nid = nid;
		this.readlogid = readlogid;
	}
	
	@Override
	public void run() {
		List<GPRS> gprss = GPRSDao.getGPRSsbyNID(nid);
		if(gprss.size() == 0){
			return;
		}
		
		CountDownLatch latch = new CountDownLatch(gprss.size());
		//the key is the gprsaddr the value is a map (which contains the result of the gprs)
		ConcurrentMap<String, Map<String,String>> results = new ConcurrentHashMap<>();
		ReadGPRS readgprs = null;
		GPRS gprs = null;
		for(int i = 0;i < gprss.size();i++){
			gprs = gprss.get(i);
			readgprs = new ReadGPRS(gprs, results, latch);
			readgprs.start();
		}
		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//the neighbor is read over
		//get the result from the results the key is the gprsaddr the value is a map (which contains the result of the gprs)
		//TODO
	}

}
