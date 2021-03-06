/*
 *    Copyright 2009-2014 the original author or authors.
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

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.SQLException;
import java.util.List;

/**
 * 执行器,定义执行的方法
 */
public interface Executor {

    ResultHandler NO_RESULT_HANDLER = null;

    /**
     * 更新操作
     */
    int update(MappedStatement ms, Object parameter) throws SQLException;

    /**
     * 查询操作
     */
    <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

    /**
     * 查询操作
     */
    <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;

    /**
     * 批量刷新操作
     */
    List<BatchResult> flushStatements() throws SQLException;

    /**
     * 提交操作
     */
    void commit(boolean required) throws SQLException;

    /**
     * 回滚操作
     */
    void rollback(boolean required) throws SQLException;

    /**
     * 创建缓存key
     */
    CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

    /**
     * 是否存在缓存
     */
    boolean isCached(MappedStatement ms, CacheKey key);

    /**
     * 清理缓存
     */
    void clearLocalCache();

    /**
     * 延迟加载
     */
    void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

    /**
     * 获取事务
     */
    Transaction getTransaction();

    /**
     * 关闭执行器
     */
    void close(boolean forceRollback);

    /**
     * 是否是关闭状态
     */
    boolean isClosed();

    /**
     * 封装执行器
     */
    void setExecutorWrapper(Executor executor);

}
