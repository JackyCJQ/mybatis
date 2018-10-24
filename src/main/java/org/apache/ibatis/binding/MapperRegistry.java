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

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

import java.util.*;

/**
 * mapper接口注册机
 */
public class MapperRegistry {

    private Configuration config;
    //每一个mapper接口 都有一个对应mapperProxy生产工厂
    private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<Class<?>, MapperProxyFactory<?>>();

    public MapperRegistry(Configuration config) {
        this.config = config;
    }

    //返回代理类 接口需要在session中获取，也就是需要和特定的session关联在一起
    public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
        //获取接口对应的生产工厂
        final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
        if (mapperProxyFactory == null) {
            throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
        }
        try {
            // 根据工厂生产一个mapper接口实例
            return mapperProxyFactory.newInstance(sqlSession);
        } catch (Exception e) {
            throw new BindingException("Error getting mapper instance. Cause: " + e, e);
        }
    }

    /**
     * 查看是否已经注册过
     * @param type 要注册的接口
     * @param <T>
     * @return
     */
    public <T> boolean hasMapper(Class<T> type) {
        return knownMappers.containsKey(type);
    }

    /**
     * 如果配置文件中是通过接口的方式来进行注册
     * @param type
     * @param <T>
     */
    public <T> void addMapper(Class<T> type) {
        if (type.isInterface()) {
            //默认每个接口只能注册一次
            if (hasMapper(type)) {
                throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
            }
            boolean loadCompleted = false;
            try {
                //动态代理的实现，默认给每个接口生成一个代理工厂
                knownMappers.put(type, new MapperProxyFactory<T>(type));
                //解析接口上添加的注解信息
                MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
                parser.parse();
                loadCompleted = true;
            } finally {
                //如果加载过程中出现异常需要再将这个mapper从mybatis中删除
                if (!loadCompleted) {
                    //如果加载失败了 需要移除
                    knownMappers.remove(type);
                }
            }
        }
    }

    /**
     * 返回所有的注册的mapper
     *
     * @since 3.2.2
     */
    public Collection<Class<?>> getMappers() {
        return Collections.unmodifiableCollection(knownMappers.keySet());
    }

    /**
     * 把一个包下所有的mapper注册进来
     *
     * @since 3.2.2
     */
    public void addMappers(String packageName, Class<?> superType) {
        //查找包下所有是superType的类
        ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<Class<?>>();
        resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
        Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
        //循环遍历一个包下的内容 添加进来
        for (Class<?> mapperClass : mapperSet) {
            addMapper(mapperClass);
        }
    }

    /**
     * @since 3.2.2
     */
    //查找包下所有类 默认父类为Object.class，所有的类默认父类就是Object
    public void addMappers(String packageName) {
        addMappers(packageName, Object.class);
    }

}
