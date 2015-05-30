package com.xdkj.readmeter.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;

import com.xdkj.readmeter.db.DBPool;
import com.xdkj.readmeter.obj.ValveConfLog;
import com.xdkj.readmeter.obj.Valvelog;
import com.xdkj.readmeter.util.Result2Map;

public class ValveConfLogDao {
	
	public static List<ValveConfLog> getValveConfLog(int valvelogid){
		
		String SQL = "select vcl.PID pid,vcl.MeterID meterid,vcl.Switch switchaction,vcl.Result result,vcl.ValveLogID valvelogid," +
				"m.MeterAddr meteraddr,m.GPRSID gprsid," +
				"g.IP ip,g.Port port,g.GPRSProtocol gprsprotocol,g.GPRSAddr gprsaddr from ValveConfLog vcl " +
				"left join Meter m " +
				"on vcl.MeterID = m.PID " +
				"left join GPRS g " +
				"on m.GPRSID = g.PID " +
				"where vcl.ValveLogID = ?";
		
		Connection con = null;
		ValveConfLog valveConfLog = null;
		List<ValveConfLog> list = new ArrayList<>();
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, valvelogid);
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				valveConfLog = new ValveConfLog();
				Map map = Result2Map.getResultMap(rs);
				BeanUtils.populate(valveConfLog, map);
				list.add(valveConfLog);
			}
		} catch (Exception e) {
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
		return list;
	}

	public static void updateValveConfLog(ValveConfLog valveConfLog, boolean finished,
			String reason) {
		
		String SQL = "update valveconflog " +
				"set result = ?,errorReason =?,errorstatus = 0,completetime = now() " +
				"where pid = ?";
		
		String SQL2 = "update Meter " +
				"set valvestate = ? " +
				"where pid = "+valveConfLog.getMeterid();
		Connection con = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, finished?1:2);
			pstmt.setString(2, reason);
			pstmt.setInt(3, valveConfLog.getPid());
			
			pstmt.executeUpdate();
			
			pstmt = con.prepareStatement(SQL2);
			if(finished){
				pstmt.setInt(1, valveConfLog.getSwitchaction());
			}else{
				pstmt.setInt(1, 2);
			}
			
			pstmt.executeUpdate();
			
			con.commit();
		} catch (Exception e) {
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

	public static void addValveConfLogs(List<Integer> list,int valvelogid) {
		
		String SQL = "insert into valveconflog " +
				"(meterid,switch,result,errorreason,errorstatus,removereason,valvelogid) " +
				"values(?,0,0,'',0,'',"+valvelogid+")";
		
		Connection con = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			
			PreparedStatement pstmt = con.prepareStatement(SQL);
			
			for(int i=0;i <list.size();i++){
				pstmt.setInt(1, list.get(i));
				pstmt.addBatch();
			}
			
			pstmt.executeBatch();
			
			con.commit();
		} catch (Exception e) {
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
