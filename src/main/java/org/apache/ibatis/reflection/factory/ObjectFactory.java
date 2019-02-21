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
package org.apache.ibatis.reflection.factory;

import java.util.List;
import java.util.Properties;


/**
 * 对象工厂，所有对象都要由工厂来产生
 */
public interface ObjectFactory {
    //工厂初始化时候可以设置一些属性
    void setProperties(Properties properties);

    //无参构造器创建
    <T> T create(Class<T> type);

    //有参构造器创建
    <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);

    //判断创建的是否是集合
    <T> boolean isCollection(Class<T> type);

}
