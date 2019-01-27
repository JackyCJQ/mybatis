/*
 *    Copyright 2009-2013 the original author or authors.
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
package org.apache.ibatis.binding;

import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mapper代理接口生产工厂，就是工厂每次都是new一个出来，其实就是每次都是新建一个，就是相当于一个工厂
 */
public class MapperProxyFactory<T> {
    private final Class<T> mapperInterface;
    //接口中的每个接口需要和sql匹配起来
    private Map<Method, MapperMethod> methodCache = new ConcurrentHashMap<Method, MapperMethod>();

    //只需传递进来一个接口
    public MapperProxyFactory(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    /**
     * 获取真实的接口
     * @return
     */
    public Class<T> getMapperInterface() {
        return mapperInterface;
    }

    /**
     * 获取对应接口所有定义的方法
     * @return
     */
    public Map<Method, MapperMethod> getMethodCache() {
        return methodCache;
    }

    /**
     * 生成一个接口代理实现类
     * @param mapperProxy
     * @return
     */
    @SuppressWarnings("unchecked")
    protected T newInstance(MapperProxy<T> mapperProxy) {
        //    public static Object newProxyInstance(ClassLoader loader,Class<?>[] interfaces,InvocationHandler h)
        //     mapperProxy实现了InvocationHandler
        return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[]{mapperInterface}, mapperProxy);
    }

    /**
     * 生成一个接口代理实现类
     * @param sqlSession
     * @return
     */
    public T newInstance(SqlSession sqlSession) {
        final MapperProxy<T>  mapperProxy = new MapperProxy<T>(sqlSession, mapperInterface, methodCache);
        return newInstance(mapperProxy);
    }

}
