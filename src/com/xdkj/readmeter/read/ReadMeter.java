package com.xdkj.readmeter.read;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
		case 3:
			//EG表  atom集中器
			readEGatom();
			break;
		case 4:
			//D10 下的表
			readD10();
		case 5:
			//188
			read188v2();
			break;
		default:
			break;
		}
	}

	private void readD10() {
		
		//更新readlog  
		ReadLogDao.updateReadLog(readlogid,true,"D10","正常0;异常1");
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
				
				
				at[4] = (byte) Integer.parseInt(meter.getMeterAddr());//Byte.parseByte(meter.getMeterAddr());
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
					result = "正常1;异常0";
				}else{
					result = "正常0;异常1";
				}
			}else{
				result = "正常0;异常1";
			}
			//更新readlog   
			ReadLogDao.updateReadLog(readlogid,finished,reason,result);
		}
	}


	/**
	 * 集中器往下采用青岛海大艺高协议   集中器往上使用类新天协议
	 */
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
			//没有连接到监听程序  
			String result = "正常0;异常1";
			//更新readlog  
			ReadLogDao.updateReadLog(readlogid,true,"连接监听异常",result);
			return;
		}
		

		boolean timeout = false;
		boolean read = false;
		
		//服务器登录指令
		byte[] gprsaddr = StringUtil.string2Byte(gprs.getGprsaddr());
		//~~~~~~~~~~~~~~~~~~~~~~~登录监听服务器
		read = ReadGPRS.loginListener(s, out, in, gprsaddr);
		
		if(!read){
			//集中器不在线
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
			String result = "正常0;异常1";
			//更新readlog  
			ReadLogDao.updateReadLog(readlogid,true,"集中器不在线",result);
			return;
		}
		
		boolean seq_syn = false;
		//~~~~~~~~~~~~~~~~~~~~~~~~~确定集中器的服务器的序列号
		seq_syn = ReadGPRS.synSEQ(s, out, in, seq, gprsaddr);
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
			
			String result = "正常0;异常1";
			//更新readlog  
			ReadLogDao.updateReadLog(readlogid,true,"序列号未同步",result);
			return;
		}
		
		//开始抄表
		//尝试3次抄表指令
		
		int read_good = 0;
		int meterstatus = 1;   //表状态   ==1正常    ！=1 异常
		int ack_timeout_count = 0;  //抄表指令未收到确认计数   指令重发次数
		int rcv_10timeout_count = 0;  //接收数据 10s超时计数
		for(int j = 0;j < 3 && read_good == 0;j++){
			if(read_good == 0){
				seq++;
				seq = (byte) (seq&0x0F);
			}
			
			byte[] framedata = new byte[4];
			framedata[0] = (byte) 0xAA;
			framedata[1] = (byte) (Integer.parseInt(meter.getCollectorAddr()) / 256);
			framedata[2] = (byte) Integer.parseInt(meter.getCollectorAddr());
			framedata[3] = (byte) Integer.parseInt(meter.getMeterAddr());//Byte.parseByte(meter.getMeterAddr());
			
			boolean read_ack = false;  //集中器收到抄表指令
			read_ack = ReadGPRS.sendFrame(s, out, in, seq, gprsaddr, framedata);
			if(!read_ack){
				//没有收到集中器的收到指令确认  3s后重新发送指令
				ack_timeout_count++;
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			
			
			try {
				//等待集中器返回数据  10s
				s.setSoTimeout(15000);
				byte[] deal = new byte[200];
				int middle = 0;
				timeout = false;
				try {
					int slave_seq = 20;  //接收过来的seq 取值范围为0~15  第一次肯定不相同
					while ((count = in.read(data, 0, 100)) > 0) {
						
						for(int k = 0;k < count;k++){
							deal[middle+k] = data[k];
						}
						middle = middle + count;
						//从deal中查找帧  如果没有找到帧  则放弃。
						//首先查找0x68  直到找到0x68为止
						
						int ret = ReadGPRS.checkFrame(deal, middle);
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
							
							if(slave_seq != slave_seq_){
								slave_seq = slave_seq_;
								
								if(deal[18] == (byte) Integer.parseInt(meter.getMeterAddr())){
									//地址相同
									//save the data
									byte[] meterdata = new byte[3];
									meterdata[0] = deal[18];
									meterdata[1] = deal[19];
									meterdata[2] = deal[20];
									meterstatus = ReadMeterLogDao.addReadMeterLog(readlogid,gprs,mid,meterdata);
									read_good = 1;
								}
							}else{
								//这条数据我已经收到过了  do nothing
							}
							//多帧时   为接收下一帧做准备
							middle = 0;
							break;
						}
						
						if(read_good == 1){
							break;
						}
						
					}
				} catch (SocketTimeoutException e) {
					timeout = true;
					rcv_10timeout_count++;
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
//			一次抄单个表完成。
		}
		//~~~~~~~~~~~~~~~~~~~~~~~~~记录抄这个采集器的结果~~~~
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
		String failreason = "";
		if(read_good == 1){
			failreason = "";
			if(meterstatus == 1){
				result = "正常1;异常0";
			}else{
				result = "正常0;异常1";
				failreason = "表状态:"+meterstatus;
			}
		}else{
			failreason = "指令重发:"+ack_timeout_count+";10s超时:"+rcv_10timeout_count;
			result = "正常0;异常1";
		}
		//更新readlog  
		ReadLogDao.updateReadLog(readlogid,true,failreason,result);
	}

	
	private void read188() {
		Socket s = null;
		OutputStream out = null;
		InputStream in = null;
		int count = 0;
		byte[] data = new byte[256];
		byte seq = 0;   //服务器给集中器发送的序列号
		
		
		try {
			s = new Socket(gprs.getIp(),gprs.getPort());
			out = s.getOutputStream();
			in = s.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//如果没有连接成功 
		if(s == null){
			//没有连接到监听程序  
			String result = "正常0;异常1";
			//更新readlog  
			ReadLogDao.updateReadLog(readlogid,true,"连接监听异常",result);
			return;
		}
		
		//服务器登录指令
		byte[] gprsaddr = StringUtil.string2Byte(gprs.getGprsaddr());
		
		//~~~~~~~~~~~~~~~~~~~~~~~登录监听服务器
		boolean timeout = false;
		boolean read = false;
		read = ReadGPRS.loginListener(s, out, in, gprsaddr);
		if(!read){
			//集中器不在线
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
			String result = "正常0;异常1";
			//更新readlog  
			ReadLogDao.updateReadLog(readlogid,true,"集中器不在线",result);
			return;
		}
		
		boolean seq_syn = false;
		//~~~~~~~~~~~~~~~~~~~~~~~~~确定集中器的服务器的序列号
		seq_syn = ReadGPRS.synSEQ(s, out, in, seq, gprsaddr);
		
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
			
			String result = "正常0;异常1";
			//更新readlog  
			ReadLogDao.updateReadLog(readlogid,true,"序列号未同步",result);
			return;
		}
		
		/************************************************************************/
		//开始抄表
		int read_good = 0;
		int meterstatus = 1;   //表状态   ==1正常    ！=1 异常
		int ack_timeout_count = 0;  //抄表指令未收到确认计数   指令重发次数
		int rcv_15timeout_count = 0;  //接收数据 15s超时计数
		for(int i = 0;i < 3 && read_good == 0;i++){
			
			if(read_good == 0){
				seq++;
				seq = (byte) (seq&0x0F);
			}
			
			//the data in the frame
			byte[] meteraddr = StringUtil.string2Byte(meter.getMeterAddr());
			byte[] framedata = new byte[11];
			framedata[0] = 0x10;
			for(int j= 1;j <= 7;j++){
				framedata[j] = meteraddr[6-(j-1)];
			}
			framedata[8] = 0x00;
			framedata[9] = 0x00;
			framedata[10] = 0x01;
			boolean read_ack = false;  //集中器收到抄表指令
			read_ack = ReadGPRS.sendFrame(s, out, in, seq, gprsaddr, framedata);
			
			if(!read_ack){
				//没有收到集中器的收到指令确认  3s后重新发送指令
				ack_timeout_count++;
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			
			try {
				//等待集中器返回数据  15s
				s.setSoTimeout(15000);
				byte[] deal = new byte[256];
				int middle = 0;
				timeout = false;
				
				try {
					int slave_seq = 20;  //接收过来的seq 取值范围为0~15  第一次肯定不相同
					while ((count = in.read(data, 0, 256)) > 0) {
						
						for(int k = 0;k < count;k++){
							deal[middle+k] = data[k];
						}
						middle = middle + count;
						//从deal中查找帧  如果没有找到帧  则放弃。
						//首先查找0x68  直到找到0x68为止
						int ret = ReadGPRS.checkFrame(deal, middle);
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
							if(slave_seq != slave_seq_){
								slave_seq = slave_seq_;
								Frame readdata = new Frame(Arrays.copyOf(deal, middle));
								byte[] meterdata = readdata.getData();
								
								byte state = meterdata[4+3+1+12]; //4~组    3~afn-seq-fn 1~type  12~status前的数据
								if(((byte)(state &0x40) ==(byte)0x40) || ((byte)(state &0x80)==(byte)0x80)){
									meterstatus = 0;
								}else{
									meterstatus = 1;
								}
								ReadMeterLogDao.addReadMeterLog(readlogid,gprs,mid,meterdata);
								read_good = 1;
								
							}else{
								//这条数据我已经收到过了  do nothing
							}
							//多帧时   为接收下一帧做准备
							middle = 0;
							break;
						}
						if(read_good == 1){
							//跳出接收循环
							break;
						}
					}
				} catch (SocketTimeoutException e) {
					timeout = true;
					rcv_15timeout_count++;
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		//~~~~~~~~~~~~~~~~~~~~~~~~~记录抄这个采集器的结果~~~~
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
		String failreason = "";
		if(read_good == 1){
			failreason = "";
			if(meterstatus == 1){
				result = "正常1;异常0";
			}else{
				result = "正常0;异常1";
				failreason = "表状态:"+meterstatus;
			}
		}else{
			failreason = "指令重发:"+ack_timeout_count+";15s超时:"+rcv_15timeout_count;
			result = "正常0;异常1";
		}
		//更新readlog  
		ReadLogDao.updateReadLog(readlogid,true,failreason,result);
		
		
	}
	
	private void read188v2() {
		Socket s = null;
		OutputStream out = null;
		InputStream in = null;
		int count = 0;
		byte[] data = new byte[256];
		byte seq = 0;   //服务器给集中器发送的序列号
		
		
		try {
			s = new Socket(gprs.getIp(),gprs.getPort());
			out = s.getOutputStream();
			in = s.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//如果没有连接成功 
		if(s == null){
			//没有连接到监听程序  
			String result = "正常0;异常1";
			//更新readlog  
			ReadLogDao.updateReadLog(readlogid,true,"连接监听异常",result);
			return;
		}
		
		//服务器登录指令
		byte[] gprsaddr = StringUtil.string2Byte(gprs.getGprsaddr());
		
		//~~~~~~~~~~~~~~~~~~~~~~~登录监听服务器
		boolean timeout = false;
		boolean read = false;
		read = ReadGPRS.loginListener(s, out, in, gprsaddr);
		if(!read){
			//集中器不在线
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
			String result = "正常0;异常1";
			//更新readlog  
			ReadLogDao.updateReadLog(readlogid,true,"集中器不在线",result);
			return;
		}
		
		/************************************************************************/
		//开始抄表
		int read_good = 0;
		int meterstatus = 1;   //表状态   ==1正常    ！=1 异常
		int ack_timeout_count = 0;  //抄表指令未收到确认计数   指令重发次数
		int rcv_15timeout_count = 0;  //接收数据 15s超时计数
		for(int i = 0;i < 3 && read_good == 0;i++){
			
			if(read_good == 0){
				seq++;
				seq = (byte) (seq&0x0F);
			}
			
			//the data in the frame
			byte[] meteraddr = StringUtil.string2Byte(meter.getMeterAddr());
			byte[] cjqaddr = StringUtil.string2Byte(meter.getCollectorAddr());
			byte[] framedata = new byte[13];
			framedata[0] = 0x11;
			for(int j= 0;j < 5;j++){
				framedata[j+1] = cjqaddr[4-j];
			}
			for(int j= 0;j < 7;j++){
				framedata[j+6] = meteraddr[6-j];
			}
			boolean read_ack = false;  //集中器收到抄表指令
			read_ack = ReadGPRS.sendFrame(s, out, in, seq, gprsaddr, framedata);
			
			if(!read_ack){
				//没有收到集中器的收到指令确认  3s后重新发送指令
				ack_timeout_count++;
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			
			try {
				//等待集中器返回数据  15s
				s.setSoTimeout(20000);
				byte[] deal = new byte[256];
				int middle = 0;
				timeout = false;
				
				try {
					int slave_seq = 20;  //接收过来的seq 取值范围为0~15  第一次肯定不相同
					while ((count = in.read(data, 0, 256)) > 0) {
						
						for(int k = 0;k < count;k++){
							deal[middle+k] = data[k];
						}
						middle = middle + count;
						//从deal中查找帧  如果没有找到帧  则放弃。
						//首先查找0x68  直到找到0x68为止
						int ret = ReadGPRS.checkFrame(deal, middle);
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
							if(slave_seq != slave_seq_){
								slave_seq = slave_seq_;
								Frame readdata = new Frame(Arrays.copyOf(deal, middle));
								byte[] meterdata = readdata.getData();
								
								byte state = meterdata[4+3+1+12]; //4~组    3~afn-seq-fn 1~type  12~status前的数据
								if(((byte)(state &0x40) ==(byte)0x40) || ((byte)(state &0x80)==(byte)0x80)){
									meterstatus = 0;
								}else{
									meterstatus = 1;
								}
								ReadMeterLogDao.addReadMeterLog(readlogid,gprs,mid,meterdata);
								read_good = 1;
								
							}else{
								//这条数据我已经收到过了  do nothing
							}
							//多帧时   为接收下一帧做准备
							middle = 0;
							break;
						}
						if(read_good == 1){
							//跳出接收循环
							break;
						}
					}
				} catch (SocketTimeoutException e) {
					timeout = true;
					rcv_15timeout_count++;
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		//~~~~~~~~~~~~~~~~~~~~~~~~~记录抄这个采集器的结果~~~~
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
		String failreason = "";
		if(read_good == 1){
			failreason = "";
			if(meterstatus == 1){
				result = "正常1;异常0";
			}else{
				result = "正常0;异常1";
				failreason = "表状态:"+meterstatus;
			}
		}else{
			failreason = "指令重发:"+ack_timeout_count+";15s超时:"+rcv_15timeout_count;
			result = "正常0;异常1";
		}
		//更新readlog  
		ReadLogDao.updateReadLog(readlogid,true,failreason,result);
		
		
	}
	
	
	public static void main(String[] args) {
		new ReadMeter(1, 1).start();
	}
}
