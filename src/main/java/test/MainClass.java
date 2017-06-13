package test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.datasource.jndi.JndiDataSourceFactory;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import dao.User;
import daoMapper.UserMapper;

public class MainClass {

	public static void main(String[] args) {
		
		
		
		String configPath = "mybatis-config.xml";
		try{
			InputStream is = Resources.getResourceAsStream(configPath);
//			InputStream is0 = Resources.getResourceAsStream(configPath);
			SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(is,"development1");
//			SqlSessionFactory sqlSessionFactory0 = new SqlSessionFactoryBuilder().build(is0, "development2");
			SqlSession session = sqlSessionFactory.openSession();
//			SqlSession session0 = sqlSessionFactory.openSession();
			UserMapper mapper = session.getMapper(UserMapper.class);
//			UserMapper mapper0 = session0.getMapper(UserMapper.class);
			User u = new User();
			String phone = "137325610";
			u.setPhoneNO(phone);
			PageBean pb = new PageBean(u);
			int id = mapper.selectIDByUser(u);
			session.commit(); 
			session.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

