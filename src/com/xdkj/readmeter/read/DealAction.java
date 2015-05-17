package com.xdkj.readmeter.read;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.xdkj.readmeter.dao.ReadLogDao;
import com.xdkj.readmeter.dao.ValveLogDao;
import com.xdkj.readmeter.obj.Readlog;
import com.xdkj.readmeter.obj.Valvelog;

public class DealAction {
	
	private final static ExecutorService threadpool = Executors.newFixedThreadPool(20);

	public static void addAction(String function,int pid){
		if(function.equalsIgnoreCase("read")){
			//get the readlog by the pid 
			Readlog readlog = ReadLogDao.getByID(pid);
			if(readlog.getReadStatus() >= 100){
				return;
			}
			switch (readlog.getReadObject()) {
			case 1:
				ReadNeighbor readNeighbor = new ReadNeighbor(readlog.getObjectId(), readlog.getPid());
				threadpool.execute(readNeighbor);
				break;
			case 2:
				ReadNeighbors readNeighbors = new ReadNeighbors(readlog.getAdminid(), readlog.getPid());
				threadpool.execute(readNeighbors);
				break;
			case 3:
				ReadMeter readMeter = new ReadMeter(readlog.getObjectId(), readlog.getPid());
				threadpool.execute(readMeter);
				break;
			default:
				break;
			}
		}
		if(function.equalsIgnoreCase("valve")){
			//get the valvelog by pid
			Valvelog valvelog = ValveLogDao.getByID(pid);
			if(valvelog.getStatus() >= 100){
				return;
			}
			ValveControl valveControl = new ValveControl(valvelog);
			threadpool.execute(valveControl);
		}
	}
	
}
