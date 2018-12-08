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

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认结果处理器
 */
public class DefaultResultHandler implements ResultHandler {

    //内部实现是存了一个List
    private final List<Object> list;

    public DefaultResultHandler() {
        list = new ArrayList<Object>();
    }

    @SuppressWarnings("unchecked")
    public DefaultResultHandler(ObjectFactory objectFactory) {
        //创建一个空的list
        list = objectFactory.create(List.class);
    }

    @Override
    public void handleResult(ResultContext context) {
        //处理很简单，就是把记录加入List
        list.add(context.getResultObject());
    }

    public List<Object> getResultList() {
        return list;
    }

}
