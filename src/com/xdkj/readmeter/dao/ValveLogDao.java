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
}