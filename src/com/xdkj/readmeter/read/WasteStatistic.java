package com.xdkj.readmeter.read;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.xdkj.readmeter.dao.WasteLogDao;
import com.xdkj.readmeter.obj.WasteLog;

public class WasteStatistic {

	public static void statistic(List<WasteLog> logs,int nid,int readlogid){
		
		if(logs.size() > 0){
			//每个楼的水损统计
			WasteLog log = null;
			WasteLog log2 = null;
			Map<String, Map<Integer,WasteLog>> map = new HashMap<>();
			for(int i = 0;i < logs.size();i++){
				log = logs.get(i);
				if(!map.containsKey(log.getLouNum())){
					Map<Integer,WasteLog> map2 = new HashMap<>();
					//楼总表
					for(int j = 0;j < logs.size();j++){
						log2 = logs.get(j);
						if(log2.getLouNum().equals(log.getLouNum()) && i != j){
							map2.put(log.getMainMeter(), log);
							map2.put(log2.getMainMeter(), log2);
							map.put(log.getLouNum(), map2);
							break;
						}
					}
				}
			}
			
			//这里面不会包含小区的总表  小区的总表单独统计  这里统计的是楼的总表和分表
			for(Entry<String, Map<Integer,WasteLog>> entry : map.entrySet()){
				Map<Integer,WasteLog> map2 = entry.getValue();
				String lou = entry.getKey();
				log = map2.get(0);  //普通表
				log2 = map2.get(2);  //楼总表
				
				WasteLogDao.addWasteLog(nid,readlogid,log2.getM_id(),lou,log2.getReaddata(),log.getReaddata(),log2.getReaddata()-log.getReaddata());
			}
			
		}
		//小区的水损统计
		WasteLogDao.addNeiWasteLog(nid, readlogid);
	}
}

