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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * 对字段进行反射
 * 
 */
public class SetFieldInvoker implements Invoker {
  //要反射赋值的字段
  private Field field;

  public SetFieldInvoker(Field field) {
    this.field = field;
  }

  //反射为字段赋值
  @Override
  public Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
   //set放射就需要一个参数即可
    field.set(target, args[0]);
    return null;
  }
  //获取字段的类型
  @Override
  public Class<?> getType() {
    return field.getType();
  }
}
