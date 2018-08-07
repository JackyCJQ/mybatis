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
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

/**
 * 执行器基类
 */
public abstract class BaseExecutor implements Executor {

    private static final Log log = LogFactory.getLog(BaseExecutor.class);

    //事务管理
    protected Transaction transaction;
    //代理执行器
    protected Executor wrapper;
    //延迟加载队列（线程安全）
    protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;
    //本地缓存
    protected PerpetualCache localCache;
    //本地输出参数缓存
    protected PerpetualCache localOutputParameterCache;
    //全局配置
    protected Configuration configuration;
    //查询堆栈
    protected int queryStack = 0;
    //是否关闭
    private boolean closed;

    /**
     * 构造函数创建
     *
     * @param configuration
     * @param transaction
     */
    protected BaseExecutor(Configuration configuration, Transaction transaction) {
        this.transaction = transaction;
        this.deferredLoads = new ConcurrentLinkedQueue<DeferredLoad>();
        this.localCache = new PerpetualCache("LocalCache");
        this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
        this.closed = false;
        this.configuration = configuration;
        this.wrapper = this;
    }

    /**
     * 获取事务
     *
     * @return
     */
    @Override
    public Transaction getTransaction() {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        return transaction;
    }

    /**
     * 关闭的时候是否强制回滚
     *
     * @param forceRollback true 强制回滚 false 不会强制回滚
     */
    @Override
    public void close(boolean forceRollback) {
        try {
            try {
                rollback(forceRollback);
            } finally {
                //关闭执行器的时候 也需要关闭关联的事务
                if (transaction != null) {
                    transaction.close();
                }
            }
        } catch (SQLException e) {
            // Ignore.  There's nothing that can be done at this point.
            log.warn("Unexpected exception on closing transaction.  Cause: " + e);
        } finally {
            //关闭的时候把 事务 缓存清空
            transaction = null;
            deferredLoads = null;
            localCache = null;
            localOutputParameterCache = null;
            closed = true;
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * SqlSession.update/insert/delete会调用此方法
     *
     * @param ms
     * @param parameter
     * @return
     * @throws SQLException
     */
    @Override
    public int update(MappedStatement ms, Object parameter) throws SQLException {
        ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        //先清局部缓存，再更新，如何更新交由子类，模板方法模式
        clearLocalCache();
        return doUpdate(ms, parameter);
    }

    @Override
    public List<BatchResult> flushStatements() throws SQLException {
        return flushStatements(false);
    }

    /**
     * 刷新语句，Batch用
     *
     * @param isRollBack 是否需要回滚
     * @return
     * @throws SQLException
     */
    public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
        //执行器关闭 就不存在以下处理
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        //交由具体的子类去实现
        return doFlushStatements(isRollBack);
    }

    /**
     * SqlSession.selectList会调用此方法
     *
     * @param ms
     * @param parameter
     * @param rowBounds
     * @param resultHandler
     * @param <E>
     * @return
     * @throws SQLException
     */
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
        //得到绑定sql
        BoundSql boundSql = ms.getBoundSql(parameter);
        //创建缓存Key
        CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
        //查询
        return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
    }

    /**
     * 核心的查询语句
     *
     * @param ms
     * @param parameter
     * @param rowBounds
     * @param resultHandler
     * @param key
     * @param boundSql
     * @param <E>
     * @return
     * @throws SQLException
     */
    @SuppressWarnings("unchecked")
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
        ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
        //如果已经关闭，报错
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        //仅查询堆栈为0，且在配置文件中配置了需要清空缓存
        if (queryStack == 0 && ms.isFlushCacheRequired()) {
            clearLocalCache();
        }
        List<E> list;
        try {
            //查询堆栈+1
            queryStack++;
            //先根据cachekey从localCache去查，是否存在缓存
            list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
            //如果缓存中存在
            if (list != null) {
                //若查到localCache缓存，处理localOutputParameterCache，把存储过程的缓存也添加进入localOutputParameterCache
                handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
            } else {
                //从数据库查
                list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
            }
        } finally {
            //查询堆栈--
            queryStack--;
        }
        //
        if (queryStack == 0) {
            //延迟加载队列中所有元素
            for (DeferredLoad deferredLoad : deferredLoads) {
                deferredLoad.load();
            }
            //清空延迟加载队列
            deferredLoads.clear();
            //如果是STATEMENT，清本地缓存
            if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
                // issue #482
                clearLocalCache();
            }
        }
        return list;
    }

    /**
     * 延迟加载，DefaultResultSetHandler.getNestedQueryMappingValue调用.属于嵌套查询
     *
     * @param ms
     * @param resultObject
     * @param property
     * @param key
     * @param targetType
     */
    @Override
    public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
        //如果能加载，则立刻加载，否则加入到延迟加载队列中，如果有查询正在执行，则不能加载，
        if (deferredLoad.canLoad()) {
            deferredLoad.load();
        } else {
            //如果不能加载 则添加一个进入
            deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
        }
    }

    /**
     * 创建缓存的key
     *
     * @param ms
     * @param parameterObject
     * @param rowBounds
     * @param boundSql
     * @return
     */
    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        CacheKey cacheKey = new CacheKey();
        //MyBatis 对于其 Key 的生成采取规则为：[mappedStementId + offset + limit + SQL + queryParams + environment]生成一个哈希码
        cacheKey.update(ms.getId());
        cacheKey.update(Integer.valueOf(rowBounds.getOffset()));
        cacheKey.update(Integer.valueOf(rowBounds.getLimit()));
        cacheKey.update(boundSql.getSql());
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
        //模仿DefaultParameterHandler的逻辑,不再重复，请参考DefaultParameterHandler
        for (int i = 0; i < parameterMappings.size(); i++) {
            ParameterMapping parameterMapping = parameterMappings.get(i);
            if (parameterMapping.getMode() != ParameterMode.OUT) {
                Object value;
                String propertyName = parameterMapping.getProperty();
                if (boundSql.hasAdditionalParameter(propertyName)) {
                    value = boundSql.getAdditionalParameter(propertyName);
                } else if (parameterObject == null) {
                    value = null;
                } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                    value = parameterObject;
                } else {
                    MetaObject metaObject = configuration.newMetaObject(parameterObject);
                    value = metaObject.getValue(propertyName);
                }
                cacheKey.update(value);
            }
        }
        if (configuration.getEnvironment() != null) {
            // issue #176
            cacheKey.update(configuration.getEnvironment().getId());
        }
        return cacheKey;
    }

    @Override
    public boolean isCached(MappedStatement ms, CacheKey key) {
        return localCache.getObject(key) != null;
    }

    /**
     * 提交事务
     *
     * @param required
     * @throws SQLException
     */
    @Override
    public void commit(boolean required) throws SQLException {
        if (closed) {
            throw new ExecutorException("Cannot commit, transaction is already closed");
        }
        //清楚缓存
        clearLocalCache();
        flushStatements();
        //提交事务
        if (required) {
            transaction.commit();
        }
    }

    /**
     * 是否强制回滚事务
     *
     * @param required
     * @throws SQLException
     */
    @Override
    public void rollback(boolean required) throws SQLException {
        //如果执行器已经关闭 就不存在以下逻辑处理
        if (!closed) {
            try {
                //在关闭的时候清除本地缓存 冲刷语句
                clearLocalCache();
                flushStatements(true);
            } finally {
                if (required) {
                    //事务回滚
                    transaction.rollback();
                }
            }
        }
    }

    /**
     * 清除本地缓存。本地输出参数缓存
     */
    @Override
    public void clearLocalCache() {
        if (!closed) {
            localCache.clear();
            localOutputParameterCache.clear();
        }
    }

    /**
     * 有具体的子类去实现数据库更新操作
     *
     * @param ms
     * @param parameter
     * @return
     * @throws SQLException
     */
    protected abstract int doUpdate(MappedStatement ms, Object parameter)
            throws SQLException;

    /**
     * 有具体的子类去实现数据库批量刷新
     *
     * @param isRollback
     * @return
     * @throws SQLException
     */
    protected abstract List<BatchResult> doFlushStatements(boolean isRollback)
            throws SQLException;

    /**
     * 有具体的子类去实现数据库查询
     *
     * @param ms
     * @param parameter
     * @param rowBounds
     * @param resultHandler
     * @param boundSql
     * @param <E>
     * @return
     * @throws SQLException
     */
    protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException;

    protected void closeStatement(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    /**
     * 处理存储过程的OUT参数
     *
     * @param ms
     * @param key
     * @param parameter
     * @param boundSql
     */
    private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
        //处理存储过程的OUT参数
        if (ms.getStatementType() == StatementType.CALLABLE) {
            //在本地缓存中查找
            final Object cachedParameter = localOutputParameterCache.getObject(key);
            //如果之前执行的时候缓存过了 并且输入的参数也不为空
            if (cachedParameter != null && parameter != null) {
                //
                final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
                final MetaObject metaParameter = configuration.newMetaObject(parameter);

                for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
                    //如果参数是out 或者是inout 说明是 存储过程的参数
                    if (parameterMapping.getMode() != ParameterMode.IN) {
                        final String parameterName = parameterMapping.getProperty();
                        final Object cachedValue = metaCachedParameter.getValue(parameterName);
                        metaParameter.setValue(parameterName, cachedValue);
                    }
                }
            }
        }
    }

    /**
     * 从数据库中查寻结果
     *
     * @param ms
     * @param parameter
     * @param rowBounds
     * @param resultHandler
     * @param key
     * @param boundSql
     * @param <E>
     * @return
     * @throws SQLException
     */
    private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
        List<E> list;
        //先向缓存中放入占位符，防止其他引用这个缓存
        localCache.putObject(key, EXECUTION_PLACEHOLDER);
        try {
            list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
        } finally {
            //最后删除占位符
            localCache.removeObject(key);
        }
        //把查询的结果加入缓存
        localCache.putObject(key, list);
        //如果是存储过程，把参数也加入缓存
        if (ms.getStatementType() == StatementType.CALLABLE) {
            localOutputParameterCache.putObject(key, parameter);
        }
        return list;
    }

    /**
     * 获取一个数据库连接
     *
     * @param statementLog
     * @return
     * @throws SQLException
     */
    protected Connection getConnection(Log statementLog) throws SQLException {
        Connection connection = transaction.getConnection();
        if (statementLog.isDebugEnabled()) {
            //如果需要打印Connection的日志，返回一个ConnectionLogger(代理模式, AOP思想)，增加sql细节执行的日志
            return ConnectionLogger.newInstance(connection, statementLog, queryStack);
        } else {
            return connection;
        }
    }

    @Override
    public void setExecutorWrapper(Executor wrapper) {
        this.wrapper = wrapper;
    }

    /**
     * 延迟加载类
     */
    private static class DeferredLoad {
        //元对象属性
        private final MetaObject resultObject;
        //属性
        private final String property;
        //目标类型
        private final Class<?> targetType;
        //缓存的key
        private final CacheKey key;
        //本地缓存
        private final PerpetualCache localCache;
        //对象工厂
        private final ObjectFactory objectFactory;
        //结果抽取器
        private final ResultExtractor resultExtractor;

        // issue #781
        public DeferredLoad(MetaObject resultObject,
                            String property,
                            CacheKey key,
                            PerpetualCache localCache,
                            Configuration configuration,
                            Class<?> targetType) {
            this.resultObject = resultObject;
            this.property = property;
            this.key = key;
            this.localCache = localCache;
            this.objectFactory = configuration.getObjectFactory();
            this.resultExtractor = new ResultExtractor(configuration, objectFactory);
            this.targetType = targetType;
        }

        public boolean canLoad() {
            //缓存中找到，且不为占位符，代表可以加载
            return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
        }

        //加载
        public void load() {
            //得到缓存的查寻结果
            List<Object> list = (List<Object>) localCache.getObject(key);
            //调用ResultExtractor.extractObjectFromList
            Object value = resultExtractor.extractObjectFromList(list, targetType);
            resultObject.setValue(property, value);
        }

    }

}
