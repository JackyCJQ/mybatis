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
package org.apache.ibatis.executor.result;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;

import java.util.Map;

/**
 * 默认Map结果处理器
 */
public class DefaultMapResultHandler<K, V> implements ResultHandler {

    //内部实现是存了一个Map，key就是指定的Mapkey，V 就是对应数据库的一行数据
    private final Map<K, V> mappedResults;
    //由于返回的是一个map,每个对象需要指定一个key(key可以为pojo中的一个属性值) value-->pojo
    private final String mapKey;
    private final ObjectFactory objectFactory;
    private final ObjectWrapperFactory objectWrapperFactory;

    @SuppressWarnings("unchecked")
    public DefaultMapResultHandler(String mapKey, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory) {
        this.objectFactory = objectFactory;
        this.objectWrapperFactory = objectWrapperFactory;
        //默认创建一个HashMap
        this.mappedResults = objectFactory.create(Map.class);
        this.mapKey = mapKey;
    }

    @Override
    public void handleResult(ResultContext context) {
        //获取每一行结果
        final V value = (V) context.getResultObject();
        final MetaObject mo = MetaObject.forObject(value, objectFactory, objectWrapperFactory);
        //获取结果集中 关于mapkey的值
        final K key = (K) mo.getValue(mapKey);
        //就是把指定的MapKey作为key来进行存放
        mappedResults.put(key, value);
    }

    public Map<K, V> getMappedResults() {
        return mappedResults;
    }
}
