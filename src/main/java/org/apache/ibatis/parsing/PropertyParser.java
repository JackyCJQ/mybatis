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

import java.util.Properties;
/**
 * 属性解析器
 */
public class PropertyParser {

  private PropertyParser() {
    // Prevent Instantiation
  }

  /**
   *
   * @param string 需要替换的变量 ${}这种格式
   * @param variables 属性配置
   * @return
   */
  public static String parse(String string, Properties variables) {
    VariableTokenHandler handler = new VariableTokenHandler(variables);

    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }

    /**
     *引用一个配置文件 通过此类进行替换
     */
  private static class VariableTokenHandler implements TokenHandler {
    private Properties variables;

    public VariableTokenHandler(Properties variables) {
      this.variables = variables;
    }

    @Override
    public String handleToken(String content) {
        /**
         * 从properties中查找是否配置过相应的属性
         */
      if (variables != null && variables.containsKey(content)) {
        return variables.getProperty(content);
      }
      //如果不存在返回 愿配置 例如 ${username}
      return "${" + content + "}";
    }
  }
}
