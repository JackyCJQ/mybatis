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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 对方法进行反射
 */
public class MethodInvoker implements Invoker {
    //如果是set 则为参数类型。如果是get 则为返回值的类型
    private Class<?> type;
    //要进行反射的方法
    private Method method;

    public MethodInvoker(Method method) {
        this.method = method;
        //如果只有一个参数，认为是set方法
        if (method.getParameterTypes().length == 1) {
            type = method.getParameterTypes()[0];
        } else {
            //没有参数是get方法
            type = method.getReturnType();
        }
    }

    //反射方法
    @Override
    public Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
        return method.invoke(target, args);
    }

    //如果是set方法，则是参数的类型；如果是get方法，则是返回值的类型
    @Override
    public Class<?> getType() {
        return type;
    }
}
