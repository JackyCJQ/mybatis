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
package org.apache.ibatis.reflection.property;

import org.apache.ibatis.reflection.ReflectionException;

import java.util.Locale;

/**
 * 属性命名器
 */
public final class PropertyNamer {

    private PropertyNamer() {

    }

    /**
     * 方法的名字
     *
     * @param name
     * @return
     */
    public static String methodToProperty(String name) {
        //去掉get|set|is
        if (name.startsWith("is")) {
            name = name.substring(2);
        } else if (name.startsWith("get") || name.startsWith("set")) {
            name = name.substring(3);
        } else {
            throw new ReflectionException("Error parsing property name '" + name + "'.  Didn't start with 'is', 'get' or 'set'.");
        }
        //如果只有1个字母-->转为小写
        //如果大于1个字母，第二个字母非大写-->转为小写
        //如果前两个字母都大写，则不进行转化，说明原来就是大写的
        //String uRL -->String getuRL()
        if (name.length() == 1 || (name.length() > 1 && !Character.isUpperCase(name.charAt(1)))) {
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }
        return name;
    }

    /**
     * @param name 方法的名字
     * @return
     */
    public static boolean isProperty(String name) {
        //必须以get|set|is开头
        return name.startsWith("get") || name.startsWith("set") || name.startsWith("is");
    }

    /**
     * @param name 方法的名字
     * @return
     */
    public static boolean isGetter(String name) {
        return name.startsWith("get") || name.startsWith("is");
    }

    /**
     * @param name 方法的名字
     * @return
     */
    public static boolean isSetter(String name) {
        return name.startsWith("set");
    }

}
