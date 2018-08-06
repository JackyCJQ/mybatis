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
package org.apache.ibatis.parsing;

/**
 * 普通记号解析器，处理#{}和${}参数
 * 在解析xml文件时就将进行替换
 */
public class GenericTokenParser {

    //有一个开始和结束记号
    private final String openToken;
    private final String closeToken;
    //记号处理器 用来进行实际值的替换 例如 把${usernmae}--->jacky
    private final TokenHandler handler;

    public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
        this.openToken = openToken;
        this.closeToken = closeToken;
        this.handler = handler;
    }

    /**
     * 一般就是 <property name="username" value="${username}"/>
     * @param text ${username}
     * @return
     */
    public String parse(String text) {
        StringBuilder builder = new StringBuilder();
        if (text != null && text.length() > 0) {
            char[] src = text.toCharArray();
            //记录处理过的位置
            int offset = 0;
            //第一次从0开始寻找
            int start = text.indexOf(openToken, offset);
            //#{favouriteSection,jdbcType=VARCHAR}
            // 比如可以解析${first_name} ${initial} sdf${last_name} reporting.这样的字符串,里面有3个 ${}
            // 逻辑是先把openToken都去掉，然后在处理closeToken时在进行替换
            while (start > -1) {
                //判断一下 ${ 前面是否是反斜杠，有的话就去掉，此时是不需要解析属性
                if (start > 0 && src[start - 1] == '\\') {
                    //仅仅是去掉反斜杠
                    builder.append(src, offset, start - offset - 1).append(openToken);
                    offset = start + openToken.length();
                } else {
                    //这里是需要解析属性
                    int end = text.indexOf(closeToken, start);
                    if (end == -1) {
                        builder.append(src, offset, src.length - offset);
                        offset = src.length;
                    } else {
                        //去掉 closeToken
                        builder.append(src, offset, start - offset);
                        offset = start + openToken.length();
                        //获取在   例如${username} 中的username
                        String content = new String(src, offset, end - offset);
                        //在这里进行了变量的替换
                        builder.append(handler.handleToken(content));
                        offset = end + closeToken.length();
                    }
                }
                //在查找后面是否还有以 openToken 开始的标记
                start = text.indexOf(openToken, offset);
            }
            //在加上后面的东西
            if (offset < src.length) {
                builder.append(src, offset, src.length - offset);
            }
        }
        return builder.toString();
    }

}
