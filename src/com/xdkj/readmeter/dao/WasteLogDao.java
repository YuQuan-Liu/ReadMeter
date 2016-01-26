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
import com.xdkj.readmeter.obj.WasteLog;
import com.xdkj.readmeter.util.Result2Map;

public class WasteLogDao {
	
	public static List<WasteLog> wasteStatistics(int n_id,int readlogid){
		String SQL = "select w.lounum louNum,w.mainmeter mainMeter,w.neighborid n_id,w.sumread readdata,m_.pid m_id from ( " +
				"select lounum,mainmeter,c.neighborid,sum(readdata) sumread from meter m " +
				"join customer c " +
				"on m.customerid = c.pid " +
				"where m.valid = 1 and c.valid = 1 and c.neighborid = ? and m.MainMeter != 1 " +
				"group by lounum,mainmeter) w " +
				"left join ( " +
				"select m.pid,c.lounum from meter m " +
				"join customer c " +
				"on m.customerid = c.pid " +
				"where  m.valid = 1 and c.valid = 1 and c.neighborid = ? and m.MainMeter = 2 " +
				") m_ on w.lounum = m_.lounum ";
		Connection con = null;
		List<WasteLog> list = new ArrayList<>();
		WasteLog wasteLog = null;
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, n_id);
			pstmt.setInt(2, n_id);
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				wasteLog = new WasteLog();
				Map map = Result2Map.getResultMap(rs);
				BeanUtils.populate(wasteLog, map);
				list.add(wasteLog);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
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

	public static void addWasteLog(int nid, int readlogid, int m_id,String lou, int meterread, int slavesum, int waste) {
		
		String SQL = "insert into wastelog " +
				"(neighborid,readlogid,meterid,actiontime,lounum,meterread,salvesum,waste,valid,remark) " +
				"values(?,?,?,now(),?,?,?,?,0,'')";
		Connection con = null;
		
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, nid);
			pstmt.setInt(2, readlogid);
			pstmt.setInt(3, m_id);
			pstmt.setString(4, lou);
			pstmt.setInt(5, meterread);
			pstmt.setInt(6, slavesum);
			pstmt.setInt(7, waste);
			pstmt.executeUpdate();
		} catch (SQLException e) {
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

	public static void addNeiWasteLog(int nid, int readlogid) {
		
		String SQL = "select sum(readdata) from meter m " +
				"where neighborid = ? and m.valid = 1 and m.mainmeter = 0";
		String SQL2 = "select c.lounum,m.pid,m.readdata from meter m " +
				"join customer c " +
				"on m.customerid = c.pid " +
				"where c.neighborid = ? and m.mainmeter = 1 and m.valid = 1 and c.valid = 1 ";
		
		Connection con = null;
		
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, nid);
			ResultSet rs = pstmt.executeQuery();
			int allread = 0;
			while(rs.next()){
				allread = rs.getInt(1);
			}
			if(allread != 0){
				//表有读数
				pstmt = con.prepareStatement(SQL2);
				pstmt.setInt(1, nid);
				rs = pstmt.executeQuery();
				String lou = "0";
				int mid = 0;
				int readdata = 0;
				while(rs.next()){
					lou = rs.getString(1);
					mid = rs.getInt(2);
					readdata = rs.getInt(3);
					break;
				}
				if(mid > 0){
					addWasteLog(nid, readlogid, mid, "0", readdata, allread, readdata-allread);
				}
			}
		} catch (SQLException e) {
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
