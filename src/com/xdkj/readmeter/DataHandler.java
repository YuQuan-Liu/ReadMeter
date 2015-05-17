package com.xdkj.readmeter;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xdkj.readmeter.read.DealAction;


public class DataHandler extends IoHandlerAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger(DataHandler.class);
	
	@Override
	public void exceptionCaught(IoSession session,Throwable cause){
		session.close(true);
		logger.error("MINA错误", cause);
	}
	
	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {
		
		if(session.getIdleCount(status) == 1){
			session.close(true);
		}
		
	}
	
	@Override
	public void messageReceived(IoSession session,Object message){
		String action = (String) message;
		logger.info(session.getRemoteAddress().toString()+action);
		try {
			JSONObject jo = new JSONObject(action);
			String function = jo.getString("function");
			int pid = jo.getInt("pid");
			if((function.equalsIgnoreCase("read") || function.equalsIgnoreCase("valve")) && pid > 0){
				session.write("{\"function\":\""+function+"\",\"pid\":\""+pid+"\",\"result\":\"success\"}");
				//将指令添加到处理的线程池
				DealAction.addAction(function, pid);
			}else{
				session.write("{\"function\":\""+function+"\",\"pid\":\""+pid+"\",\"result\":\"fail\"}");
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void sessionOpened(IoSession session) throws Exception{
		logger.info(session.getRemoteAddress().toString());
	}
	
	@Override
	public void sessionClosed(IoSession session) throws Exception{
		
	}
}
