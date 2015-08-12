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
import com.xdkj.readmeter.obj.Collector;
import com.xdkj.readmeter.obj.GPRS;
import com.xdkj.readmeter.obj.Meter;
import com.xdkj.readmeter.util.Result2Map;

public class MeterDao {
	
	public static List<Collector> getCollectorsByGID(int gid){
		
		String SQL = "select CollectorAddr colAddr,COUNT(*) meterNums from meter " +
				"where gprsid = ? and Valid = 1 " +
				"group by CollectorAddr " +
				"order by CollectorAddr ";
		Connection con = null;
		List<Collector> list = new ArrayList<>();
		Map map = null;
		Collector col = null;
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, gid);
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				map = Result2Map.getResultMap(rs);
				col = new Collector();
				BeanUtils.populate(col, map);
				list.add(col);
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

	public static Meter getMeterbyID(int mid) {
		String SQL = "select collectorAddr,meterAddr from meter " +
				"where pid = ?";
		Connection con = null;
		Map map = null;
		Meter meter = null;
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, mid);
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				map = Result2Map.getResultMap(rs);
				meter = new Meter();
				BeanUtils.populate(meter, map);
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
		return meter;
	}

	public static int getMeterCountByGID(Integer gid) {
		String SQL = "select count(*) from meter " +
				"where gprsid = ? and valid = 1";
		Connection con = null;
		int count = 0;
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, gid);
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				count = rs.getInt(1);
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
		return count;
	}
}
