package com.xdkj.readmeter.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.xdkj.readmeter.db.DBPool;
import com.xdkj.readmeter.obj.GPRS;
import com.xdkj.readmeter.obj.Meter;

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
		// TODO Auto-generated method stub
		
	}

}
