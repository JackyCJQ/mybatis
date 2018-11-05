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
package org.apache.ibatis.binding;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * mapper接口代理类实现
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

    private static final long serialVersionUID = -6424540398559729838L;
    //代理类实际执行的时候，需要在session中执行，因为需要和对应的数据环境，事务等结合在一起才能执行
    private final SqlSession sqlSession;
    //用户定义的mapper接口
    private final Class<T> mapperInterface;
    //接口中每一个方法 需要和mapper.xml中的sql信息进行匹配，才能通过方法去调用对应的sql去执行
    private final Map<Method, MapperMethod> methodCache;

    public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
        this.sqlSession = sqlSession;
        this.mapperInterface = mapperInterface;
        this.methodCache = methodCache;
    }

    /**
     * 代理类方法执行的入口
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //如果这个方法是Object中声明的方法（toString、hashCode等）无需代理 直接执行
        if (Object.class.equals(method.getDeclaringClass())) {
            try {
                return method.invoke(this, args);
            } catch (Throwable t) {
                throw ExceptionUtil.unwrapThrowable(t);
            }
        }
        //将sql与接口中定义的方法整合起来
        final MapperMethod mapperMethod = cachedMapperMethod(method);
        //代理方法去实际执行
        return mapperMethod.execute(sqlSession, args);
    }

    /**
     * 把mapper接口中的方法和mapper.xml中的sql语句整合起来，这样就能直接通过方法调用sql语句
     *
     * @param method
     * @return
     */
    private MapperMethod cachedMapperMethod(Method method) {
        MapperMethod mapperMethod = methodCache.get(method);
        if (mapperMethod == null) {
            mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
            methodCache.put(method, mapperMethod);
        }
        return mapperMethod;
    }

}
