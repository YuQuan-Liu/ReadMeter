package com.xdkj.readmeter.read;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.xdkj.readmeter.dao.WarnDao;
import com.xdkj.readmeter.obj.CustomerWarn;

public class WarnSender extends Thread {

	private List<CustomerWarn> list;
	
	public WarnSender(List<CustomerWarn> list) {
		super();
		this.list = list;
	}



	@Override
	public void run() {
		CustomerWarn warn = null;
		for(int i = 0;list != null && i < list.size();i++){
			warn = list.get(i);
			boolean done = sendSMS(warn);
			WarnDao.addWarnLog(warn,done);
		}
	}
	
	
	private boolean sendSMS(CustomerWarn warn) {
		// message
		boolean done = false;
		Map<String, String> para = new HashMap<String, String>();

		/**
		 * 目标手机号码，多个以“,”分隔，一次性调用最多100个号码，示例：139********,138********
		 */
		para.put("mob", warn.getCustomerMobile());
		
		/**
		 * 微米账号的接口UID
		 */
		para.put("uid", "Hnq9MjyE1pBf");

		/**
		 * 微米账号的接口密码
		 */
		para.put("pas", "qg4nwa7k");

		/**
		 * 接口返回类型：json、xml、txt。默认值为txt
		 */
		para.put("type", "json");

		/**
		 * 短信内容。必须设置好短信签名，签名规范： <br>
		 * 1、短信内容一定要带签名，签名放在短信内容的最前面；<br>
		 * 2、签名格式：【***】，签名内容为三个汉字以上（包括三个）；<br>
		 * 3、短信内容不允许双签名，即短信内容里只有一个“【】”
		 * 
		 */
		para.put("con", "【"+warn.getCompanyName()+"】尊敬的"+warn.getCustomerName()+"用户您好，您的水费余额为"+warn.getCustomerBalance().doubleValue()+",请您及时交费。谢谢合作。");
		
		JSONObject jo = null;
		try {
			jo = new JSONObject(HttpClientHelper.convertStreamToString(
					HttpClientHelper.get("http://api.weimi.cc/2/sms/send.html",
							para), "UTF-8"));
			if(jo.get("code").toString().equals("0")){
				done = true;
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		return done;
	}
}
