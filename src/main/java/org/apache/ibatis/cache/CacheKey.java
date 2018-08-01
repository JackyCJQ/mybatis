/*
 *    Copyright 2009-2014 the original author or authors.
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
package org.apache.ibatis.cache;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * 缓存key
 * MyBatis 对于其 Key 的生成采取规则为：[mappedStementId + offset + limit + SQL + queryParams + environment]生成一个哈希码
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;
  //默认设置一个缓存值为null的key
  public static final CacheKey NULL_CACHE_KEY = new NullCacheKey();
  //提供了两个默认的数值
  private static final int DEFAULT_MULTIPLYER = 37;
  private static final int DEFAULT_HASHCODE = 17;

  private int multiplier;
  private int hashcode;
  //这个是校验
  private long checksum;
  private int count;
  private List<Object> updateList;

  public CacheKey() {
    //默认的hashcode
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLYER;
    this.count = 0;
    this.updateList = new ArrayList<Object>();
  }

  //传入一个Object数组，更新hashcode和效验码
  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }
  //返回缓存中更新的数量
  public int getUpdateCount() {
    return updateList.size();
  }
  //更新
  public void update(Object object) {
    //如果缓存中更新的是数组，则循环调用doUpdate
    if (object != null && object.getClass().isArray()) {
      //通过这种方式可以获取数组的长度
      int length = Array.getLength(object);
      for (int i = 0; i < length; i++) {
        //应该是通过反射的方式来获取
        Object element = Array.get(object, i);
        doUpdate(element);
      }
    } else {
        //如果不是数组，则直接进行更新
      doUpdate(object);
    }
  }

  private void doUpdate(Object object) {
    //如果为null 哈希值设置为1,否则就是原来的哈希值
    int baseHashCode = object == null ? 1 : object.hashCode();

    count++;
    checksum += baseHashCode;
    baseHashCode *= count;

    hashcode = multiplier * hashcode + baseHashCode;
    //同时将对象加入列表，这样万一两个CacheKey的hash码碰巧一样，再根据对象严格equals来区分
    updateList.add(object);
  }

  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  @Override
  public boolean equals(Object object) {
    //判断地址是否是一样的 ==判断的是内存地址
    if (this == object) {
      return true;
    }
    //缓存的key必须是CacheKey的子类
    if (!(object instanceof CacheKey)) {
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;

    //先比hashcode，checksum，count，理论上可以快速比出来
    if (hashcode != cacheKey.hashcode) {
      return false;
    }
    if (checksum != cacheKey.checksum) {
      return false;
    }
    if (count != cacheKey.count) {
      return false;
    }

    //万一两个CacheKey的hash码碰巧一样，再根据对象严格equals来区分
    //这里两个list的size没比是否相等，其实前面count相等就已经保证了
    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (thisObject == null) {
        if (thatObject != null) {
          return false;
        }
      } else {
        if (!thisObject.equals(thatObject)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  @Override
  public String toString() {
    StringBuilder returnValue = new StringBuilder().append(hashcode).append(':').append(checksum);
    for (int i = 0; i < updateList.size(); i++) {
      returnValue.append(':').append(updateList.get(i));
    }

    return returnValue.toString();
  }

  @Override
  //实现了深度复制 把更新列表进行了复制
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    clonedCacheKey.updateList = new ArrayList<Object>(updateList);
    return clonedCacheKey;
  }

}
