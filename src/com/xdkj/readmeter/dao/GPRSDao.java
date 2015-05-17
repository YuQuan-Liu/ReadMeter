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
import com.xdkj.readmeter.obj.GPRS;
import com.xdkj.readmeter.util.Result2Map;

public class GPRSDao {
	public static List<GPRS> getGPRSsbyNID(int nid){
		
		String SQL = "select pid,neighborid,gprsaddr,gprsprotocol,ip,port from gprs " +
				"where neighborid = ? and valid = 1 ";
		
		Connection con = null;
		Map map = null;
		GPRS gprs = null;
		List<GPRS> list = new ArrayList<>();
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, nid);
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				map = Result2Map.getResultMap(rs);
				gprs = new GPRS();
				BeanUtils.populate(gprs, map);
				list.add(gprs);
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
	
	public static GPRS getGPRSbyMID(int mid){
		
		String SQL = "select pid,neighborid,gprsaddr,gprsprotocol,ip,port from gprs " +
				"where PID = (select gprsid from Meter where PID = ?)";
		Connection con = null;
		Map map = null;
		GPRS gprs = null;
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, mid);
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				map = Result2Map.getResultMap(rs);
				gprs = new GPRS();
				BeanUtils.populate(gprs, map);
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
		return gprs;
	}
}
