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
 * 这个地方声明为抽象类，可以获取参数的类型
 */
public abstract class TypeReference<T> {

    //T 可能为类，可能为范型类型的
    private final Type rawType;

    protected TypeReference() {
        //获取子类在继承父类的时候订单的T的类型
        rawType = getSuperclassTypeParameter(getClass());
    }

    Type getSuperclassTypeParameter(Class<?> clazz) {
        // 获取直接父类
        Type genericSuperclass = clazz.getGenericSuperclass();
        //如果T的直接父类为Class类型,只有在第一个继承TypeReference时才可以指定T的具体类型
        if (genericSuperclass instanceof Class) {
            //一直找到TypeReference类。获取这个地方的T的类型
            if (TypeReference.class != genericSuperclass) {
                return getSuperclassTypeParameter(clazz.getSuperclass());
            }
            throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
                    + "Remove the extension or add a type parameter to it.");
        }
        //获得泛型化的参数类型 public class Test extends TypeReference<List<String>> 获取List类型
        Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
        // TODO remove this when Reflector is fixed to return Types
        //如果还是参数化类型，在获取
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
