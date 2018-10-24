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
package org.apache.ibatis.builder;

import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 配置文件解析的基础类  builder模式
 */
public abstract class BaseBuilder {
    //全局配置
    protected final Configuration configuration;
    //类型别名注册机
    protected final TypeAliasRegistry typeAliasRegistry;
    //类型处理器注册机
    protected final TypeHandlerRegistry typeHandlerRegistry;

    public BaseBuilder(Configuration configuration) {
        this.configuration = configuration;
        this.typeAliasRegistry = this.configuration.getTypeAliasRegistry();
        this.typeHandlerRegistry = this.configuration.getTypeHandlerRegistry();
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * 解析正则表达式
     *
     * @param regex        正则表达式
     * @param defaultValue 默认值
     * @return
     */
    protected Pattern parseExpression(String regex, String defaultValue) {
        return Pattern.compile(regex == null ? defaultValue : regex);
    }

    /**
     * 布尔类型
     *
     * @param value
     * @param defaultValue
     * @return
     */
    protected Boolean booleanValueOf(String value, Boolean defaultValue) {
        return value == null ? defaultValue : Boolean.valueOf(value);
    }

    /**
     * integer类型
     *
     * @param value
     * @param defaultValue
     * @return
     */
    protected Integer integerValueOf(String value, Integer defaultValue) {
        return value == null ? defaultValue : Integer.valueOf(value);
    }

    /**
     * 切割以,分割的字符串
     *
     * @param value
     * @param defaultValue
     * @return
     */
    protected Set<String> stringSetValueOf(String value, String defaultValue) {
        value = (value == null ? defaultValue : value);
        return new HashSet<String>(Arrays.asList(value.split(",")));
    }

    /**
     * 根据名称 解析成对应的枚举类型
     *
     * @param alias jdbc类型的字符串
     * @return
     */
    protected JdbcType resolveJdbcType(String alias) {
        if (alias == null) {
            return null;
        }
        try {
            return JdbcType.valueOf(alias);
        } catch (IllegalArgumentException e) {
            throw new BuilderException("Error resolving JdbcType. Cause: " + e, e);
        }
    }

    /**
     * 根据名称解析ResultSetType，获取对应的枚举值
     *
     * @param alias
     * @return FORWARD_ONLY SCROLL_INSENSITIVE  SCROLL_SENSITIVE 中的一个
     */
    protected ResultSetType resolveResultSetType(String alias) {
        if (alias == null) {
            return null;
        }
        try {
            return ResultSetType.valueOf(alias);
        } catch (IllegalArgumentException e) {
            throw new BuilderException("Error resolving ResultSetType. Cause: " + e, e);
        }
    }

    /**
     * 根据名字解析ParameterMode类型，获取对应的枚举值
     *
     * @param alias
     * @return IN OUT  INOUT 中的一种
     */
    protected ParameterMode resolveParameterMode(String alias) {
        if (alias == null) {
            return null;
        }
        try {
            return ParameterMode.valueOf(alias);
        } catch (IllegalArgumentException e) {
            throw new BuilderException("Error resolving ParameterMode. Cause: " + e, e);
        }
    }

    /**
     * 根据别名解析Class，然后创建一个无参的实例
     * alias是一个已经注册进类名注册机中的类
     * 或是 完整类名，通过反射创建
     * 在配置一些外部类的时候，通过此方法进行加载
     *
     * @param alias typeAliasRegistry中注册的类型别名
     * @return
     */
    protected Object createInstance(String alias) {
        Class<?> clazz = resolveClass(alias);
        if (clazz == null) {
            return null;
        }
        try {
            //注意 ：在原来的类型注册中必须得有一个无参的构造函数
            return resolveClass(alias).newInstance();
        } catch (Exception e) {
            throw new BuilderException("Error creating instance. Cause: " + e, e);
        }
    }

    /**
     * 根据别名查找对应的注册的Class类
     *
     * @param alias
     * @return
     */
    protected Class<?> resolveClass(String alias) {
        if (alias == null) {
            return null;
        }
        try {
            return resolveAlias(alias);
        } catch (Exception e) {
            throw new BuilderException("Error resolving class. Cause: " + e, e);
        }
    }

    /**
     * 查找对应的类型处理器，这个是单例的
     *
     * @param javaType         java类型
     * @param typeHandlerAlias 注册的类型处理器别名（这个时必须的）
     * @return
     */
    protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, String typeHandlerAlias) {
        if (typeHandlerAlias == null) {
            return null;
        }
        //根据类型别名或者是完成的类名 查找是否已经注册
        Class<?> type = resolveClass(typeHandlerAlias);
        //如果自己声明类型处理器，必须要继承TypeHandler
        if (type != null && !TypeHandler.class.isAssignableFrom(type)) {
            throw new BuilderException("Type " + type.getName() + " is not a valid TypeHandler because it does not implement TypeHandler interface");
        }
        //强转为TypeHandler，就可以调用TypeHandler中预先声明的方法，约定在先
        Class<? extends TypeHandler<?>> typeHandlerType = (Class<? extends TypeHandler<?>>) type;
        return resolveTypeHandler(javaType, typeHandlerType);
    }

    /**
     * 根据Java属性类型 查找对应的类型处理器
     *
     * @param javaType
     * @param typeHandlerType
     * @return
     */
    protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, Class<? extends TypeHandler<?>> typeHandlerType) {
        if (typeHandlerType == null) {
            return null;
        }
        //typeHandlerRegistry 提前注册好了每个Class与其一个实例 每次应用不用在重复创建，类似于单例模式
        TypeHandler<?> handler = typeHandlerRegistry.getMappingTypeHandler(typeHandlerType);
        if (handler == null) {
            //如果没有注册，调用typeHandlerRegistry.getInstance来new一个TypeHandler返回
            handler = typeHandlerRegistry.getInstance(javaType, typeHandlerType);
        }
        return handler;
    }

    /**
     * 查找typeAliasRegistry中是否存在整个别名对应的Class，也可以是全类名
     *
     * @param alias
     * @return
     */
    protected Class<?> resolveAlias(String alias) {
        return typeAliasRegistry.resolveAlias(alias);
    }
}
