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
package org.apache.ibatis.session.defaults;

import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 默认的SqlSessionFactory
 */
public class DefaultSqlSessionFactory implements SqlSessionFactory {

    private final Configuration configuration;

    // 创建默认的session工厂
    public DefaultSqlSessionFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    //最终都会调用2种方法：openSessionFromDataSource,openSessionFromConnection
    //以下6个方法都会调用openSessionFromDataSource
    @Override
    public SqlSession openSession() {
        //如果不指定执行器类型 就使用默认的
        return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, false);
    }

    @Override
    public SqlSession openSession(boolean autoCommit) {
        return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, autoCommit);
    }

    @Override
    public SqlSession openSession(ExecutorType execType) {
        return openSessionFromDataSource(execType, null, false);
    }

    @Override
    public SqlSession openSession(TransactionIsolationLevel level) {
        return openSessionFromDataSource(configuration.getDefaultExecutorType(), level, false);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
        return openSessionFromDataSource(execType, level, false);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
        return openSessionFromDataSource(execType, null, autoCommit);
    }

    //以下2个方法都会调用openSessionFromConnection
    @Override
    public SqlSession openSession(Connection connection) {
        return openSessionFromConnection(configuration.getDefaultExecutorType(), connection);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, Connection connection) {
        return openSessionFromConnection(execType, connection);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {
        Transaction tx = null;
        try {
            //获取解析配置文件得到的数据库环境
            final Environment environment = configuration.getEnvironment();
            //根据一个环境获取一个相应的事务
            final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
            //通过事务工厂来产生一个事务
            tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
            //生成一个执行器(事务包含在执行器里)
            final Executor executor = configuration.newExecutor(tx, execType);
            //然后产生一个DefaultSqlSession
            return new DefaultSqlSession(configuration, executor, autoCommit);
        } catch (Exception e) {
            //如果打开事务出错，则关闭它
            closeTransaction(tx); // may have fetched a connection so lets call close()
            throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
        } finally {
            //最后清空错误上下文
            ErrorContext.instance().reset();
        }
    }

    private SqlSession openSessionFromConnection(ExecutorType execType, Connection connection) {
        try {
            boolean autoCommit;
            try {
                autoCommit = connection.getAutoCommit();
            } catch (SQLException e) {
                // Failover to true, as most poor drivers
                // or databases won't support transactions
                autoCommit = true;
            }
            final Environment environment = configuration.getEnvironment();
            final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
            final Transaction tx = transactionFactory.newTransaction(connection);
            //都是需要创建事务和执行器
            final Executor executor = configuration.newExecutor(tx, execType);
            return new DefaultSqlSession(configuration, executor, autoCommit);
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
        } finally {
            ErrorContext.instance().reset();
        }
    }

    /**
     * 获取环境对应的事务
     *
     * @param environment
     * @return
     */
    private TransactionFactory getTransactionFactoryFromEnvironment(Environment environment) {
        //如果没有配置事务工厂，则返回托管事务工厂
        if (environment == null || environment.getTransactionFactory() == null) {
            //默认返回托管的事务工厂
            return new ManagedTransactionFactory();
        }
        return environment.getTransactionFactory();
    }

    /**
     * 关闭事务控制
     *
     * @param tx
     */
    private void closeTransaction(Transaction tx) {
        if (tx != null) {
            try {
                tx.close();
            } catch (SQLException ignore) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

}
