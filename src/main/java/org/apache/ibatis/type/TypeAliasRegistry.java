/*
 *    Copyright 2009-2013 the original author or authors.
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

import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.util.*;

/**
 * 类型别名注册机
 * 当访问一个别名时 要么是全路径名  要么是别名
 */
public class TypeAliasRegistry {
    //别名和其对应的class
    private final Map<String, Class<?>> TYPE_ALIASES = new HashMap<String, Class<?>>();

    //初始化别名注册器
    public TypeAliasRegistry() {
        //构造函数里注册系统内置的类型别名
        registerAlias("string", String.class);

        //8种基本包装类型
        registerAlias("byte", Byte.class);
        registerAlias("long", Long.class);
        registerAlias("short", Short.class);
        registerAlias("int", Integer.class);
        registerAlias("integer", Integer.class);
        registerAlias("double", Double.class);
        registerAlias("float", Float.class);
        registerAlias("boolean", Boolean.class);

        //8种基本包装类型数组类型
        registerAlias("byte[]", Byte[].class);
        registerAlias("long[]", Long[].class);
        registerAlias("short[]", Short[].class);
        registerAlias("int[]", Integer[].class);
        registerAlias("integer[]", Integer[].class);
        registerAlias("double[]", Double[].class);
        registerAlias("float[]", Float[].class);
        registerAlias("boolean[]", Boolean[].class);

        //加个下划线，就变成了基本类型
        registerAlias("_byte", byte.class);
        registerAlias("_long", long.class);
        registerAlias("_short", short.class);
        registerAlias("_int", int.class);
        registerAlias("_integer", int.class);
        registerAlias("_double", double.class);
        registerAlias("_float", float.class);
        registerAlias("_boolean", boolean.class);

        //加个下划线，就变成了基本数组类型
        registerAlias("_byte[]", byte[].class);
        registerAlias("_long[]", long[].class);
        registerAlias("_short[]", short[].class);
        registerAlias("_int[]", int[].class);
        registerAlias("_integer[]", int[].class);
        registerAlias("_double[]", double[].class);
        registerAlias("_float[]", float[].class);
        registerAlias("_boolean[]", boolean[].class);

        //日期数字型
        registerAlias("date", Date.class);
        registerAlias("decimal", BigDecimal.class);
        registerAlias("bigdecimal", BigDecimal.class);
        registerAlias("biginteger", BigInteger.class);
        registerAlias("object", Object.class);

        registerAlias("date[]", Date[].class);
        registerAlias("decimal[]", BigDecimal[].class);
        registerAlias("bigdecimal[]", BigDecimal[].class);
        registerAlias("biginteger[]", BigInteger[].class);
        registerAlias("object[]", Object[].class);

        //集合型
        registerAlias("map", Map.class);
        registerAlias("hashmap", HashMap.class);
        registerAlias("list", List.class);
        registerAlias("arraylist", ArrayList.class);
        registerAlias("collection", Collection.class);
        registerAlias("iterator", Iterator.class);

        //还有个ResultSet型
        registerAlias("ResultSet", ResultSet.class);
    }

    //解析类型别名
    public <T> Class<T> resolveAlias(String string) {
        try {
            if (string == null) {
                return null;
            }
            //需要转化为全部小写 在配置文件的时候可以不用区分大小写
            String key = string.toLowerCase(Locale.ENGLISH);
            Class<T> value;
            //查找是否注册过了
            if (TYPE_ALIASES.containsKey(key)) {
                value = (Class<T>) TYPE_ALIASES.get(key);
            } else {
                //找不到，再试着将String直接转成Class(这样怪不得我们也可以直接用java.lang.Integer的方式定义，也可以就int这么定义)
                //一般没有进行别名注册的，可以通过全路径进行加载,这样的方式 不太方便，每次都是通过反射创建实例
                value = (Class<T>) Resources.classForName(string);
            }
            return value;
        } catch (ClassNotFoundException e) {
            throw new TypeException("Could not resolve type alias '" + string + "'.  Cause: " + e, e);
        }
    }

    /**
     * 注册一个包名下的全部类
     *
     * @param packageName
     */
    public void registerAliases(String packageName) {
        registerAliases(packageName, Object.class);
    }

    /**
     * 扫描并注册包下所有继承于superType的类型别名 一般来说都是继承Object 其实也可以自己指定一个标记接口 只加载继承这个父类的
     */
    public void registerAliases(String packageName, Class<?> superType) {
        //扫描一个包
        ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<Class<?>>();
        //扫描包下几乎所有的类
        resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
        Set<Class<? extends Class<?>>> typeSet = resolverUtil.getClasses();
        for (Class<?> type : typeSet) {
            //如果不是匿名内部类  不是接口 不是成员类则就进行注册
            if (!type.isAnonymousClass() && !type.isInterface() && !type.isMemberClass()) {
                registerAlias(type);
            }
        }
    }

    /**
     * 如果没有指定别名，就会使用类名
     *
     * @param type
     */
    public void registerAlias(Class<?> type) {
        //如果没有类型别名，用Class.getSimpleName来注册 就是类的名字
        String alias = type.getSimpleName();
        //或者通过Alias注解来注册(Class.getAnnotation)
        //如果有Alias注解值用注解的值
        Alias aliasAnnotation = type.getAnnotation(Alias.class);
        if (aliasAnnotation != null) {
            alias = aliasAnnotation.value();
        }
        registerAlias(alias, type);
    }

    /**
     * 注册别名
     *
     * @param alias
     * @param value
     */
    public void registerAlias(String alias, Class<?> value) {
        if (alias == null) {
            throw new TypeException("The parameter alias cannot be null");
        }
        //把别名转换为小写英文 在外面引用的时候 只需写对就行，这里一律转化为全小写
        String key = alias.toLowerCase(Locale.ENGLISH);
        //如果已经存在key了，且value和之前不一致，报错
        if (TYPE_ALIASES.containsKey(key) && TYPE_ALIASES.get(key) != null && !TYPE_ALIASES.get(key).equals(value)) {
            throw new TypeException("The alias '" + alias + "' is already mapped to the value '" + TYPE_ALIASES.get(key).getName() + "'.");
        }
        //如果不存在就直接注册进去就行了
        TYPE_ALIASES.put(key, value);
    }

    /**
     * 也可以注册一个类路径 把这个类加载进来之后在进行注册
     *
     * @param alias
     * @param value
     */
    public void registerAlias(String alias, String value) {
        try {
            registerAlias(alias, Resources.classForName(value));
        } catch (ClassNotFoundException e) {
            throw new TypeException("Error registering type alias " + alias + " for " + value + ". Cause: " + e, e);
        }
    }

    /**
     * 返回一个不可更改的视图 就是在返回时 不能进行更改 否则就会报错
     */
    public Map<String, Class<?>> getTypeAliases() {
        return Collections.unmodifiableMap(TYPE_ALIASES);
    }

}
