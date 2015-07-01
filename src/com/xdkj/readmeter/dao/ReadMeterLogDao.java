package com.xdkj.readmeter.dao;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.xdkj.readmeter.db.DBPool;
import com.xdkj.readmeter.obj.Collector;
import com.xdkj.readmeter.obj.GPRS;

public class ReadMeterLogDao {

	/**
	 * 抄当前表时返回breakdown  cjq breakdown
	 * @param readlogid
	 * @param gprs
	 * @param meter
	 */
	public static void addBreakdown(int readlogid, GPRS gprs, int mid) {
		
		//更新表的
		Connection con = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			
			CallableStatement call = con.prepareCall("{call addreadmeterlog(?,?,?,?,?,?)}");
			call.setInt(1, mid);
			call.setInt(2, 4);  //actiontype
			call.setInt(3, -1);	//meterread
			call.setInt(4, 1);	//valvestate
			call.setInt(5, readlogid);  //readlogid
			call.setString(6, "");  //remark
			call.executeUpdate();
			
			con.commit();
			
		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally{
			if(con != null){
				try {
					con.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 抄当前表时返回数据  根据gprs的协议类型分析data  获取数据
	 * @param readlogid
	 * @param gprs
	 * @param mid
	 * @param data
	 */
	public static void addReadMeterLog(int readlogid, GPRS gprs, int mid,
			byte[] data) {
		
		int meterread = -1;
		byte meterstatus = 1;
		byte valvestatus = 1;
		String remark = "";
		boolean add = false;
		
		switch (gprs.getGprsprotocol()) {
		case 1:
			if(data[0] == 0x0e && data[1] == 0x0d && data[2] == 0x0c){
				int error = data[0] ^ data[1] ^ data[2] ^ 
						data[3] ^ data[4] ^ data[5] ^ 
						data[6] ^ data[7] ^ data[8] ^ data[9];
				if(error == 0){
					meterread = data[7]&0xFF;
					meterread = meterread << 8;
					meterread = meterread|(data[8]&0xFF);
					String numstr = Integer.toHexString(meterread);
					if(numstr.equals("aaaa")){
						meterread = -1;
						meterstatus = 2;
						remark = "aaaa";
					}else{
						if(numstr.equals("bbbb")){
							meterread = -1;
							meterstatus = 3;
							remark = "bbbb";
						}else{
							meterstatus = 1;
							try {
								meterread = Integer.valueOf(numstr);
							} catch (NumberFormatException e) {
//								System.out.println("返回数据错误"+"gprsid"+gprs.getPid() +"cjqaddr"+cjqaddr+"meteraddr" +deal[10 * i + 4]);
//								e.printStackTrace();
								remark = e.getMessage();
							}
						}
					}
					add = true;
				}else{
					//error != 0 give up
				}
			}
			break;
		case 2:
			meterstatus = data[16];
			valvestatus = data[16];
			ByteBuffer bf = ByteBuffer.allocate(4);
			bf.put(data, 12, 4);
			bf.order(ByteOrder.LITTLE_ENDIAN);
			
			String readhexstr = Integer.toHexString(bf.getInt(0));  //get the int   turn the int to hex string
			meterread = Integer.parseInt(readhexstr)/100;  //turn the readhexstr to the real read
			
			if((meterstatus &0x40) == 0x40){
//				timeout
				remark = meterstatus+"";
				meterstatus = 4;
			}else{
//				normal
				meterstatus = 1;
			}
			
			switch (valvestatus &0x03) {
			case 0x00:
				valvestatus = 1; //开
				break;
			case 0x02:
				valvestatus = 0; //关
				break;
			case 0x03:
				valvestatus = 2; //异常
				break;
			default:
				break;
			}
			add = true;
			break;
		default:
			break;
		}
		if(add){
			Connection con = null;
			try {
				con = DBPool.getConnection();
				con.setAutoCommit(false);
				CallableStatement call = con.prepareCall("{call addreadmeterlog(?,?,?,?,?,?)}");
				call.setInt(1, mid);
				call.setInt(2, meterstatus);  //actiontype
				call.setInt(3, meterread);	//meterread
				call.setInt(4, valvestatus);	//valvestate
				call.setInt(5, readlogid);  //readlogid
				call.setString(6, remark);  //remark
				call.executeUpdate();
				
				con.commit();
				
			} catch (SQLException e) {
				try {
					con.rollback();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
				e.printStackTrace();
			} finally{
				if(con != null){
					try {
						con.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
	}

	public static void addBreakdown(int readlogid, GPRS gprs, Collector col) {
		
		Connection con = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			CallableStatement call = con.prepareCall("{call addbreakdowns(?,?,?,?)}");
			
			for(int i = 0;i < col.getMeterNums();i++){
				
				
				call.setInt(1, readlogid);
				call.setInt(2, gprs.getPid());  //
				call.setInt(3, col.getColAddr());	//
				call.setInt(4, i+1);	//
				call.addBatch();
			}
			
			call.executeBatch();
			con.commit();
			
		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally{
			if(con != null){
				try {
					con.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * EG协议  添加表数据
	 * @param readlogid
	 * @param gprs
	 * @param col
	 * @param deal
	 */
	public static void addReadMeterLogs(int readlogid, GPRS gprs,
			Collector col, byte[] deal) {
		int meterread = -1;
		int meteraddr = 1;
		byte meterstatus = 1;
		String remark = "";
		Connection con = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			
			CallableStatement call = con.prepareCall("{call addreadmeterlogs(?,?,?,?,?,?,?,?)}");
			
			for(int i = 0;i < col.getMeterNums();i++){
				
				if(deal[10*i] == 0x0e && deal[10*i+1] == 0x0d && deal[10*i+2] == 0x0c){
					int error = deal[10 * i] ^ deal[10 * i + 1] ^ deal[10 * i + 2] ^ 
							deal[10 * i + 3] ^ deal[10 * i + 4] ^ deal[10 * i + 5] ^ 
							deal[10 * i + 6] ^ deal[10 * i + 7] ^ deal[10 * i + 8] ^ deal[10 * i + 9];
					if(error == 0){
						meteraddr = deal[10 * i + 4]&0xFF;
						meterread = deal[10 * i + 7]&0xFF;
						meterread = meterread << 8;
						meterread = meterread|(deal[10 * i + 8]&0xFF);
						String numstr = Integer.toHexString(meterread);
						if(numstr.equals("aaaa")){
							meterread = -1;
							meterstatus = 2;
							remark = "aaaa";
						}else{
							if(numstr.equals("bbbb")){
								meterread = -1;
								meterstatus = 3;
								remark = "bbbb";
							}else{
								meterstatus = 1;
								try {
									meterread = Integer.valueOf(numstr);
								} catch (NumberFormatException e) {
//									System.out.println("返回数据错误"+"gprsid"+gprs.getPid() +"cjqaddr"+cjqaddr+"meteraddr" +deal[10 * i + 4]);
//									e.printStackTrace();
									remark = e.getMessage();
								}
							}
						}
						
						call.setInt(1, readlogid);
						call.setInt(2, gprs.getPid());  //
						call.setInt(3, col.getColAddr());	//
						call.setInt(4, meteraddr);	//
						
						call.setInt(5, meterstatus);
						call.setInt(6, meterread);  //
						call.setInt(7, 1);	//
						call.setString(8, remark);	//
						
						call.addBatch();
						
					}else{
						//error != 0 give up
					}
				}
			}
			
			call.executeBatch();
			con.commit();
			
		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally{
			if(con != null){
				try {
					con.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 188 添加表数据
	 * @param readlogid
	 * @param gprs
	 * @param meters
	 * @param deal
	 */
	public static void addReadMeterLogs(int readlogid, GPRS gprs, int meters,
			byte[] deal) {
		int meterread = -1;
		byte meterstatus = 1;
		byte valvestatus = 1;
		String remark = "";
		
		Connection con = null;
		ByteBuffer bf = ByteBuffer.allocate(4);
		bf.order(ByteOrder.LITTLE_ENDIAN);
		String meteraddr = "";
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			CallableStatement call = con.prepareCall("{call addreadmeterlogsnational(?,?,?,?,?,?,?)}");
			
			for(int i = 0;i < meters;i++){
				meteraddr = "";
				meterstatus = deal[i*14+1+3+12];
				valvestatus = deal[i*14+1+3+12];
				if((meterstatus &0x40) ==0x40){
//					timeout
					remark = meterstatus+"";
					meterstatus = 4;
				}else{
//					normal
					meterstatus = 1;
				}
				switch (valvestatus &0x03) {
				case 0x00:
					valvestatus = 1; //开
					break;
				case 0x02:
					valvestatus = 0; //关
					break;
				case 0x03:
					valvestatus = 2; //异常
					break;
				default:
					break;
				}
				
				for(int j = 0;j<7;j++){
					meteraddr += String.format("%02x", deal[14*i + 1+3+j]&0xFF);
				}
				
				bf.put(deal, i*14+1+3+8, 4);
				String readhexstr = Integer.toHexString(bf.getInt(0));  //get the int   turn the int to hex string
				meterread = Integer.parseInt(readhexstr)/100;  //turn the readhexstr to the real read
				bf.flip();
				call.setInt(1, readlogid);
				call.setInt(2, gprs.getPid());  //
				call.setString(3, new StringBuilder(meteraddr).reverse().toString());	//
				
				call.setInt(4, meterstatus);
				call.setInt(5, meterread);  //
				call.setInt(6, valvestatus);	//
				call.setString(7, remark);	//
				
				call.addBatch();
			}
			
			call.executeBatch();
			con.commit();
			
		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally{
			if(con != null){
				try {
					con.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
}
