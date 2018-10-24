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
package org.apache.ibatis.session;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 *   程序的入口
 *   用来构建SqlSessionFactory
 *   通过SqlSessionFactory来得到sqlSession,sqlSession负责每一次会话
 *   通过字节流或者是字符流的方式读取xml配置文件
 *   Reader reader =Resources.getResourceAsReader("mybatis-config.xml");
 *   InputStream inputStream=Resources.getResourceAsStream("mybatis-config.xml");
 */

public class SqlSessionFactoryBuilder {

    public SqlSessionFactory build(Reader reader) {
        return build(reader, null, null);
    }

    public SqlSessionFactory build(Reader reader, String environment) {
        return build(reader, environment, null);
    }

    public SqlSessionFactory build(Reader reader, Properties properties) {
        return build(reader, null, properties);
    }

    /**
     * factory = new SqlSessionFactoryBuilder().build(reader);
     * 在通过比如spring结合时通过spring来注入相关的属性
     * factory = new SqlSessionFactoryBuilder().build(reader,"id",new Properties());
     * @param reader  一个读取xml配置文件的流
     * @param environment 可以指定不同的数据源的环境 通过ID来标示
     * @param properties  可以自己加载一个配置文件，此时这个配置的级别应该是最低的，后来加载的如果由重复的属性，会进行覆盖
     * @return
     */
    public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
        try {
            /**
             * 根据不同的xml文件，会对应不同的xxxConfigBuilder,主要是XMLConfigBuilder，MapperConfigBuilder
             */
            XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            /**
             * 在解析xxx.xml配置文件的时候，进行错误上下文的清空，以此来追踪错误。
             */
            ErrorContext.instance().reset();
            try {
                reader.close();
            } catch (IOException e) {
            }
        }
    }

    public SqlSessionFactory build(InputStream inputStream) {
        return build(inputStream, null, null);
    }

    public SqlSessionFactory build(InputStream inputStream, String environment) {
        return build(inputStream, environment, null);
    }

    public SqlSessionFactory build(InputStream inputStream, Properties properties) {
        return build(inputStream, null, properties);
    }

    public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
        try {
            XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            ErrorContext.instance().reset();
            try {
                inputStream.close();
            } catch (IOException e) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

    /**
     * 默认使用DefaultSqlSessionFactory
     * @param config
     * @return
     */
    public SqlSessionFactory build(Configuration config) {
        return new DefaultSqlSessionFactory(config);
    }

}
