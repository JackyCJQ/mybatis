/*
 *    Copyright 2009-2011 the original author or authors.
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
package org.apache.ibatis.session;

import org.apache.ibatis.executor.BatchResult;

import java.io.Closeable;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * 这是MyBatis主要的一个类，用来执行SQL，获取映射器，管理事务
 */
public interface SqlSession extends Closeable {

    /**
     * 根据指定的SqlID获取一条记录的封装对象
     */
    <T> T selectOne(String statement);

    /**
     * 根据指定的SqlID获取一条记录的封装对象，只不过这个方法容许我们可以给sql传递一些参数
     * 一般在实际使用中，这个参数传递的是pojo，或者Map或者ImmutableMap
     */
    <T> T selectOne(String statement, Object parameter);

    /**
     * 根据指定的sqlId获取多条记录
     */
    <E> List<E> selectList(String statement);

    /**
     * 获取多条记录，这个方法容许我们可以传递一些参数
     */
    <E> List<E> selectList(String statement, Object parameter);

    /**
     * 获取多条记录，这个方法容许我们可以传递一些参数，不过这个方法容许我们进行
     * 分页查询。
     * <p>
     * 需要注意的是默认情况下，Mybatis为了扩展性，仅仅支持内存分页。也就是会先把
     * 所有的数据查询出来以后，然后在内存中进行分页。因此在实际的情况中，需要注意
     * 这一点。
     * 一般情况下公司都会编写自己的Mybatis 物理分页插件
     */
    <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds);

    /**
     * 将查询到的结果列表转换为Map类型。
     */
    <K, V> Map<K, V> selectMap(String statement, String mapKey);

    /**
     * 将查询到的结果列表转换为Map类型。这个方法容许我们传入需要的参数
     */
    <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey);

    /**
     * 获取多条记录,加上分页,并存入Map
     */
    <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds);

    /**
     * Retrieve a single row mapped from the statement key and parameter
     * using a {@code ResultHandler}.
     */
    void select(String statement, Object parameter, ResultHandler handler);

    /**
     * 获取一条记录,并转交给ResultHandler处理。这个方法容许我们自己定义对
     * 查询到的行的处理方式。不过一般用的并不是很多
     */
    void select(String statement, ResultHandler handler);

    /**
     * 获取一条记录,加上分页,并转交给ResultHandler处理
     */
    void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler);

    /**
     * 插入记录。一般情况下这个语句在实际项目中用的并不是太多，而且更多使用带参数的insert函数
     */
    int insert(String statement);

    /**
     * 插入记录，容许传入参数。
     */
    int insert(String statement, Object parameter);

    /**
     * 更新记录。返回的是受影响的行数
     */
    int update(String statement);

    /**
     * 更新记录
     */
    int update(String statement, Object parameter);

    /**
     * 删除记录
     */
    int delete(String statement);

    /**
     * 删除记录
     */
    int delete(String statement, Object parameter);

    //以下是事务控制方法,commit,rollback

    /**
     * Flushes batch statements and commits database connection.
     * Note that database connection will not be committed if no updates/deletes/inserts were called.
     * To force the commit call {@link SqlSession#commit(boolean)}
     */
    void commit();

    /**
     * Flushes batch statements and commits database connection.
     *
     * @param force forces connection commit
     */
    void commit(boolean force);

    /**
     * Discards pending batch statements and rolls database connection back.
     * Note that database connection will not be rolled back if no updates/deletes/inserts were called.
     * To force the rollback call {@link SqlSession#rollback(boolean)}
     */
    void rollback();

    /**
     * Discards pending batch statements and rolls database connection back.
     * Note that database connection will not be rolled back if no updates/deletes/inserts were called.
     *
     * @param force forces connection rollback
     */
    void rollback(boolean force);

    /**
     * Flushes batch statements.
     * 刷新批处理语句,返回批处理结果
     *
     * @return BatchResult list of updated records
     * @since 3.0.6
     */
    List<BatchResult> flushStatements();

    /**
     * Closes the session
     * 关闭Session
     */
    @Override
    void close();

    /**
     * Clears local session cache
     * 清理Session缓存
     */
    void clearCache();

    /**
     * Retrieves current configuration
     * 得到配置
     *
     * @return Configuration
     */
    Configuration getConfiguration();

    /**
     * 得到映射器
     * 这个巧妙的使用了泛型，使得类型安全
     * 到了MyBatis 3，还可以用注解,这样xml都不用写了
     */
    <T> T getMapper(Class<T> type);

    /**
     * 得到数据库连接
     *
     * @return Connection
     */
    Connection getConnection();
}
