package com.xdkj.readmeter.read;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;


import com.xdkj.readmeter.dao.GPRSDao;
import com.xdkj.readmeter.dao.MeterDao;
import com.xdkj.readmeter.dao.ReadMeterLogDao;
import com.xdkj.readmeter.obj.Collector;
import com.xdkj.readmeter.obj.Frame;
import com.xdkj.readmeter.obj.GPRS;
import com.xdkj.readmeter.util.StringUtil;

public class ReadGPRS extends Thread {

	private int readlogid;
	private GPRS gprs;
	private CountDownLatch latch;
	private ConcurrentMap<String, Map<String,String>> results;
	
	private Map<String, String> result = new HashMap<>();
	
	public ReadGPRS(int readlogid, GPRS gprs,ConcurrentMap<String, Map<String,String>> results, CountDownLatch latch) {
		super();
		this.readlogid = readlogid;
		this.gprs = gprs;
		this.results = results;
		this.latch = latch;
		this.results.put(gprs.getGprsaddr(), result);
	}

	@Override
	public void run() {
		switch (gprs.getGprsprotocol()) {
		case 1:
			//EG
			readEG();
			break;
		case 2:
			//188
			read188();
			break;
		default:
			break;
		}
	}
	
	private void readEG() {
		Socket s = null;
		OutputStream out = null;
		InputStream in = null;
		int count = 0;
		byte[] data = new byte[1024];
		String res = "";
		int normal = 0;
		int timeout = 0;
		
		try {
			s = new Socket(gprs.getIp(),gprs.getPort());
			s.setSoTimeout(2*60*1000);  //2min
			out = s.getOutputStream();
			in = s.getInputStream();
			
			out.write(("ATDT"+gprs.getGprsaddr()).getBytes());
			while((count = in.read(data,0,1024)) > 0){
				res = new String(data,0,count);
				break;
			}
			
			if(res.equalsIgnoreCase("GPRS")){
				List<Collector> list = MeterDao.getCollectorsByGID(gprs.getPid());
				Collector col = null;
				byte[] at = new byte[10];
				byte[] deal = new byte[2048];
				int middle = 0;
				for(int i = 0;null!=list && i < list.size();i++){
					col = list.get(i);
					int cjqmeters = col.getMeterNums();
					
					at[0] = 0x0E;
					at[1] = 0x0D;
					at[2] = 0x0C;
					at[3] = 0x01;
					at[4] = (byte) cjqmeters;
					at[5] = (byte) (col.getColAddr() / 256);
					at[6] = (byte) col.getColAddr();
					at[7] = (byte) 0xFF;
					at[8] = (byte) 0xFF;
					at[9] = (byte) (at[0] ^ at[1] ^ at[2] ^ at[3] ^ at[4] ^ at[5] ^ at[6] ^ at[7] ^ at[8]);
					
					out.write(at);
					
					while ((count = in.read(data, 0, 1024)) > 0) {
						
						for(int j = 0;j<count;j++){
							deal[middle+j] = data[j];
						}

						middle = middle + count;
						
						int meters = (middle)/10;
						
						if(count == 9 && (new String(data,0,count)).contains("BREAKDOWN")){
//							breakdown(gprs,col); 
							ReadMeterLogDao.addBreakdown(readlogid,gprs,col);
							String breakdown = result.get("breakdown");
							if(null == breakdown){
								breakdown = col.getColAddr() +",";
							}else{
								breakdown = breakdown + col.getColAddr() +",";
							}
							result.put("breakdown", breakdown);
							timeout += timeout;
							break;
						}
						if(cjqmeters == meters){
//							dataToDB(gprs,col,deal);  
							ReadMeterLogDao.addReadMeterLogs(readlogid,gprs,col,deal);
							normal += cjqmeters;
							break;
						}
					}
				}
				result.put("success", "true");
			}else{
				result.put("success", "false");
				result.put("error", res);
			}
			
		} catch (Exception e) {
			result.put("success", "false");
			result.put("error", e.getMessage());
			e.printStackTrace();
		} finally{
			try {
				if(out!=null){
					out.close();
				}
				if(in!=null){
					in.close();
				}
				if(s!=null){
					s.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			result.put("result", "正常"+normal+";超时"+timeout);
			latch.countDown();
		}
		
	}

	private void read188() {
		Socket s = null;
		OutputStream out = null;
		InputStream in = null;
		int count = 0;
		byte[] data = new byte[1024];
		int normal = 0;
		int timeout = 0;
		
		try {
			s = new Socket(gprs.getIp(),gprs.getPort());
			s.setSoTimeout(10*60*1000);  //10min
			out = s.getOutputStream();
			in = s.getInputStream();
			byte[] gprsaddr = StringUtil.string2Byte(gprs.getGprsaddr());
			
			Frame login = new Frame(0, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_LINE), 
					Frame.AFN_LOGIN, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR), 
					(byte)0x01, gprsaddr, new byte[0]);
			
			out.write(login.getFrame());
			while((count = in.read(data,0,17)) > 0){
				break;
			}
			if(Frame.checkFrame(Arrays.copyOf(data, count))){
				Frame login_result = new Frame(Arrays.copyOf(data, count));
				if(login_result.getFn() == 0x01){
					//online
					//get the meters under the gprs
					int meters = MeterDao.getMeterCountByGID(gprs.getPid());
					//the data in the frame
					byte[] framedata = new byte[11];
					framedata[0] = 0x10;
					for(int i= 1;i <= 7;i++){
						framedata[i] = (byte) 0xFF;
					}
					framedata[8] = 0x00;
					framedata[9] = 0x00;
					framedata[10] = 0x01;
					
					Frame readgprs = new Frame(11, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND), 
							Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR), 
							(byte)0x04, gprsaddr, framedata);
					
					out.write(readgprs.getFrame());
					
					byte[] deal = new byte[1024];
					int middle = 0;
					while ((count = in.read(data, 0, 1024)) > 0) {
						for(int j = 0;j<count;j++){
							deal[middle+j] = data[j];
						}

						middle = middle + count;
						
						if(Frame.checkFrame(Arrays.copyOf(deal, middle))){
							Frame readdata = new Frame(Arrays.copyOf(deal, middle));
							byte[] meterdata = readdata.getData();
							int metercount = (meterdata.length-1-3)/14;
							meters -= metercount;
							//判断表的状态  看是否超时  
							for(int i = 0;i < metercount;i++){
								byte state = meterdata[i*14+3+1+12];
								if(((state &0x40) ==0x40) && ((state &0x80)==0x80)){
									timeout++;
								}else{
									normal++;
								}
							}
//							dataToDB(gprs,col,deal);  
							ReadMeterLogDao.addReadMeterLogs(readlogid,gprs,metercount,meterdata);
							
							if(meters == 0){
								break;
							}
							middle = 0;
						}
					}
					result.put("success", "true");
				}else{
					//offline
					result.put("success", "false");
					result.put("error", "offline");
				}
			}else{
				//监听错误
				result.put("success", "false");
				result.put("error", "offline error");
			}
		} catch (Exception e) {
			result.put("success", "false");
			result.put("error", e.getMessage());
			e.printStackTrace();
		} finally{
			try {
				if(out!=null){
					out.close();
				}
				if(in!=null){
					in.close();
				}
				if(s!=null){
					s.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			result.put("result", "正常"+normal+";超时"+timeout);
			latch.countDown();
		}
	}
}
