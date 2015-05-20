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
import com.xdkj.readmeter.obj.Readlog;
import com.xdkj.readmeter.util.Property;
import com.xdkj.readmeter.util.Result2Map;

public class ReadLogDao {

	public static Readlog getByID(int pid){
		
		String SQL = "select pid,adminid,objectId,readType,remote,readObject,ip,readStatus,failReason,startTime,completeTime,settle,result from ReadLog where pid = ? ";
		
		Connection con = null;
		Readlog readlog = new Readlog();
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, pid);
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				Map map = Result2Map.getResultMap(rs);
				BeanUtils.populate(readlog, map);
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
		return readlog;
	}
	
	public static List<Readlog> getAllNeighborReadlog(int adminid,int pid){
		
		String ip = Property.getValue("ip").trim();
		String SQL = "select pid,adminid,objectId,readType,remote,readObject,ip,readStatus,failReason,startTime,completeTime,settle,result from ReadLog " +
				"where pid >= ? and adminid = ? and ip = ? ";
		
		Connection con = null;
		Readlog readlog = null;
		List<Readlog> list = new ArrayList<>();
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, pid);
			pstmt.setInt(2, adminid);
			pstmt.setString(3, ip);
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				readlog = new Readlog();
				Map map = Result2Map.getResultMap(rs);
				BeanUtils.populate(readlog, map);
				list.add(readlog);
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

	public static void updateReadLog(int readlogid, boolean finished,
			String reason, String result) {
		
		String SQL = "update readlog " +
				"set ReadStatus = 100,FailReason = ?,CompleteTime = now(),Result = ? " +
				"where PID = ?";
		
		Connection con = null;
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setString(1, reason);
			pstmt.setString(2, result);
			pstmt.setInt(3, readlogid);
			
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

	public static void updateReadLog(int readlogid, String result, String reason) {
		
		String SQL = "update readlog " +
				"set ReadStatus = 100,FailReason = ?,CompleteTime = now(),Result = ? " +
				"where PID = ?";
		
		Connection con = null;
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setString(1, reason);
			pstmt.setString(2, result);
			pstmt.setInt(3, readlogid);
			
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
}
