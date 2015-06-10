package com.xdkj.readmeter.read;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

import com.xdkj.readmeter.dao.GPRSDao;
import com.xdkj.readmeter.dao.MeterDao;
import com.xdkj.readmeter.dao.ReadLogDao;
import com.xdkj.readmeter.dao.ReadMeterLogDao;
import com.xdkj.readmeter.obj.Frame;
import com.xdkj.readmeter.obj.GPRS;
import com.xdkj.readmeter.obj.Meter;
import com.xdkj.readmeter.util.StringUtil;

public class ReadMeter extends Thread{
	
	private int mid;
	private int readlogid;
	private GPRS gprs;
	private Meter meter;
	public ReadMeter(int mid, int readlogid) {
		super();
		this.mid = mid;
		this.readlogid = readlogid;
	}

	@Override
	public void run() {
		//根据mid  获取集中器对应的信息
		gprs = GPRSDao.getGPRSbyMID(mid);
		meter = MeterDao.getMeterbyID(mid);
		
//		System.out.println(gprs.getGprsprotocol()+"~~~");
		
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
		byte[] data = new byte[10];
		String res = "";
		boolean finished = false;  //抄表是否成功  默认不成功
		String reason = "";  //抄表失败原因
		int normal = 0;  //抄表是否正常  
		
		try {
			s = new Socket(gprs.getIp(),gprs.getPort());
			s.setSoTimeout(10*1000);  //10s
			out = s.getOutputStream();
			in = s.getInputStream();
			
			out.write(("ATDT"+gprs.getGprsaddr()).getBytes());
			
			while((count = in.read(data,0,10)) > 0){
				res = new String(data,0,count);
				break;
			}
			
			if(res.equalsIgnoreCase("GPRS")){
				byte[] at = new byte[10];
				at[0] = 0x0E;
				at[1] = 0x0D;
				at[2] = 0x0C;
				at[3] = 0x02;
				at[4] = Byte.parseByte(meter.getMeterAddr());
				at[5] = (byte) (Integer.parseInt(meter.getCollectorAddr()) / 256);
				at[6] = (byte) Integer.parseInt(meter.getCollectorAddr());
				at[7] = (byte) 0xFF;
				at[8] = (byte) 0xFF;
				at[9] = (byte) (at[0] ^ at[1] ^ at[2] ^ at[3] ^ at[4] ^ at[5] ^ at[6] ^ at[7] ^ at[8]);
				
				out.write(at);
				while ((count = in.read(data, 0, 10)) > 0) {
					if(count == 9 && (new String(data,0,9)).equalsIgnoreCase("BREAKDOWN")){
//						ReadLogDao.insertData(meter.getGprs().getPid(), meter.getColaddr(), meter.getMeteraddr(), (short)4, -1, "breakdown");
						//抄表记录 采集器breakdown
						ReadMeterLogDao.addBreakdown(readlogid,gprs,mid);
					}else{
//						dataToDB(meter.getGprs(),new Acquisitor(meter.getColaddr(), 1),data);
						//抄表记录 插入最新抄上来的表的结果
						ReadMeterLogDao.addReadMeterLog(readlogid,gprs,mid,data);
						normal = 1;
					}
					finished = true;
					break;// got the data (the num or BREAKDOWN)
				}
			}else{
				//抄表失败 res
				reason = res;
			}
			
		} catch (Exception e) {
			reason = e.getMessage();
			e.printStackTrace();
		}finally{
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
			String result = "";
			if(finished){
				if(normal == 1){
					result = "正常1;异常0;超时0";
				}else{
					result = "正常0；异常0;超时1";
				}
			}else{
				result = "正常0；异常1;超时0";
			}
			//更新readlog   
			ReadLogDao.updateReadLog(readlogid,finished,reason,result);
		}
	}

	private void read188() {
		Socket s = null;
		OutputStream out = null;
		InputStream in = null;
		int count = 0;
		byte[] data = new byte[1024];
		boolean finished = false;  //抄表是否成功  默认不成功
		String reason = "";  //抄表失败原因
		
		try {
			s = new Socket(gprs.getIp(),gprs.getPort());
			s.setSoTimeout(30*1000);  //30s
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
					byte[] meteraddr = StringUtil.string2Byte(meter.getMeterAddr());
					//the data in the frame
					byte[] framedata = new byte[11];
					framedata[0] = 0x10;
					for(int i= 1;i <= 7;i++){
						framedata[i] = meteraddr[6-(i-1)];
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
//							dataToDB(gprs,col,deal);  
							ReadMeterLogDao.addReadMeterLog(readlogid,gprs,mid,meterdata);
							finished = true;
							break;
						}
					}
				}else{
					//offline
					reason = "offline";
				}
			}else{
				//监听错误
				reason = "listener error";
			}
		} catch (Exception e) {
			reason = e.getMessage();
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
			String result = "";
			if(finished){
				result = "正常1;异常0;超时0";
			}else{
				result = "正常0；异常1;超时0";
			}
			ReadLogDao.updateReadLog(readlogid,finished,reason,result);
		}
	}
	
	public static void main(String[] args) {
		new ReadMeter(1, 1).start();
	}
}
