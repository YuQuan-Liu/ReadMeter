package com.xdkj.readmeter.dao;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BigDecimalConverter;

import com.xdkj.readmeter.db.DBPool;
import com.xdkj.readmeter.obj.CustomerWarn;
import com.xdkj.readmeter.util.Result2Map;

public class MeterDeductionDao {

	public static void calculate(int nid,int adid,int readlogid){
		//选出所有的阀控 付费方式为抄表完直接扣费的表  进行结算扣费
		Connection con = null;
		CallableStatement cstmt = null;
		
		try {
			con = DBPool.getConnection();
			cstmt = con.prepareCall("{call calculate_neighbor_auto(?,?,?)}");
			cstmt.setInt(1, nid);
			cstmt.setInt(2, adid);
			cstmt.setInt(3, readlogid);
			cstmt.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally{
			try {
				if(cstmt != null){
					cstmt.close();
				}
				if(con != null){
					con.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public static int valvecontrol(int nid,int adid,int readlogid){
		//阀控 付费方式为直接扣费的表  结算完之后  查看阀值  对阀门进行操作
		String SQL = "select distinct m.pid from customer c " +
				"join meter m " +
				"on c.pid = m.customerid " +
				"where c.neighborid = ? and c.customerbalance < m.ValveOFFThre and m.valid = 1 and c.valid = 1 and m.isvalve = 1 and m.DeductionStyle = 1";
		Connection con = null;
		int valvelogid = 0;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, nid);
			ResultSet rs = pstmt.executeQuery();
			rs.last();
			int count = rs.getRow();
			if(count > 0){
				//有要关的阀
				//插入ValveLog
				valvelogid = ValveLogDao.addValveLog(adid,count);
				rs.beforeFirst();
				List<Integer> list = new ArrayList<>();
				while(rs.next()){
					list.add(rs.getInt(1));
				}
				ValveConfLogDao.addValveConfLogs(list,valvelogid);
			}
			con.commit();
			
		} catch (SQLException e) {
			e.printStackTrace();
		} finally{
			try {
				if(con != null){
					con.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return valvelogid;
	}

	public static List<CustomerWarn> warnCustomer(int nid, int adminid, int readlogid) {
		
		String SQL = "select c.pid cid,c.customerName,c.customerBalance,c.customerMobile,c.customerEmail,c.warnStyle,wc.companyName from customer c " +
				"join meter m " +
				"on c.pid = m.customerid " +
				"join neighbor n " +
				"on n.pid = c.neighborid " +
				"join watercompany wc " +
				"on wc.pid = n.wcid " +
				"where c.neighborid = ? and c.customerbalance < c.warnThre and m.valid = 1 and c.valid = 1 and m.isvalve = 1 and m.DeductionStyle = 1";
		
		Connection con = null;
		List<CustomerWarn> list = null;
		try {
			con = DBPool.getConnection();
			con.setAutoCommit(false);
			PreparedStatement pstmt = con.prepareStatement(SQL);
			pstmt.setInt(1, nid);
			
			ResultSet rs = pstmt.executeQuery();
			rs.last();
			int count = rs.getRow();
			if(count > 0){
				//有要提醒的用户
				rs.beforeFirst();
				list = new ArrayList<>();
				Map map = null;
				while(rs.next()){
					map = Result2Map.getResultMap(rs);
					CustomerWarn warn = new CustomerWarn();
					ConvertUtils.register(new BigDecimalConverter(), BigDecimal.class);
					BeanUtils.populate(warn, map);
					list.add(warn);
				}
			}
			con.commit();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			try {
				if(con != null){
					con.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return list;
	}
}
