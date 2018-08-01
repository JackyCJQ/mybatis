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
package org.apache.ibatis.session;

import java.sql.Connection;

/**
 * 事务隔离级别，是一个枚举型
 * 
 */
public enum TransactionIsolationLevel {
  /**
   * 包括jdbc支持的5种事务级别
   */
  //什么都不设置 会出现脏读 幻读 不可重复读
  NONE(Connection.TRANSACTION_NONE),
  //会出现不可重复读
  READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
  //会出现脏读
  READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
  //会出现幻读
  REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
  //这个级别最高 已串行的方式
  SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

  private final int level;

  private TransactionIsolationLevel(int level) {
    this.level = level;
  }

  public int getLevel() {
    return level;
  }
}
