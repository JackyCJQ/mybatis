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

    <T> T selectOne(String statement);

    <T> T selectOne(String statement, Object parameter);

    <E> List<E> selectList(String statement);

    <E> List<E> selectList(String statement, Object parameter);

    /**
     * 需要注意的是默认情况下，Mybatis为了扩展性，仅仅支持内存分页。也就是会先把
     * 所有的数据查询出来以后，然后在内存中进行分页。因此在实际的情况中，需要注意
     * 这一点。
     */
    <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds);

    <K, V> Map<K, V> selectMap(String statement, String mapKey);

    <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey);

    <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds);

    void select(String statement, Object parameter, ResultHandler handler);

    void select(String statement, ResultHandler handler);

    void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler);

    int insert(String statement);

    int insert(String statement, Object parameter);

    int update(String statement);

    int update(String statement, Object parameter);

    int delete(String statement);

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

    void clearCache();

    Configuration getConfiguration();

    <T> T getMapper(Class<T> type);

    Connection getConnection();
}
