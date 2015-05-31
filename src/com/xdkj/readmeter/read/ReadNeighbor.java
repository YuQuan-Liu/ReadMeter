package com.xdkj.readmeter.read;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

import com.xdkj.readmeter.dao.GPRSDao;
import com.xdkj.readmeter.dao.MeterDeductionDao;
import com.xdkj.readmeter.dao.ReadLogDao;
import com.xdkj.readmeter.obj.CustomerWarn;
import com.xdkj.readmeter.obj.GPRS;
import com.xdkj.readmeter.obj.Readlog;

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
			readgprs = new ReadGPRS(readlogid, gprs, results, latch);
			readgprs.start();
		}
		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//the neighbor is read over
		//get the result from the results the key is the gprsaddr the value is a map (which contains the result of the gprs)
		//
		
		//统计当前小区的水损
		
		
		Readlog readlog = ReadLogDao.getByID(readlogid);
		//结算阀控需要自动扣费的表具
		MeterDeductionDao.calculate(nid,readlog.getAdminid(),readlog.getPid());
		
		//对低于用户的金额阀值的用户进行短信提醒
		List<CustomerWarn> list = MeterDeductionDao.warnCustomer(nid,readlog.getAdminid(),readlog.getPid());
		if(list != null){
			new WarnSender(list).start();
		}
		
		//对阀控自动扣费的表具进行阀门控制
		int valvelogid = MeterDeductionDao.valvecontrol(nid,readlog.getAdminid(),readlog.getPid());
		if(valvelogid > 0){
			DealAction.addAction("valve", valvelogid);
		}
		
		String reason = "";
		String result = "";
		for(Entry<String, Map<String, String>> entry : results.entrySet()){
			String key = entry.getKey();
			Map<String,String> resultmap = entry.getValue();
//			if(resultmap.get("success").equals("true")){
//				result += 
//			}
			result = result + key+":"+resultmap.get("result");
			reason = reason + key+":"+resultmap.get("error");
		}
		ReadLogDao.updateReadLog(readlogid,result,reason);
	}

	public static void main(String[] args) {
		new ReadNeighbor(1, 1).start();
	}
}
