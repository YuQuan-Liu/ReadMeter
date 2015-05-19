package com.xdkj.readmeter.dao;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
		String SQL = "insert into ReadMeterLog " +
				"(MeterId,ActionType,ActionResult,ReadLogid,remark) " +
				"values(?,?,?,?,'')";
		String SQL2 = "update Meter " +
				"set meterstate = ?,readdata = ?,readtime = now() " +
				"where pid = ?";
		
		Connection con = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, mid);
			pstmt.setInt(2, 4);
			pstmt.setInt(3, -1);
			pstmt.setInt(4, readlogid);
			
			pstmt.executeUpdate();
			pstmt = con.prepareStatement(SQL2);
			pstmt.setInt(1, 4);
			pstmt.setInt(2, -1);
			pstmt.setInt(3, readlogid);
			pstmt.executeUpdate();
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
		String SQL = "insert into ReadMeterLog " +
				"(MeterId,ActionType,ActionResult,ReadLogid,remark) " +
				"values(?,?,?,?,?)";
		String SQL2 = "update Meter " +
				"set meterstate = ?,readdata = ?,valvestate = ?,readtime = now() " +
				"where pid = ?";
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
					
					Connection con = null;
					try {
						con = DBPool.getConnection();
						con.setAutoCommit(false);
						PreparedStatement pstmt = con.prepareStatement(SQL);
						pstmt.setInt(1, mid);
						pstmt.setInt(2, meterstatus);
						pstmt.setInt(3, meterread);
						pstmt.setInt(4, readlogid);
						pstmt.setString(5, remark);
						
						pstmt.executeUpdate();
						pstmt = con.prepareStatement(SQL2);
						pstmt.setInt(1, meterstatus);
						pstmt.setInt(2, meterread);
						pstmt.setInt(3, 0);
						pstmt.setInt(4, mid);
						pstmt.executeUpdate();
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
			
			if(((meterstatus &0x40) ==0x40) && ((meterstatus &0x80)==0x80)){
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
			case 0x01:
				valvestatus = 0; //关
				break;
			case 0x03:
				valvestatus = 2; //异常
				break;
			default:
				break;
			}
			
			Connection con = null;
			try {
				con = DBPool.getConnection();
				con.setAutoCommit(false);
				PreparedStatement pstmt = con.prepareStatement(SQL);
				pstmt.setInt(1, mid);
				pstmt.setInt(2, meterstatus);
				pstmt.setInt(3, meterread);
				pstmt.setInt(4, readlogid);
				pstmt.setString(5, remark);
				
				pstmt.executeUpdate();
				pstmt = con.prepareStatement(SQL2);
				pstmt.setInt(1, meterstatus);
				pstmt.setInt(2, meterread);
				pstmt.setInt(3, valvestatus);
				pstmt.setInt(4, mid);
				pstmt.executeUpdate();
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
			break;
		default:
			break;
		}
		
	}

	public static void addBreakdown(int readlogid, GPRS gprs, Collector col) {
		
		String SQL = "insert into ReadMeterLog " +
				"(MeterId,ActionType,ActionResult,ReadLogid,remark) " +
				"select pid,?,?,?,? from Meter " +
				"where gprsid = "+gprs.getPid()+" and CollectorAddr = "+col.getColAddr()+" and MeterAddr = ? and valid = 1 ";
		String SQL2 = "update Meter " +
				"set meterstate = ?,readdata = ?,readtime = now() " +
				"where gprsid = "+gprs.getPid()+" and CollectorAddr = "+col.getColAddr()+" and MeterAddr = ? and valid = 1 ";
		
		Connection con = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			PreparedStatement pstmt = null;
			
			for(int i = 0;i < col.getMeterNums();i++){
				pstmt = con.prepareStatement(SQL);
				pstmt.setInt(1, 4);
				pstmt.setInt(2, -1);
				pstmt.setInt(3, readlogid);
				pstmt.setString(4, "");
				pstmt.setInt(5, i);
				pstmt.addBatch();
				
				pstmt = con.prepareStatement(SQL2);
				pstmt.setInt(1, 4);
				pstmt.setInt(2, -1);
				pstmt.setInt(3, i);
				pstmt.addBatch();
				
			}
			
			pstmt.executeBatch();
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
		byte meterstatus = 1;
		String remark = "";
		String SQL = "insert into ReadMeterLog " +
				"(MeterId,ActionType,ActionResult,ReadLogid,remark) " +
				"select pid,?,?,?,? from Meter " +
				"where gprsid = "+gprs.getPid()+" and CollectorAddr = "+col.getColAddr()+" and MeterAddr = ? and valid = 1 ";
		String SQL2 = "update Meter " +
				"set meterstate = ?,readdata = ?,readtime = now() " +
				"where gprsid = "+gprs.getPid()+" and CollectorAddr = "+col.getColAddr()+" and MeterAddr = ? and valid = 1 ";
		
		Connection con = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			PreparedStatement pstmt = null;
			
			for(int i = 0;i < col.getMeterNums();i++){
				
				if(deal[10*i] == 0x0e && deal[10*i+1] == 0x0d && deal[10*i+2] == 0x0c){
					int error = deal[10 * i] ^ deal[10 * i + 1] ^ deal[10 * i + 2] ^ 
							deal[10 * i + 3] ^ deal[10 * i + 4] ^ deal[10 * i + 5] ^ 
							deal[10 * i + 6] ^ deal[10 * i + 7] ^ deal[10 * i + 8] ^ deal[10 * i + 9];
					if(error == 0){
						meterread = deal[7]&0xFF;
						meterread = meterread << 8;
						meterread = meterread|(deal[8]&0xFF);
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
						
						pstmt = con.prepareStatement(SQL);
						pstmt.setInt(1, meterstatus);
						pstmt.setInt(2, meterread);
						pstmt.setInt(3, readlogid);
						pstmt.setString(4, remark);
						pstmt.setInt(5, i);
						pstmt.addBatch();
						
						pstmt = con.prepareStatement(SQL2);
						pstmt.setInt(1, meterstatus);
						pstmt.setInt(2, meterread);
						pstmt.setInt(3, i);
						pstmt.addBatch();
						
					}else{
						//error != 0 give up
					}
				}
			}
			
			pstmt.executeBatch();
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
		String SQL = "insert into ReadMeterLog " +
				"(MeterId,ActionType,ActionResult,ReadLogid,remark) " +
				"select pid,?,?,?,? from Meter " +
				"where gprsid = "+gprs.getPid()+" and MeterAddr = ? and valid = 1 ;";
		String SQL2 = "update Meter " +
				"set meterstate = ?,readdata = ?,valvestate = ?,readtime = now() " +
				"where gprsid = "+gprs.getPid()+" and MeterAddr = ? and valid = 1 ";
		
		Connection con = null;
		ByteBuffer bf = ByteBuffer.allocate(4);
		bf.order(ByteOrder.LITTLE_ENDIAN);
		String meteraddr = "";
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			PreparedStatement pstmt = null;
			PreparedStatement pstmt2 = null;
			
			for(int i = 0;i < meters;i++){
				
				meterstatus = deal[i*14+1+3+12];
				valvestatus = deal[i*14+1+3+12];
				if(((meterstatus &0x40) ==0x40) && ((meterstatus &0x80)==0x80)){
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
				case 0x01:
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
				pstmt = con.prepareStatement(SQL);
				pstmt.setInt(1, meterstatus);
				pstmt.setInt(2, meterread);
				pstmt.setInt(3, readlogid);
				pstmt.setString(4, remark);
				pstmt.setString(5, meteraddr);
				pstmt.addBatch();
				
				pstmt2 = con.prepareStatement(SQL2);
				pstmt2.setInt(1, meterstatus);
				pstmt2.setInt(2, meterread);
				pstmt2.setInt(3, valvestatus);
				pstmt2.setString(4, meteraddr);
				pstmt2.addBatch();
			}
			
			pstmt.executeBatch();
			pstmt2.executeBatch();
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
