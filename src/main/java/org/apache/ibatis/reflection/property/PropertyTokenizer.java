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

import java.util.Iterator;

/**
 * 属性分解为标记，迭代子模式
 * 如person[0].birthdate.year，将依次取得person[0], birthdate, year
 */
public class PropertyTokenizer implements Iterable<PropertyTokenizer>, Iterator<PropertyTokenizer> {
    //例子： person[0].birthdate.year
    private String name; //person
    private String indexedName; //person[0]
    //如果存在中括号，则解析里面的数字索引，如果是map，则为key,例如：map['key']，所以这里用的是String
    private String index;
    private String children; //每次解析一个.之后剩下的部分

    public PropertyTokenizer(String fullname) {
        //person[0].birthdate.year
        int delim = fullname.indexOf('.');
        if (delim > -1) {
            name = fullname.substring(0, delim);
            children = fullname.substring(delim + 1);
        } else {
            //找不到.的话，取全部部分
            name = fullname;
            children = null;
        }
        indexedName = name;
        //如果存在[]这样的索引，把中括号里的数字给解析出来
        delim = name.indexOf('[');
        if (delim > -1) {
            //person[0]  里面的对应的数字0
            index = name.substring(delim + 1, name.length() - 1);
            //person[0]-->person
            name = name.substring(0, delim);
        }
    }

    public String getName() {
        return name;
    }

    public String getIndex() {
        return index;
    }

    public String getIndexedName() {
        return indexedName;
    }

    public String getChildren() {
        return children;
    }

    @Override
    public boolean hasNext() {
        return children != null;
    }

    //取得下一个,非常简单，直接再通过儿子来new另外一个实例，类似于递归，必须搭配上面那个方法来使用
    @Override
    public PropertyTokenizer next() {
        return new PropertyTokenizer(children);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
    }

    //这个地方用到了迭代器 有意思 通过迭代器循环解析
    @Override
    public Iterator<PropertyTokenizer> iterator() {
        return this;
    }
}
