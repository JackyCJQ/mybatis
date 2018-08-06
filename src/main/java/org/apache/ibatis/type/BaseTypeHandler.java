/*
 *    Copyright 2009-2012 the original author or authors.
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
package org.apache.ibatis.type;

import org.apache.ibatis.session.Configuration;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 * @author Simone Tripodi
 */

/**
 * 类型处理器的基类
 * 
 */
public abstract class BaseTypeHandler<T> extends TypeReference<T> implements TypeHandler<T> {

  //引用全局引用
  protected Configuration configuration;
 //通过set的方式注入
  public void setConfiguration(Configuration c) {
    this.configuration = c;
  }


  //由父类抽象出公共的方法 具体的差异处理交由子类去实现
  @Override
  public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
    //特殊情况，设置NULL
    if (parameter == null) {
      if (jdbcType == null) {
        //如果查询时参数为null并且需要设置参数的时候 jdbc类型就不能为空 否则就不知道 查寻条件的类型能否和数据库中相匹配
        throw new TypeException("JDBC requires that the JdbcType must be specified for all nullable parameters.");
      }
      try {
        //参数设成NULL 对应的数据库字段知道是什么类型的就能设置为了null
        ps.setNull(i, jdbcType.TYPE_CODE);
      } catch (SQLException e) {
        throw new TypeException("Error setting null for parameter #" + i + " with JdbcType " + jdbcType + " . " +
                "Try setting a different JdbcType for this parameter or a different jdbcTypeForNull configuration property. " +
                "Cause: " + e, e);
      }
    } else {
      //非NULL情况，怎么设还得交给不同的子类完成, setNonNullParameter是一个抽象方法
      //根据不同的子类进行参数设置  这里只是由父类统一处理null的情况
      setNonNullParameter(ps, i, parameter, jdbcType);
    }
  }

  @Override
  public T getResult(ResultSet rs, String columnName) throws SQLException {
    // 根据不同的数据库字段 返回相应的java类型 也是交由子类去实现
    T result = getNullableResult(rs, columnName);
    //首先读取 然后调用这个方法判断读取的字段是否是null
    //通过ResultSet.wasNull判断是否为NULL
    if (rs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

  @Override
  public T getResult(ResultSet rs, int columnIndex) throws SQLException {
    T result = getNullableResult(rs, columnIndex);
    if (rs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

  @Override
  public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
    T result = getNullableResult(cs, columnIndex);
	//通过CallableStatement.wasNull判断是否为NULL
    if (cs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

	//非NULL情况，怎么设参数还得交给不同的子类完成
  public abstract void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

	//以下3个方法是取得可能为null的结果，具体交给子类完成
  public abstract T getNullableResult(ResultSet rs, String columnName) throws SQLException;

  public abstract T getNullableResult(ResultSet rs, int columnIndex) throws SQLException;

  public abstract T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException;

}
