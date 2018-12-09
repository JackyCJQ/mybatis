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
package org.apache.ibatis.mapping;

import java.sql.ResultSet;
/**
 * 结果集类型，不同的结果集也是对应不同的
 */
public enum ResultSetType {
  //结果集的游标只能往前走
  FORWARD_ONLY(ResultSet.TYPE_FORWARD_ONLY),
  //结果集是可滚动的，但是对数据的变化不敏感
  SCROLL_INSENSITIVE(ResultSet.TYPE_SCROLL_INSENSITIVE),
  //结果集是可滚动的，但是对结果集变化敏感
  SCROLL_SENSITIVE(ResultSet.TYPE_SCROLL_SENSITIVE);
  //枚举类一般都是私有的属性
  private int value;

  ResultSetType(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
