package com.xdkj.readmeter.db;

import java.beans.PropertyVetoException;
import java.sql.Connection;
import java.sql.SQLException;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.xdkj.readmeter.util.Property;


public class DBPool {

	private static ComboPooledDataSource cpds;
	
	static{
		cpds = new ComboPooledDataSource();
		try {
			cpds.setDriverClass(Property.getValue("driver"));
			cpds.setJdbcUrl(Property.getValue("url"));
			cpds.setUser(Property.getValue("user"));
			cpds.setPassword(Property.getValue("password"));
			
			cpds.setMinPoolSize(5);
			cpds.setAcquireIncrement(5);
			cpds.setMaxPoolSize(20);
			cpds.setMaxStatements(180);
			cpds.setMaxIdleTime(60);
			
		} catch (PropertyVetoException e) {
			e.printStackTrace();
		}
	}
	
	public static Connection getConnection() throws SQLException{
		return cpds.getConnection();
	}
	
}
