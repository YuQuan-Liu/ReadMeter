package com.xdkj.readmeter.read;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
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
		
		try {
			s = new Socket(valveConfLog.getIp(),valveConfLog.getPort());
			s.setSoTimeout(30*1000);  //30s
			out = s.getOutputStream();
			in = s.getInputStream();
			byte[] gprsaddr = StringUtil.string2Byte(valveConfLog.getGprsaddr());
			
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
					byte[] meteraddr = StringUtil.string2Byte(valveConfLog.getMeteraddr());
					//the data in the frame
					byte[] framedata = new byte[10];
					framedata[0] = 0x10;
					for(int i= 1;i <= 7;i++){
						framedata[i] = meteraddr[i-1];
					}
					framedata[8] = 0x00;
					framedata[9] = 0x00;
					byte action = 0x03;  //默认开阀
					if(valveConfLog.getSwitchaction() == 0){
						action = 0x02;  //关阀
					}
					Frame readgprs = new Frame(10, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_FIRST), 
							Frame.AFN_CONTROL, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR), 
							action, gprsaddr, framedata);
					
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
							//return ack  nack
							if(readdata.getFn() == 0x01){
								//ack
								normal++;
								finished = true;
							}else{
								//nack 异常
								error++;
								reason = "nack";
							}
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
			if(!finished){
				error++;
			}
//			dataToDB(gprs,col,deal);  
			ValveConfLogDao.updateValveConfLog(valveConfLog,finished,reason);
		}
	}
	
	public static void main(String[] args) {
		Valvelog log = new Valvelog();
		log.setPid(1);
		new ValveControl(log).start();
	}
}
