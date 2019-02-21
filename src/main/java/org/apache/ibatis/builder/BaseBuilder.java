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
 * 配置文件解析的基础类 ，主要是提供配置的解析的基础工具
 * builder模式
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
        //以下两个属性 在new configuration的时候已经进行了初始化
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
     * @param alias jdbc类型枚举名字
     * @return
     */
    protected JdbcType resolveJdbcType(String alias) {
        if (alias == null) {
            return null;
        }
        try {
            //枚举类 如果不存在，说明需要的jdbc类型没有注册，就会抛出异常
            return JdbcType.valueOf(alias);
        } catch (IllegalArgumentException e) {
            throw new BuilderException("Error resolving JdbcType. Cause: " + e, e);
        }
    }

    /**
     * 根据名称解析ResultSetType，获取对应的枚举值
     *
     * @param alias
     * @return FORWARD_ONLY ：只能顺序遍历 SCROLL_INSENSITIVE ：对指针回滚，不敏感  SCROLL_SENSITIVE：对指针回退敏感
     */
    protected ResultSetType resolveResultSetType(String alias) {
        if (alias == null) {
            return null;
        }
        try {
            //解析到对应的枚举类型
            return ResultSetType.valueOf(alias);
        } catch (IllegalArgumentException e) {
            throw new BuilderException("Error resolving ResultSetType. Cause: " + e, e);
        }
    }

    /**
     * 根据名字解析ParameterMode类型，获取对应的枚举值
     *
     * @param alias
     * @return IN：输入 OUT：输出（存储过程）  INOUT（存储过程） 中的一种
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
     * alias是一个已经注册进TypeAliasRegistry中的类或是完整类名，
     * 如果是已经注册的直接获取，如果是完整的类名通过反射创建
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
     * @param typeHandlerAlias 注册的类型处理器别名或者是class文件的全路径（这个是必须的）
     * @return
     */
    protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, String typeHandlerAlias) {
        if (typeHandlerAlias == null) {
            return null;
        }
        //如果这个类型处理器已经通过别名注册了，则直接获取这个class,如果是类的全路径，则重新加载这个class文件
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
     * @param javaType        类型处理器对应的Java类型
     * @param typeHandlerType 类型处理器
     * @return
     */
    protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, Class<? extends TypeHandler<?>> typeHandlerType) {
        if (typeHandlerType == null) {
            return null;
        }
        //typeHandlerRegistry 提前注册好了每个Class与其一个实例 每次使用的时候不用在重复创建，类似于单例模式
        TypeHandler<?> handler = typeHandlerRegistry.getMappingTypeHandler(typeHandlerType);
        if (handler == null) {
            //如果是自定义的类型注册器，则没有注册，调用typeHandlerRegistry.getInstance来new一个TypeHandler返回，并且不会注册进去，所以
            //对于自定义的类型处理器，每次都是new 一个新的对象来处理
            handler = typeHandlerRegistry.getInstance(javaType, typeHandlerType);
        }
        return handler;
    }

    /**
     * 查找typeAliasRegistry中是否存在这个别名，如果是类的全路径，则会重新加载这个class文件
     *
     * @param alias
     * @return
     */
    protected Class<?> resolveAlias(String alias) {
        return typeAliasRegistry.resolveAlias(alias);
    }
}
