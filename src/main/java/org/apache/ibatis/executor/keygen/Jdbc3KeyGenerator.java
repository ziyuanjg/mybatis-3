/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.executor.keygen;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  /**
   * A shared instance.
   * @since 3.4.3
   */
  public static final Jdbc3KeyGenerator INSTANCE = new Jdbc3KeyGenerator();

  //在插入操作之前执行
  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // do nothing
  }

  //在插入操作后执行
  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    processBatch(ms, stmt, getParameters(parameter));
  }

  public void processBatch(MappedStatement ms, Statement stmt, Collection<Object> parameters) {
    ResultSet rs = null;
    try {
      //获取返回的主键值
      rs = stmt.getGeneratedKeys();
      final Configuration configuration = ms.getConfiguration();
      //获取类型处理器容器
      final TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      //获取xml中配置的返回属性名称集合
      final String[] keyProperties = ms.getKeyProperties();
      //获取result中的列名称和类型信息
      final ResultSetMetaData rsmd = rs.getMetaData();
      TypeHandler<?>[] typeHandlers = null;
      //如果xml中没有配置返回字段或者实际返回的字段数少于配置字段数则不会进行绑定
      if (keyProperties != null && rsmd.getColumnCount() >= keyProperties.length) {
        for (Object parameter : parameters) {
          //通常每个statement都有一条结果数据
          if (!rs.next()) {
            break;
          }
          //创建参数对象的元对象实例，此处是关键，因为实际上后面是将返回的字段信息写入这个metaParam对象中，而metaParam对象持有parameter对象的引用
          //所以实际上数据是写入到了BatchResult的参数对象中
          final MetaObject metaParam = configuration.newMetaObject(parameter);
          //如果类型处理器为空则根据返回类型获取一个符合的类型处理器
          if (typeHandlers == null) {
            typeHandlers = getTypeHandlers(typeHandlerRegistry, metaParam, keyProperties, rsmd);
          }
          //将返回数据写入BatchResult的参数对象
          populateKeys(rs, metaParam, keyProperties, typeHandlers);
        }
      }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }

  private Collection<Object> getParameters(Object parameter) {
    Collection<Object> parameters = null;
    if (parameter instanceof Collection) {
      parameters = (Collection) parameter;
    } else if (parameter instanceof Map) {
      Map parameterMap = (Map) parameter;
      if (parameterMap.containsKey("collection")) {
        parameters = (Collection) parameterMap.get("collection");
      } else if (parameterMap.containsKey("list")) {
        parameters = (List) parameterMap.get("list");
      } else if (parameterMap.containsKey("array")) {
        parameters = Arrays.asList((Object[]) parameterMap.get("array"));
      }
    }
    if (parameters == null) {
      parameters = new ArrayList<Object>();
      parameters.add(parameter);
    }
    return parameters;
  }

  private TypeHandler<?>[] getTypeHandlers(TypeHandlerRegistry typeHandlerRegistry, MetaObject metaParam, String[] keyProperties, ResultSetMetaData rsmd) throws SQLException {
    TypeHandler<?>[] typeHandlers = new TypeHandler<?>[keyProperties.length];
    for (int i = 0; i < keyProperties.length; i++) {
      if (metaParam.hasSetter(keyProperties[i])) {
        Class<?> keyPropertyType = metaParam.getSetterType(keyProperties[i]);
        TypeHandler<?> th = typeHandlerRegistry.getTypeHandler(keyPropertyType, JdbcType.forCode(rsmd.getColumnType(i + 1)));
        typeHandlers[i] = th;
      }
    }
    return typeHandlers;
  }

  private void populateKeys(ResultSet rs, MetaObject metaParam, String[] keyProperties, TypeHandler<?>[] typeHandlers) throws SQLException {
    for (int i = 0; i < keyProperties.length; i++) {
      String property = keyProperties[i];
      if (!metaParam.hasSetter(property)) {
        throw new ExecutorException("No setter found for the keyProperty '" + property + "' in " + metaParam.getOriginalObject().getClass().getName() + ".");
      }
      TypeHandler<?> th = typeHandlers[i];
      if (th != null) {
        Object value = th.getResult(rs, i + 1);
        metaParam.setValue(property, value);
      }
    }
  }

}
