package interceptor;

import java.sql.Connection;
import java.util.Properties;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

@Intercepts(@Signature(
		type = StatementHandler.class,
		method = "prepare",
		args = {Connection.class,Integer.class} 
 		))
public class Intercrptors  implements Interceptor{
	
	public Object intercept(Invocation invocation) throws Throwable {
		// TODO Auto-generated method stub
		return invocation.proceed();
	}

	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	public void setProperties(Properties properties) {
		// TODO Auto-generated method stub
		
	}

}
