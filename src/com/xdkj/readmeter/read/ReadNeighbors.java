package com.xdkj.readmeter.read;

import java.util.List;

import com.xdkj.readmeter.dao.ReadLogDao;
import com.xdkj.readmeter.obj.Readlog;

public class ReadNeighbors extends Thread{
	
	private int adminid;
	private int readlogid;
	
	public ReadNeighbors(int adminid, int readlogid) {
		super();
		this.adminid = adminid;
		this.readlogid = readlogid;
	}

	@Override
	public void run() {
		//get all the neighbors
		List<Readlog> list = ReadLogDao.getAllNeighborReadlog(adminid, readlogid);
		ReadNeighbor readNeighbor = null;
		Readlog readlog = null;
		for(int i = 0;null != list && i < list.size();i++){
			readlog = list.get(i);
			readNeighbor = new ReadNeighbor(readlog.getObjectId(), readlog.getPid());
			readNeighbor.start();
		}
		
	}
}
