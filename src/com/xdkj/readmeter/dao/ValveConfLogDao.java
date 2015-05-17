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
}
