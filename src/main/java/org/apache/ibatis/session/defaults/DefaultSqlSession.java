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
package org.apache.ibatis.session.defaults;

import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.result.DefaultMapResultHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认SqlSession实现
 */
public class DefaultSqlSession implements SqlSession {
    //默认的全局配置
    private Configuration configuration;
    //执行器
    private Executor executor;
    /**
     * 是否自动提交
     */
    private boolean autoCommit;
    //是否是脏数据
    private boolean dirty;

    /**
     * 构建的具体信息，需要有外面传进来
     *
     * @param configuration
     * @param executor
     * @param autoCommit
     */
    public DefaultSqlSession(Configuration configuration, Executor executor, boolean autoCommit) {
        this.configuration = configuration;
        this.executor = executor;
        this.dirty = false;//默认不是脏数据
        this.autoCommit = autoCommit;
    }

    /**
     * 默认 不是自动提交
     *
     * @param configuration
     * @param executor
     */
    public DefaultSqlSession(Configuration configuration, Executor executor) {
        this(configuration, executor, false);
    }

    @Override
    public <T> T selectOne(String statement) {
        return this.<T>selectOne(statement, null);
    }

    //核心selectOne 语句和参数
    @Override
    public <T> T selectOne(String statement, Object parameter) {
        // 特别需要主要的是当没有查询到结果的时候就会返回null。因此一般建议在mapper中编写resultType的时候使用包装类型
        //而不是基本类型，比如推荐使用Integer而不是int。这样就可以避免NPE
        List<T> list = this.<T>selectList(statement, parameter);
        if (list.size() == 1) {
            return list.get(0);
        } else if (list.size() > 1) {
            throw new TooManyResultsException("Expected one result (or null) to be returned by selectOne(), but found: " + list.size());
        } else {
            return null;
        }
    }

    @Override
    public <E> List<E> selectList(String statement) {
        return this.selectList(statement, null);
    }

    @Override
    public <E> List<E> selectList(String statement, Object parameter) {
        return this.selectList(statement, parameter, RowBounds.DEFAULT);
    }

    /**
     * 查询执行的核心操作
     *
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @param rowBounds Bounds to limit object retrieval
     * @param <E>
     * @return
     */
    @Override
    public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
        try {
            //根据statement id找到对应的MappedStatement
            MappedStatement ms = configuration.getMappedStatement(statement);
            //执行器来查询结果，核心的就是这个四个参数的查询
            return executor.query(ms, wrapCollection(parameter), rowBounds, Executor.NO_RESULT_HANDLER);
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
        } finally {
            //每次查询完毕 都会重置错误的上下文 以便每次都是最新的错误
            ErrorContext.instance().reset();
        }
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
        return this.selectMap(statement, null, mapKey, RowBounds.DEFAULT);
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
        return this.selectMap(statement, parameter, mapKey, RowBounds.DEFAULT);
    }

    /**
     * map查询也是带分页的
     *
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @param mapKey    The property to use as key for each value in the list.
     * @param rowBounds Bounds to limit object retrieval
     * @param <K>
     * @param <V>
     * @return
     */
    @Override
    public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
        //先得到查寻的数据 在转化为List
        final List<?> list = selectList(statement, parameter, rowBounds);
        final DefaultMapResultHandler<K, V> mapResultHandler = new DefaultMapResultHandler<K, V>(mapKey,
                configuration.getObjectFactory(), configuration.getObjectWrapperFactory());
        final DefaultResultContext context = new DefaultResultContext();
        for (Object o : list) {
            //循环用DefaultMapResultHandler处理每条记录
            context.nextResultObject(o);
            mapResultHandler.handleResult(context);
        }
        //注意这个DefaultMapResultHandler里面存了所有已处理的记录(内部实现可能就是一个Map)，最后再返回一个Map
        return mapResultHandler.getMappedResults();
    }


    @Override
    public void select(String statement, Object parameter, ResultHandler handler) {
        select(statement, parameter, RowBounds.DEFAULT, handler);
    }

    @Override
    public void select(String statement, ResultHandler handler) {
        select(statement, null, RowBounds.DEFAULT, handler);
    }

    //核心select,带有ResultHandler，和selectList代码差不多的，区别就一个ResultHandler
    @Override
    public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
        try {
            MappedStatement ms = configuration.getMappedStatement(statement);
            executor.query(ms, wrapCollection(parameter), rowBounds, handler);
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
        } finally {
            ErrorContext.instance().reset();
        }
    }

    @Override
    public int insert(String statement) {
        return insert(statement, null);
    }

    @Override
    public int insert(String statement, Object parameter) {
        return update(statement, parameter);
    }

    @Override
    public int update(String statement) {
        return update(statement, null);
    }

    /**
     * 插入和更新的核心操作
     * parameter:是一个map
     */
    @Override
    public int update(String statement, Object parameter) {
        try {
            //每次要更新之前，dirty标志设为true
            dirty = true;
            MappedStatement ms = configuration.getMappedStatement(statement);
            //转而用执行器来update结果
            return executor.update(ms, wrapCollection(parameter));
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error updating database.  Cause: " + e, e);
        } finally {
            ErrorContext.instance().reset();
        }
    }

    @Override
    public int delete(String statement) {
        return update(statement, null);
    }

    @Override
    public int delete(String statement, Object parameter) {
        return update(statement, parameter);
    }

    @Override
    public void commit() {
        commit(false);
    }

    //核心commit
    @Override
    public void commit(boolean force) {
        try {
            //转而用执行器来commit
            executor.commit(isCommitOrRollbackRequired(force));
            //每次commit之后，dirty标志设为false
            dirty = false;
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error committing transaction.  Cause: " + e, e);
        } finally {
            ErrorContext.instance().reset();
        }
    }

    @Override
    public void rollback() {
        rollback(false);
    }

    //核心rollback
    @Override
    public void rollback(boolean force) {
        try {
            //转而用执行器来rollback
            executor.rollback(isCommitOrRollbackRequired(force));
            dirty = false;
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error rolling back transaction.  Cause: " + e, e);
        } finally {
            ErrorContext.instance().reset();
        }
    }

    //核心flushStatements
    @Override
    public List<BatchResult> flushStatements() {
        try {
            return executor.flushStatements();
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error flushing statements.  Cause: " + e, e);
        } finally {
            ErrorContext.instance().reset();
        }
    }

    //核心close
    @Override
    public void close() {
        try {
            //转而用执行器来close
            executor.close(isCommitOrRollbackRequired(false));
            dirty = false;
        } finally {
            ErrorContext.instance().reset();
        }
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public <T> T getMapper(Class<T> type) {
        return configuration.<T>getMapper(type, this);
    }

    @Override
    public Connection getConnection() {
        try {
            return executor.getTransaction().getConnection();
        } catch (SQLException e) {
            throw ExceptionFactory.wrapException("Error getting a new connection.  Cause: " + e, e);
        }
    }

    //核心clearCache
    @Override
    public void clearCache() {
        executor.clearLocalCache();
    }

    private boolean isCommitOrRollbackRequired(boolean force) {
        //强制自动提交或者是不自动提交但是是脏数据 都返回true
        return (!autoCommit && dirty) || force;
    }

    private Object wrapCollection(final Object object) {
        //通过一个新的map，来指示对应的结构类型
        if (object instanceof Collection) {
            StrictMap<Object> map = new StrictMap<Object>();
            map.put("collection", object);
            if (object instanceof List) {
                map.put("list", object);
            }
            return map;
        } else if (object != null && object.getClass().isArray()) {
            StrictMap<Object> map = new StrictMap<Object>();
            //参数若是数组型，，做array标记
            map.put("array", object);
            return map;
        }
        //参数若不是集合型，直接返回原来值
        return object;
    }

    //严格的Map，如果找不到对应的key，直接抛BindingException例外，而不是返回null
    public static class StrictMap<V> extends HashMap<String, V> {

        private static final long serialVersionUID = -5741767162221585340L;

        @Override
        public V get(Object key) {
            if (!super.containsKey(key)) {
                throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + this.keySet());
            }
            return super.get(key);
        }

    }

}
