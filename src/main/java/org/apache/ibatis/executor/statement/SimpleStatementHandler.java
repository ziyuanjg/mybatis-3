/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * @author Clinton Begin
 */
public class SimpleStatementHandler extends BaseStatementHandler {

  public SimpleStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  @Override
  public int update(Statement statement) throws SQLException {
	//获取sql
    String sql = boundSql.getSql();
    //获取参数对象
    Object parameterObject = boundSql.getParameterObject();
    //获取主键生产器
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    int rows;
    //根据KeyGenerator的具体类型决定是否返回修改数据的主键，具体逻辑放在KeyGenerator中讲解
    if (keyGenerator instanceof Jdbc3KeyGenerator) {
      statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
      //获取修改行数
      rows = statement.getUpdateCount();
      //将返回的主键信息添加到parameterObject中
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else if (keyGenerator instanceof SelectKeyGenerator) {
      statement.execute(sql);
      rows = statement.getUpdateCount();
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else {
      statement.execute(sql);
      rows = statement.getUpdateCount();
    }
    return rows;
  }

  @Override
  public void batch(Statement statement) throws SQLException {
	//获取sql
    String sql = boundSql.getSql();
    //执行批处理
    statement.addBatch(sql);
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
	//获取sql
    String sql = boundSql.getSql();
    //执行查询
    statement.execute(sql);
    //处理结果集
    return resultSetHandler.<E>handleResultSets(statement);
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
	//获取sql
    String sql = boundSql.getSql();
    //执行查询
    statement.execute(sql);
    //处理结果集
    return resultSetHandler.<E>handleCursorResultSets(statement);
  }

  @Override
  //通过Connection创建Statement实例
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    if (mappedStatement.getResultSetType() != null) {
      //创建能返回指定类型的ResultSet的Statement实例
      return connection.createStatement(mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    } else {
      return connection.createStatement();
    }
  }

  @Override
  public void parameterize(Statement statement) throws SQLException {
    // N/A
  }

}
