package com.xdkj.readmeter.read;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;


import com.xdkj.readmeter.dao.ValveConfLogDao;
import com.xdkj.readmeter.dao.ValveLogDao;
import com.xdkj.readmeter.obj.Frame;
import com.xdkj.readmeter.obj.ValveConfLog;
import com.xdkj.readmeter.obj.Valvelog;
import com.xdkj.readmeter.util.StringUtil;

public class ValveControl extends Thread{

	private Valvelog valvelog;
	private int normal = 0;
	private int error = 0;
	
	public ValveControl(Valvelog valvelog) {
		super();
		this.valvelog = valvelog;
	}


	@Override
	public void run() {
		List<ValveConfLog> list = ValveConfLogDao.getValveConfLog(valvelog.getPid());
		ValveConfLog valveConfLog = null;
		for(int i = 0;null != list && i < list.size();i++){
			valveConfLog = list.get(i);
			valveControl(valveConfLog);
		}
		ValveLogDao.updateValveLog(valvelog,normal,error);
	}


	private void valveControl(ValveConfLog valveConfLog) {
		Socket s = null;
		OutputStream out = null;
		InputStream in = null;
		int count = 0;
		byte[] data = new byte[1024];
		boolean finished = false;  //阀门开关是否成功  默认不成功
		String reason = "";  //开关失败原因
		byte seq = 0;   //服务器给集中器发送的序列号
		
		
		try {
			s = new Socket(valveConfLog.getIp(),valveConfLog.getPort());
			out = s.getOutputStream();
			in = s.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//如果没有连接成功 
		if(s == null){
			//没有连接到监听程序  
			error++;
			reason = "连接监听异常";
			ValveConfLogDao.updateValveConfLog(valveConfLog,finished,reason);
			return;
			
		}
		
		//~~~~~~~~~~~~~~~~~~~~~~~登录监听服务器
		byte[] gprsaddr = StringUtil.string2Byte(valveConfLog.getGprsaddr());
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
			error++;
			reason = "集中器不在线";
			ValveConfLogDao.updateValveConfLog(valveConfLog,finished,reason);
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
			error++;
			reason = "序列号未同步";
			ValveConfLogDao.updateValveConfLog(valveConfLog,finished,reason);
			return;
		}
		
		
		/**********************************阀门控制**************************************/
		int ack_timeout_count = 0;  //抄表指令未收到确认计数   指令重发次数
		int rcv_30timeout_count = 0;  //接收数据30s超时计数
		for(int i = 0;i < 3 && !finished;i++){
			
			if(!finished){
				seq++;
				seq = (byte) (seq&0x0F);
			}
			
			//the data in the frame
			byte[] meteraddr = StringUtil.string2Byte(valveConfLog.getMeteraddr());
			byte[] framedata = new byte[10];
			framedata[0] = 0x10;
			for(int k= 1;k <= 7;k++){
				framedata[k] = meteraddr[6-(k-1)];
			}
			framedata[8] = 0x00;
			framedata[9] = 0x00;
			byte action = 0x03;  //默认开阀
			if(valveConfLog.getSwitchaction() == 0){
				action = 0x02;  //关阀
			}
			
			boolean read_ack = false;  //集中器收到控制指令
			read_ack = sendControlFrame(s, out, in, seq, action, gprsaddr, framedata);
			
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
				//等待集中器返回数据  30s
				s.setSoTimeout(30000);
				byte[] deal = new byte[256];
				int middle = 0;
				
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
								if(readdata.getFn() == 0x01){
									//集中器收到发出去的指令
									normal++;
									finished = true;
								}
							}else{
								//这条数据我已经收到过了  do nothing
							}
							//多帧时   为接收下一帧做准备
							middle = 0;
							break;
						}
						if(finished){
							//跳出接收循环
							break;
						}
					}
				} catch (SocketTimeoutException e) {
					rcv_30timeout_count++;
					e.printStackTrace();
				}
			} catch (Exception e) {
				reason = e.getMessage();
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
		if(!finished){
			reason = "指令重发："+ack_timeout_count+"30s超时："+rcv_30timeout_count;
			error++;
		}else{
			normal++;
		}
		ValveConfLogDao.updateValveConfLog(valveConfLog,finished,reason);
		
	}
	
	/**
	 * 给集中器发送控制指令   并确认其是否收到
	 * @param s
	 * @param out
	 * @param in
	 * @param seq
	 * @param action
	 * @param gprsaddr
	 * @param framedata
	 * @return
	 */
	public static boolean sendControlFrame(Socket s, OutputStream out, InputStream in, byte seq,byte action, byte[] gprsaddr, byte[] framedata) {
		boolean timeout = false;
		boolean read_ack = false;
		byte[] data = new byte[100];
		try {
			
//			Frame readgprs = new Frame(framedata.length, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND), 
//					Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR|seq), 
//					(byte)0x04, gprsaddr, framedata);
			Frame readgprs = new Frame(framedata.length, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_FIRST), 
					Frame.AFN_CONTROL, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR|seq), 
					action, gprsaddr, framedata);
			
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
	
	public static void main(String[] args) {
		Valvelog log = new Valvelog();
		log.setPid(1);
		new ValveControl(log).start();
	}
}
