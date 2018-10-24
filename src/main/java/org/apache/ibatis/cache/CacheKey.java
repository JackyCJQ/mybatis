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
 * MyBatis 对于其 Key 的生成采取规则为：[mappedStementId + offset + limit + SQL + queryParams + environment]生成一个哈希码
 * 以上规则才能保证一个唯一的查询
 */
public class CacheKey implements Cloneable, Serializable {

    private static final long serialVersionUID = 1146682552656046210L;
    //设置一个缓存的值为null 的key
    public static final CacheKey NULL_CACHE_KEY = new NullCacheKey();
    //提供了两个默认的数值
    private static final int DEFAULT_MULTIPLYER = 37;
    private static final int DEFAULT_HASHCODE = 17;

    private int multiplier;
    //每个key都生成唯一的hash值
    private int hashcode;
    //这个是校验
    private long checksum;
    //更新的数量
    private int count;
    //[mappedStementId + offset + limit + SQL + queryParams + environment]
    private List<Object> updateList;

    public CacheKey() {
        //默认的hashcode
        this.hashcode = DEFAULT_HASHCODE;
        this.multiplier = DEFAULT_MULTIPLYER;
        this.count = 0;
        this.updateList = new ArrayList<Object>();
    }

    /**
     * 数组内容：[mappedStementId + offset + limit + SQL + queryParams + environment]
     *
     * @param objects
     */
    public CacheKey(Object[] objects) {
        this();
        updateAll(objects);
    }

    /**
     * 生成key的那些变量数量，即 [mappedStementId + offset + limit + SQL + queryParams + environment]
     *
     * @return
     */
    public int getUpdateCount() {
        return updateList.size();
    }

    /**
     * 更新每个：[mappedStementId + offset + limit + SQL + queryParams + environment]
     */
    public void update(Object object) {
        //如果更新的是数组，则循环调用doUpdate
        if (object != null && object.getClass().isArray()) {
            //通过反射来获取长度和元素
            int length = Array.getLength(object);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(object, i);
                doUpdate(element);
            }
        } else {
            //如果不是数组，则直接进行更新
            doUpdate(object);
        }
    }

    /**
     * 更新一个对象
     *
     * @param object
     */
    private void doUpdate(Object object) {
        //如果为null 哈希值设置为1,否则就是原来的哈希值
        int baseHashCode = object == null ? 1 : object.hashCode();

        count++;
        checksum += baseHashCode;
        baseHashCode *= count;

        hashcode = multiplier * hashcode + baseHashCode;
        //同时将对象加入列表
        updateList.add(object);
    }

    public void updateAll(Object[] objects) {
        for (Object o : objects) {
            update(o);
        }
    }

    //重写equals方法
    @Override
    public boolean equals(Object object) {
        //判断地址是否是一样的
        if (this == object) {
            return true;
        }
        //比较的对象必须是CacheKey的子类
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
        for (int i = 0; i < updateList.size(); i++) {
            Object thisObject = updateList.get(i);
            Object thatObject = cacheKey.updateList.get(i);
            //最后比较字符串中的内容是否一致
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
