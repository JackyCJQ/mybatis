/*
 *    Copyright 2009-2011 the original author or authors.
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
package org.apache.ibatis.annotations;

import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义在类上的注解
 * 设定缓存的命名空间
 *
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CacheNamespace {
    //默认的缓存为PerpetualCache类
    Class<? extends org.apache.ibatis.cache.Cache> implementation() default PerpetualCache.class;

    //默认使用的缓存算法为最近最少使用缓存
    Class<? extends org.apache.ibatis.cache.Cache> eviction() default LruCache.class;

    //刷新缓存的间隔
    long flushInterval() default 0;

    //默认缓存大小
    int size() default 1024;

    //可读可写
    boolean readWrite() default true;

    //默认是非阻塞的
    boolean blocking() default false;

}
