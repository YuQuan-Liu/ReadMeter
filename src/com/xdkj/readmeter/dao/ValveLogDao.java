package com.xdkj.readmeter.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;

import com.xdkj.readmeter.db.DBPool;
import com.xdkj.readmeter.obj.Valvelog;
import com.xdkj.readmeter.util.Result2Map;

public class ValveLogDao {
	
	public static Valvelog getByID(int pid){
		
		String SQL = "select pid,adminid,actionTime,auto,actionCount,completeCount,errorCount,status,failReason,remark from ValveLog " +
				"where pid = ? ";
		
		Connection con = null;
		Valvelog valvelog = new Valvelog();
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, pid);
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				Map map = Result2Map.getResultMap(rs);
				BeanUtils.populate(valvelog, map);
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
		return valvelog;
	}

	public static void updateValveLog(Valvelog valvelog, int normal, int error) {
		String SQL = "update valvelog " +
				"set completecount = ?,errorcount=?,status = 100 " +
				"where pid = ?";
		
		Connection con = null;
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, normal);
			pstmt.setInt(2, error);
			pstmt.setInt(3, valvelog.getPid());
			
			pstmt.executeUpdate();
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

	public static int addValveLog(int adid, int count) {
		String SQL = "insert into valvelog " +
				"(adminid,actiontime,auto,actioncount,completecount,errorcount,status,failreason,remark) " +
				"values(?,now(),1,?,0,0,0,'','')";
		
		String SQL1 = "select max(pid) from valvelog " +
				"where adminid = ? and auto = 1 and status = 0 and actioncount = ?";
		int valvelogid = 0;
		Connection con = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, adid);
			pstmt.setInt(2, count);
			
			pstmt.executeUpdate();
			
			pstmt = con.prepareStatement(SQL1);
			pstmt.setInt(1, adid);
			pstmt.setInt(2, count);
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				valvelogid = rs.getInt(1);
			}
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
		return valvelogid;
		
	}
}
