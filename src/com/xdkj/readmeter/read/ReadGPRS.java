package com.xdkj.readmeter.read;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;


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
		case 3:
			//EGatom
			readEGatom();
			break;
		case 4:
			//D10
			readD10();
		default:
			break;
		}
	}
	
	private void readD10() {
		//D10 定时上报数据  不能主动去抄
		result.put("success", "true");
		result.put("error", "");
		int meters = MeterDao.getMeterCountByGID(gprs.getPid());
		result.put("result", "正常"+meters+";");
		latch.countDown();
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
		
		String cjqtimerout = "";
		
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
					
					if(col.getColAddr() == 0){
						continue;
					}
					
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
					//delay 2s
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					out.write(at);
					
					try {
						while ((count = in.read(data, 0, 1024)) > 0) {
							
							for(int j = 0;j<count;j++){
								deal[middle+j] = data[j];
							}

							middle = middle + count;
							
							int meters = (middle)/10;
//							System.out.println("cjqmeters"+cjqmeters+"meters:"+meters);
							
							if(count == 9 && (new String(data,0,count)).contains("BREAKDOWN")){
//								breakdown(gprs,col); 
								ReadMeterLogDao.addBreakdown(readlogid,gprs,col);
								String breakdown = result.get("breakdown");
								if(null == breakdown){
									breakdown = col.getColAddr() +",";
								}else{
									breakdown = breakdown + col.getColAddr() +",";
								}
								result.put("breakdown", breakdown);
								timeout += cjqmeters;
								middle = 0;
								break;
							}
							if(cjqmeters == meters){
//								dataToDB(gprs,col,deal);  
								int errorcount = ReadMeterLogDao.addReadMeterLogs(readlogid,gprs,col,deal);
//								normal += cjqmeters;
								timeout += errorcount;
								normal = normal + cjqmeters - errorcount;
								middle = 0;
								break;
							}
						}
					} catch(SocketTimeoutException se){
						//*号采集器timer out.
						cjqtimerout = cjqtimerout + col.getColAddr()+" ";
						middle = 0;
					}
					
				}
				result.put("success", "true");
				result.put("error", "");
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

			if(cjqtimerout.equals("")){
				result.put("result", "正常"+normal+";异常"+timeout);
			}else{
				result.put("result", "正常"+normal+";异常"+timeout+";采集器超时:"+cjqtimerout);
			}
			
			latch.countDown();
		}
		
	}
	
	private void readEGatom() {
		Socket s = null;
		OutputStream out = null;
		InputStream in = null;
		int count = 0;
		byte[] data = new byte[128];
		byte seq = 0;   //服务器给集中器发送的序列号
		//连接监听程序
		try {
			s = new Socket(gprs.getIp(),gprs.getPort());
			out = s.getOutputStream();
			in = s.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//如果没有连接成功 
		if(s == null){
			result.put("success", "false");
			result.put("error", "连接监听异常");
			result.put("result", "失败");
			latch.countDown();
			return;
		}
		
		boolean timeout = false;
		boolean read = false;
		
		//服务器登录指令
		byte[] gprsaddr = StringUtil.string2Byte(gprs.getGprsaddr());
		//登录监听服务器
		read = loginListener(s, out, in, gprsaddr);
		
		if(!read){
			//集中器不在线
			//记录  return; 
			try {
				if(in != null){
					in.close();
				}
				if(out != null){
					out.close();
				}
				if(s != null){
					s.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			result.put("success", "false");
			result.put("error", "集中器不在线");
			result.put("result", "失败");
			latch.countDown();
			return;
		}
		
		boolean seq_syn = false;
		//确定集中器的服务器的序列号
		seq_syn = synSEQ(s, out, in, seq, gprsaddr);
		if(!seq_syn){
			//序列号未同步
			try {
				if(in != null){
					in.close();
				}
				if(out != null){
					out.close();
				}
				if(s != null){
					s.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			result.put("success", "false");
			result.put("error", "序列号未同步");
			result.put("result", "失败");
			latch.countDown();
			return;
		}
		
		//~~~~~~~~~~~~~~~~~~~~~~~~~~~~开始挨个抄采集器
		List<Collector> list = MeterDao.getCollectorsByGID(gprs.getPid());
		Collector col = null;
		int normal_count = 0;  //正常表计数
		int error_count = 0;   //异常表计数
		String cjqtimerout = "";  //超时的采集器的地址
		for (int i = 0; null!=list && i < list.size(); i++) {
			col = list.get(i);
			int read_good = 0;
			int ack_timeout_count = 0;  //抄表指令未收到确认计数   指令重发次数
			int rcv_100timeout_count = 0;  //接收数据 100s超时计数
			boolean cjq_timeout = false;  //集中器返回采集器超时
			
			int cjqmeters = col.getMeterNums();
			
			for(int j = 0;j < 3 && read_good == 0;j++){
				if(read_good == 0){
					seq++;
					seq = (byte) (seq&0x0F);
				}
				cjq_timeout = false;
				
				//~~~~~~~~~~~~~~~~~~~~~~~~~给集中器发送抄表指令
				//the data in the frame
				byte[] framedata = new byte[4];
				framedata[0] = 0x00;
				framedata[1] = (byte) (col.getColAddr() / 256);
				framedata[2] = (byte) col.getColAddr();
				framedata[3] = (byte) cjqmeters;
				
				boolean read_ack = false;  //集中器收到抄表指令
				read_ack = sendFrame(s, out, in, seq, gprsaddr, framedata);
				if(!read_ack){
					//没有收到集中器的收到指令确认
					ack_timeout_count++;
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					continue;
				}
				
				/***************************等待集中器返回数据********************************/
				try {
					s.setSoTimeout(120000);
					byte[] deal = new byte[200];
					int middle = 0;
					timeout = false;
					byte[] meterdata = new byte[600];
					try {
						int meter_rcv = 0;  //接收到的表的数据
						int slave_seq = 20;  //接收过来的seq 取值范围为0~15  第一次肯定不相同
						while ((count = in.read(data, 0, 100)) > 0) {
							
							for(int k = 0;k < count;k++){
								deal[middle+k] = data[k];
							}
							middle = middle + count;
							//从deal中查找帧  如果没有找到帧  则放弃。
							//首先查找0x68  直到找到0x68为止
							int ret = checkFrame(deal, middle);
							switch(ret){
							case 0:
								//数据不够  继续接收
								break;
							case -1:
								//这一帧错误
								middle = 0;  //重新开始接收
								break;
							case 1:
								//这一帧正确处理
								int slave_seq_ = deal[13] & 0x0F;
								int data_len = (deal[1]&0xFF) | ((deal[2]&0xFF)<<8);
								data_len = data_len >> 2;
								
								if(slave_seq != slave_seq_){
									slave_seq = slave_seq_;
									int meternum_ = (data_len-12)/3;
									
									if((byte)0xFF == deal[17]){
										//采集器超时~~~~返回指令0xFF
										cjq_timeout = true;
									}else{
										//处理Frame 
										//正常的数据帧  
										for(int x = 0;x < meternum_;x++){
											meterdata[meter_rcv*3+x*3] = deal[18+3*x];
											meterdata[meter_rcv*3+x*3+1] = deal[18+3*x+1];
											meterdata[meter_rcv*3+x*3+2] = deal[18+3*x+2];
										}
									}
									//放在往meterdata copy前不可以  导致数组错位
									meter_rcv += meternum_;
								}else{
									//这条数据我已经收到过了  do nothing
								}
								//多帧时   为接收下一帧做准备
								middle = 0;
								break;
							}
							
							if(read_good == 0 && cjq_timeout){
								//采集器超时  跳出接收循环 
								read_good = 1;
								ReadMeterLogDao.addBreakdown(readlogid,gprs,col);
								error_count += cjqmeters;
								cjqtimerout = cjqtimerout + col.getColAddr() +" ";
								break;
							}
							
							if(read_good == 0 && meter_rcv == cjqmeters){
								//抄表完成   跳出接收循环
								read_good = 1;
								int errorcount = ReadMeterLogDao.addReadMeterLogsAtom(readlogid,gprs,col,meterdata);
								normal_count = normal_count + cjqmeters - errorcount;
								error_count += errorcount;
								break;
							}
						}
					} catch (SocketTimeoutException e) {
						if(read_good == 0){
							timeout = true;
							rcv_100timeout_count++;
							e.printStackTrace();
						}
					}
					
					if(timeout){
						//本次接收指令超时。   接收抄表数据100s超时
//						break;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				
//				采集器的一次抄表完成。  
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			//~~~~~~~~~~~~~~~~~~~~~~~~~记录抄这个采集器的结果~~~~
			if(read_good == 0){
				//3次都超时了  需要记录
				ReadMeterLogDao.addBreakdown(readlogid,gprs,col);
				error_count += cjqmeters;
				cjqtimerout = cjqtimerout + col.getColAddr() +" ";
			}
		}
		
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
		if(cjqtimerout.equals("")){
			result.put("result", "正常"+normal_count+";异常"+error_count);
		}else{
			result.put("result", "正常"+normal_count+";异常"+error_count+";采集器超时:"+cjqtimerout);
		}
		result.put("success", "true");
		result.put("error", "");
		latch.countDown();
		
	}

	
	
	private void read188() {
		Socket s = null;
		OutputStream out = null;
		InputStream in = null;
		int count = 0;
		byte[] data = new byte[1024];
		byte seq = 0;   //服务器给集中器发送的序列号
		//连接监听程序
		try {
			s = new Socket(gprs.getIp(),gprs.getPort());
			out = s.getOutputStream();
			in = s.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//如果没有连接成功 
		if(s == null){
			result.put("success", "false");
			result.put("error", "连接监听异常");
			result.put("result", "失败");
			latch.countDown();
			return;
		}
		
		//服务器登录指令
		byte[] gprsaddr = StringUtil.string2Byte(gprs.getGprsaddr());

		boolean read = false;
		read = loginListener(s, out, in, gprsaddr);
		
		if(!read){
			//集中器不在线
			//记录  return; 
			try {
				if(in != null){
					in.close();
				}
				if(out != null){
					out.close();
				}
				if(s != null){
					s.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			result.put("success", "false");
			result.put("error", "集中器不在线");
			result.put("result", "失败");
			latch.countDown();
			return;
		}
		
		boolean seq_syn = false;
		//确定集中器的服务器的序列号
		seq_syn = synSEQ(s, out, in, seq, gprsaddr);
		
		if(!seq_syn){
			//序列号未同步
			try {
				if(in != null){
					in.close();
				}
				if(out != null){
					out.close();
				}
				if(s != null){
					s.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			result.put("success", "false");
			result.put("error", "序列号未同步");
			result.put("result", "失败");
			latch.countDown();
			return;
		}
		
		/************************************************************************/
		//开始抄表
		int read_good = 0;
		int normal = 0;
		int timeout_count = 0;
		int ack_timeout_count = 0;  //抄表指令未收到确认计数   指令重发次数
		int rcv_6mtimeout_count = 0;  //接收数据 6m超时计数
		boolean timeout = false;
		
		for(int j = 0;j < 3 && read_good == 0;j++){
			if(read_good == 0){
				seq++;
				seq = (byte) (seq&0x0F);
			}
			normal = 0;
			timeout_count = 0;
			
			//~~~~~~~~~~~~~~~~~~~~~~~~~给集中器发送抄表指令
			//the data in the frame
			byte[] framedata = new byte[11];
			framedata[0] = 0x10;
			for(int i= 1;i <= 7;i++){
				framedata[i] = (byte) 0xFF;
			}
			framedata[8] = 0x00;
			framedata[9] = 0x00;
			framedata[10] = 0x01;
			
			boolean read_ack = false;  //集中器收到抄表指令
			read_ack = sendFrame(s, out, in, seq, gprsaddr, framedata);
			
			if(!read_ack){
				//没有收到集中器的收到指令确认
				ack_timeout_count++;
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			
			
			int meters = MeterDao.getMeterCountByGID(gprs.getPid());
			try {
				//等待集中器返回数据
				s.setSoTimeout(120000);   //2min  抄集中器全部表时   会每隔9s发送一个Fake帧
				timeout = false;
				try {
					byte[] deal = new byte[1024];
					byte[] middle_data = new byte[256];
					int middle = 0;
					int slave_seq = 20;  //接收过来的seq 取值范围为0~15  第一次肯定不相同
					while ((count = in.read(data, 0, 256)) > 0) {
						
						for(int k = 0;k < count;k++){
							deal[middle+k] = data[k];
						}
						middle = middle + count;
						//从deal中查找帧  如果没有找到帧  则放弃。
						//首先查找0x68  直到找到0x68为止
						int ret = checkFrame(deal, middle);
						switch(ret){
						case 0:
							//数据不够  继续接收
							break;
						case -1:
							//这一帧错误
							middle = 0;  //重新开始接收
							break;
						case 1:
							//这一帧正确处理
							//get the frame len
							int framelen = (deal[1]&0xFF) | ((deal[2]&0xFF)<<8);
							framelen = framelen >> 2;
							
							if(deal[12] == 0x0B){  //AFN
								//数据帧
								int slave_seq_ = deal[13] & 0x0F;
								if(slave_seq != slave_seq_){
									slave_seq = slave_seq_;
									Frame readdata = new Frame(Arrays.copyOf(deal, framelen+8));
									byte[] meterdata = readdata.getData();
									int metercount = (meterdata.length-1-3-4)/14;
									meters -= metercount;
									
									//判断表的状态  看是否超时  
									for(int i = 0;i < metercount;i++){
										byte state = meterdata[i*14+4+3+1+12];
										if(((state &0x40) ==0x40) || ((state &0x80)==0x80)){
											timeout_count++;
										}else{
											normal++;
										}
									}
									ReadMeterLogDao.addReadMeterLogs(readlogid,gprs,metercount,meterdata);
									
								}else{
									//这条数据我已经收到过了  do nothing
								}
							}else{
								//抄全部表时   上传的Fake帧   AFN==0x0F
								//donothing ...  防止socket 2min超时
							}
							//多帧时   为接收下一帧做准备
							middle = middle - framelen-8;
							if(middle != 0){
								for(int m = 0;m < middle;m++){
									middle_data[m]=deal[framelen+8+m];
								}
								for(int m = 0;m < middle;m++){
									deal[m]=middle_data[m];
								}
							}
							break;
						}
						if(meters <= 0){
							//跳出接收循环
							read_good = 1;
							break;
						}
					}
				} catch (SocketTimeoutException e) {
					if(read_good == 0){
						timeout = true;
						rcv_6mtimeout_count++;
						e.printStackTrace();
					}
				}
				
				if(timeout){
					//本次接收指令超时。   接收抄表数据6min超时
//					timeout_count = meters;
//					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
		
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
		result.put("result", "正常"+normal+";异常"+timeout_count);
		result.put("success", "true");
		if(timeout){
			result.put("error", "接收数据超时");
		}else{
			result.put("error", "");
		}
		
		latch.countDown();
		
	}

	/**
	 * 检查从socket中接收到的数据    是否是一帧 
	 * @param deal
	 * @param middle  从socket中接收到的byte数
	 * @return 检查结果  -1~放弃   0~数据不够  1~帧正确
	 */
	public static int checkFrame(byte[] deal, int middle) {
		int frame_len = 0;  //帧的长度
		int ret = 0;   //检查结果  -1~放弃   0~数据不够  1~帧正确
		int header = 0;
		int data_len = 0;  //帧中数据长度
		
		if(header == 0){
			if(middle > 5){
				if(deal[0] == 0x68 && deal[5] == 0x68){
					if(deal[1] == deal[3] && deal[2] == deal[4]){
						data_len = (deal[1]&0xFF) | ((deal[2]&0xFF)<<8);
						data_len = data_len >> 2;
						header = 1;
					}
				}
				if(header == 0){
					//give up the data
					ret = -1;
				}
			}
		}
		if(header == 1){
			if(middle >= (data_len + 8)){
				frame_len = data_len+8;
				
				byte cs = 0;
				for(int k = 6;k < frame_len-2;k++){
					cs += deal[k];
				}
				if(cs == deal[frame_len-2] && deal[frame_len-1] == 0x16){
					//这一帧OK
					ret = 1;
				}else{
					//这一帧有错误  放弃
					ret = -1;
				}
			}else{
				//收到的数据还不够
				ret =  0;
			}
		}
		return ret;
	}

	/**
	 * 给集中器发送抄表指令   并确认其是否收到
	 * @param s
	 * @param out
	 * @param in
	 * @param seq
	 * @param gprsaddr
	 * @param framedata
	 * @return
	 */
	public static boolean sendFrame(Socket s, OutputStream out, InputStream in, byte seq, byte[] gprsaddr, byte[] framedata) {
		boolean timeout = false;
		boolean read_ack = false;
		byte[] data = new byte[100];
		try {
			
			Frame readgprs = new Frame(framedata.length, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND), 
					Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR|seq), 
					(byte)0x04, gprsaddr, framedata);
			
			out.write(readgprs.getFrame());
			//等待集中器收到的回应
			s.setSoTimeout(10000);
			try {
				while((in.read(data, 0, 100)) > 0){
					break;
				}
			} catch (SocketTimeoutException e) {
				timeout = true;
				e.printStackTrace();
			}
			
			if(!timeout){
				//判断接收到的集中器的回应
				if(Frame.checkFrame(Arrays.copyOf(data, 17))){
					Frame ack_result = new Frame(Arrays.copyOf(data, 17));
					
					if(ack_result.getFn() == 0x01){
						//集中器收到发出去的指令
						read_ack = true;
					}
				}
			}
		} catch (Exception e2) {
			e2.printStackTrace();
		}
		return read_ack;
	}
	
	/**
	 * 确定集中器的服务器的序列号
	 * @param s
	 * @param out
	 * @param in
	 * @param seq
	 * @param gprsaddr
	 * @return
	 */
	public static boolean synSEQ(Socket s, OutputStream out, InputStream in, byte seq, byte[] gprsaddr) {
		boolean timeout = false;
		boolean seq_syn = false;
		byte[] data = new byte[100];
		//~~~~~~~~~~~~~~~~~~~~~~~~~确定集中器的服务器的序列号
		for(int i = 0;i < 3 && !seq_syn;i++){
			byte[] framedata = new byte[0];
			
			Frame syn = new Frame(0, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND), 
					Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR | seq), 
					(byte)0x05, gprsaddr, framedata);
			try {
				out.write(syn.getFrame());
				//6s内接收服务器的返回
				s.setSoTimeout(10000);
				try {
					while((in.read(data, 0, 100)) > 0){
						break;
					}
				} catch (SocketTimeoutException e) {
					timeout = true;
					e.printStackTrace();
				}
			} catch (Exception e2) {
				e2.printStackTrace();
			}
			
			if(!timeout){
				//6s内收到监听返回  判断是否是ack
				if(Frame.checkFrame(Arrays.copyOf(data, 17))){
					Frame ack = new Frame(Arrays.copyOf(data, 17));
					if(ack.getFn() == 0x01){
						//序列号同步
						seq_syn = true;
					}
				}
			}
		}
		return seq_syn;
	}

	/**
	 * 登录监听服务器
	 * @param s
	 * @param out
	 * @param in
	 * @param gprsaddr
	 * @return
	 */
	public static boolean loginListener(Socket s, OutputStream out, InputStream in, byte[] gprsaddr) {
		
		byte[] data = new byte[100];
		boolean timeout = false;
		boolean read = false;
		
		Frame login = new Frame(0, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_LINE), 
				Frame.AFN_LOGIN, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR), 
				(byte)0x01, gprsaddr, new byte[0]);
		
		//~~~~~~~~~~~~~~~~~~~~~~~登录监听服务器
		try {
			out.write(login.getFrame());
			//6s内接收服务器的返回
			s.setSoTimeout(6000);
			try {
				while((in.read(data, 0, 100)) > 0){
					break;
				}
			} catch (SocketTimeoutException e) {
				timeout = true;
				e.printStackTrace();
			}
			
			if(!timeout){
				//6s内收到监听返回  判断是否是ack
				if(Frame.checkFrame(Arrays.copyOf(data, 17))){
					Frame login_result = new Frame(Arrays.copyOf(data, 17));
					if(login_result.getFn() == 0x01){
						//online  集中器在线
						read = true;
					}
				}
			}
		} catch (Exception e2) {
			e2.printStackTrace();
		}
		return read;
	}

}
