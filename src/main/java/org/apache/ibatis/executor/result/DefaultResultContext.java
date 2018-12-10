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

import org.apache.ibatis.session.ResultContext;

/**
 * 默认结果上下文的实现
 */
public class DefaultResultContext implements ResultContext {

    //每一行的结果
    private Object resultObject;
    //已读取的结果的数量
    private int resultCount;
    //是否停止
    private boolean stopped;

    public DefaultResultContext() {
        resultObject = null;
        resultCount = 0;
        //默认是没有停止的
        stopped = false;
    }

    @Override
    public Object getResultObject() {
        return resultObject;
    }

    @Override
    public int getResultCount() {
        return resultCount;
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    /**
     * 每次调用这个方法来迭代结果集，结果集也是需要不断传入的
     *
     * @param resultObject
     */
    public void nextResultObject(Object resultObject) {
        resultCount++;
        this.resultObject = resultObject;
    }

    @Override
    public void stop() {
        this.stopped = true;
    }

}
