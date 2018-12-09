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

/**
 *
 * 反射接口，实现对字段，方法的反射获取、设置值
 * 
 */
public interface Invoker {
  //类的反射
  Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

  //如果是对字段的反射，则为字段的类型；如果是对方法的反射，则为返回值的类型或者是参数的类型
  Class<?> getType();
}
