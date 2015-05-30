package com.xdkj.readmeter.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.xdkj.readmeter.db.DBPool;
import com.xdkj.readmeter.obj.CustomerWarn;

public class WarnDao {

	public static void addWarnLog(CustomerWarn warn, boolean done) {
		
		String SQL = "insert into warnlog " +
				"(customerid,warnstyle,mobile,email,warncontent,warnReason,actiontime,warncount,successcount,failcount,valid,warn) " +
				"values(?,1,?,?,?,'',now(),1,?,?,1,0)";
		
		Connection con = null;
		
		try {
			con = DBPool.getConnection();
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, warn.getCid());
			pstmt.setString(2, warn.getCustomerMobile());
			pstmt.setString(3, warn.getCustomerEmail());
			pstmt.setString(4, warn.getCustomerName()+"~"+warn.getCustomerBalance());
			if(done){
				pstmt.setInt(5, 1);
				pstmt.setInt(6, 0);
			}else{
				pstmt.setInt(5, 0);
				pstmt.setInt(6, 1);
			}
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

}
