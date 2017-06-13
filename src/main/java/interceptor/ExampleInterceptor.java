package interceptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.jdbc.PreparedStatementLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import daoMapper.UserMapper;
import test.InterceptorClass;
import test.PageBean;

@Intercepts(@Signature(
		type = StatementHandler.class,
		method = "prepare",
		args = {Connection.class,Integer.class} 
 		))
public class ExampleInterceptor implements Interceptor{

	public Object intercept(Invocation invocation) throws Throwable {
		//statementHandler
		MetaObject statementHandler = MetaObject.forObject(invocation.getTarget(), new DefaultObjectFactory(), new DefaultObjectWrapperFactory(), new DefaultReflectorFactory());
		//具体执行的statementHandler，此处是PreparedStatementHandler
		MetaObject ps = statementHandler.metaObjectForProperty("delegate");
		//MappedStatement
		MappedStatement ms = (MappedStatement) ps.getValue("mappedStatement");
		//Configuration
		Configuration config = (Configuration) ps.getValue("configuration");
		//参数对象
		Object parameterObject = statementHandler.getValue("parameterHandler.parameterObject");
		//sql对象
		BoundSql bs = (BoundSql) statementHandler.getValue("boundSql"); 
		
		return invocation.proceed();
	}

	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	public void setProperties(Properties properties) {
	}

}
