/**
 *    Copyright 2009-2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Jeff Butler 
 */
public class BatchExecutor extends BaseExecutor {

  public static final int BATCH_UPDATE_RETURN_VALUE = Integer.MIN_VALUE + 1002;

  //statement执行队列
  private final List<Statement> statementList = new ArrayList<Statement>();
  //结果集队列
  private final List<BatchResult> batchResultList = new ArrayList<BatchResult>();
  //上次执行sql
  private String currentSql;
  //上次执行MappedStatement
  private MappedStatement currentStatement;

  public BatchExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  @Override
  public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
	//获取配置上下文
    final Configuration configuration = ms.getConfiguration();
    //创建StatementHandler实例
    final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT, null, null);
    //从MappedStatement中获取sql对象
    final BoundSql boundSql = handler.getBoundSql();
    final String sql = boundSql.getSql();
    final Statement stmt;
    //判断本次执行sql是否与上次执行sql相同
    if (sql.equals(currentSql) && ms.equals(currentStatement)) {
      int last = statementList.size() - 1;
      //获取上次使用的Statement实例
      stmt = statementList.get(last);
      //刷新事务过期时间
      applyTransactionTimeout(stmt);
      //为Statement绑定新参数
      handler.parameterize(stmt);
      //将参数对象添加至BatchResult对象中（BatchResult中维护了一个参数List）
      BatchResult batchResult = batchResultList.get(last);
      batchResult.addParameterObject(parameterObject);
    } else {
      //获取新的数据库连接实例
      Connection connection = getConnection(ms.getStatementLog());
      //创建Statement实例
      stmt = handler.prepare(connection, transaction.getTimeout());
      //为Statement绑定参数
      handler.parameterize(stmt); 
      //刷新上次执行sql以及Statement，为下次判断做准备
      currentSql = sql;
      currentStatement = ms;
      statementList.add(stmt);
      //在结果集队列中添加BatchResult对象实例
      batchResultList.add(new BatchResult(ms, sql, parameterObject));
    }
    handler.batch(stmt);
    //批处理执行器的更新操作不会返回修改行数，而是返回一个固定的数字，此处需要注意！
    return BATCH_UPDATE_RETURN_VALUE;
  }

  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException {
    Statement stmt = null;
    try {
      flushStatements();
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds, resultHandler, boundSql);
      Connection connection = getConnection(ms.getStatementLog());
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt);
      return handler.<E>query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    flushStatements();
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Connection connection = getConnection(ms.getStatementLog());
    Statement stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    return handler.<E>queryCursor(stmt);
  }

  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    try {
      List<BatchResult> results = new ArrayList<BatchResult>();
      //如果rollback则返回空集
      if (isRollback) {
        return Collections.emptyList();
      }
      for (int i = 0, n = statementList.size(); i < n; i++) {
    	//从statement队列中获取statement实例
        Statement stmt = statementList.get(i);
        //设置事务超时时间
        applyTransactionTimeout(stmt);
        //从结果集队列中获取BatchResult实例，结果集队列与上面statement队列是一一对应关系
        BatchResult batchResult = batchResultList.get(i);
        try {
          //设置本次操作修改的行数（int数组形式）,需要注意的是多次执行相同的修改sql时，结果信息会保存在同一个BatchResult实例中
          batchResult.setUpdateCounts(stmt.executeBatch());
          MappedStatement ms = batchResult.getMappedStatement();
          List<Object> parameterObjects = batchResult.getParameterObjects();
          KeyGenerator keyGenerator = ms.getKeyGenerator();
          //此处是回写字段逻辑，如果在xml中配置了需要回写的字段，则会调用KeyGenerator进行回写，具体逻辑在KeyGenerator讲解
          if (Jdbc3KeyGenerator.class.equals(keyGenerator.getClass())) {
            Jdbc3KeyGenerator jdbc3KeyGenerator = (Jdbc3KeyGenerator) keyGenerator;
            jdbc3KeyGenerator.processBatch(ms, stmt, parameterObjects);
          } else if (!NoKeyGenerator.class.equals(keyGenerator.getClass())) { 
            for (Object parameter : parameterObjects) {
              keyGenerator.processAfter(this, ms, stmt, parameter);
            }
          }
        } catch (BatchUpdateException e) {
          StringBuilder message = new StringBuilder();
          message.append(batchResult.getMappedStatement().getId())
              .append(" (batch index #")
              .append(i + 1)
              .append(")")
              .append(" failed.");
          if (i > 0) {
            message.append(" ")
                .append(i)
                .append(" prior sub executor(s) completed successfully, but will be rolled back.");
          }
          throw new BatchExecutorException(message.toString(), e, results, batchResult);
        }
        results.add(batchResult);
      }
      return results;
    } finally {
      //清空批处理队列
      for (Statement stmt : statementList) {
        closeStatement(stmt);
      }
      currentSql = null;
      statementList.clear();
      batchResultList.clear();
    }
  }

}
