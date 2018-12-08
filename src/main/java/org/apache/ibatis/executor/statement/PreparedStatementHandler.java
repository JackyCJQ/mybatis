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
package org.apache.ibatis.executor.statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.*;
import java.util.List;

/**
 * 预处理语句处理器(PREPARED)
 */
public class PreparedStatementHandler extends BaseStatementHandler {

    public PreparedStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
    }

    /**
     * 真正执行操作的地方
     * @param statement
     * @return 返回更新的行数
     * @throws SQLException
     */
    @Override
    public int update(Statement statement) throws SQLException {
        //调用PreparedStatement.execute和PreparedStatement.getUpdateCount
        PreparedStatement ps = (PreparedStatement) statement;
       //这里开始真正就行了sql的执行
        ps.execute();
        //更新的行数
        int rows = ps.getUpdateCount();
        //实际传入的参数值
        Object parameterObject = boundSql.getParameterObject();
        //获取主键生成器
        KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
        //在执行完之后，只能利用后置的主键生成器，回填主键
        keyGenerator.processAfter(executor, mappedStatement, ps, parameterObject);
        return rows;
    }

    @Override
    public void batch(Statement statement) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        ps.addBatch();
    }

    @Override
    public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        //准备执行
        ps.execute();
        //利用结果处理器进行处理结果
        return resultSetHandler.<E>handleResultSets(ps);
    }

    @Override
    protected Statement instantiateStatement(Connection connection) throws SQLException {
        //调用Connection.prepareStatement
        String sql = boundSql.getSql();
        //对于插入操作
        if (mappedStatement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
            //得到主键的列
            String[] keyColumnNames = mappedStatement.getKeyColumns();
            if (keyColumnNames == null) {
                //利用数据库生成主键
                return connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            } else {
                //自定义的主键序列
                return connection.prepareStatement(sql, keyColumnNames);
            }
        } else if (mappedStatement.getResultSetType() != null) {
            return connection.prepareStatement(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
        } else {
            //对于查询操作
            return connection.prepareStatement(sql);
        }
    }

    @Override
    public void parameterize(Statement statement) throws SQLException {
        //调用ParameterHandler.setParameters
        parameterHandler.setParameters((PreparedStatement) statement);
    }

}
