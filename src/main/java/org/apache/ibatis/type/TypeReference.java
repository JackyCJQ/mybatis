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
package org.apache.ibatis.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * References a generic type.
 * 这个地方声明为抽象类，可以获取参数的类型
 *
 * @param <T> the referenced type
 * @author Simone Tripodi
 * @since 3.1.0
 */
public abstract class TypeReference<T> {

    //引用的原生类型
    private final Type rawType;

    protected TypeReference() {
        //初始化子类的时候回有限初始化父类
        rawType = getSuperclassTypeParameter(getClass());
    }

    Type getSuperclassTypeParameter(Class<?> clazz) {
        // 获取到直接父类
        Type genericSuperclass = clazz.getGenericSuperclass();
        //如果T的直接父类为Class类型
        if (genericSuperclass instanceof Class) {
            // try to climb up the hierarchy until meet something useful
            //找到最终的父类为TypeReference，因为最原始的就是在这个地方定义的
            if (TypeReference.class != genericSuperclass) {
                return getSuperclassTypeParameter(clazz.getSuperclass());
            }
            throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
                    + "Remove the extension or add a type parameter to it.");
        }
        //获得第一个泛型化的参数
        Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
        // TODO remove this when Reflector is fixed to return Types
        //如果参数化的类型还是参数化
        if (rawType instanceof ParameterizedType) {
            rawType = ((ParameterizedType) rawType).getRawType();
        }
        //声明此类型的类或接口
        return rawType;
    }

    public final Type getRawType() {
        return rawType;
    }

    @Override
    public String toString() {
        return rawType.toString();
    }

}
