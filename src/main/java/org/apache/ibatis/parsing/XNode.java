/*
 *    Copyright 2009-2011 the original author or authors.
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

import org.w3c.dom.CharacterData;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 对org.w3c.dom.Node的包装
 * 提供更详细的信息
 */
public class XNode {

    //org.w3c.dom.Node
    private Node node;//原来的节点
    private String name;//如果有的话 对应节点的名字
    private String body;  //如果是文本类型的会有文本值
    private Properties attributes;//这个节点中的属性的配置
    private Properties variables;//对应全局的属性配置的引用
    //XPathParser方便xpath解析
    private XPathParser xpathParser;

    //在构造时就把一些信息（属性，body）全部解析好，以便我们直接通过getter函数取得
    public XNode(XPathParser xpathParser, Node node, Properties variables) {
        this.xpathParser = xpathParser;
        this.node = node;
        this.name = node.getNodeName();
        this.variables = variables;

        this.attributes = parseAttributes(node);
        this.body = parseBody(node);
    }

    public XNode newXNode(Node node) {
        return new XNode(xpathParser, node, variables);
    }

    public XNode getParent() {
        //调用Node.getParentNode,如果取到，包装一下，返回XNode
        Node parent = node.getParentNode();
        //如果没有父节点
        if (parent == null || !(parent instanceof Element)) {
            return null;
        } else {
            return new XNode(xpathParser, parent, variables);
        }
    }

    //取得完全的path (a/b/c)
    public String getPath() {
        //循环依次取得节点的父节点，然后倒序打印,也可以用一个堆栈实现
        StringBuilder builder = new StringBuilder();
        Node current = node;
        //循环遍历
        while (current != null && current instanceof Element) {
            //从最底层开始遍历
            if (current != node) {
                builder.insert(0, "/");
            }
            builder.insert(0, current.getNodeName());
            //开始查找父类路径
            current = current.getParentNode();
        }
        return builder.toString();
    }

    /**
     * 如果resultMap没有指定ID，则生成一个ID
     * <resultMap id="authorResult" type="Author">
     *	  <id property="id" column="author_id"/>
     *	  <result property="username" column="author_username"/>
     *	  <result property="password" column="author_password"/>
     *	  <result property="email" column="author_email"/>
     *	  <result property="bio" column="author_bio"/>
     *	</resultMap>
     * @return resultMap[aa_bb]形式
     */

    public String getValueBasedIdentifier() {
        StringBuilder builder = new StringBuilder();
        XNode current = this;
        while (current != null) {
            //除了第一次遍历，其他的都需要加_
            if (current != this) {
                builder.insert(0, "_");
            }
            //先拿id，拿不到再拿value,再拿不到拿property
            String value = current.getStringAttribute("id",
                    current.getStringAttribute("value",
                            current.getStringAttribute("property", null)));
            if (value != null) {
                //[aa_bb]
                value = value.replace('.', '_');
                builder.insert(0, "]");
                builder.insert(0, value);
                builder.insert(0, "[");
            }
            //节点名字[aa_bb]
            builder.insert(0, current.getName());
            current = current.getParent();
        }
        //结果类似aa[aa_aa]_bb[bb_bb]
        return builder.toString();
    }

    //以下方法都是把XPathParser的方法再重复一遍
    public String evalString(String expression) {
        return xpathParser.evalString(node, expression);
    }

    public Boolean evalBoolean(String expression) {
        return xpathParser.evalBoolean(node, expression);
    }

    public Double evalDouble(String expression) {
        return xpathParser.evalDouble(node, expression);
    }

    public List<XNode> evalNodes(String expression) {
        return xpathParser.evalNodes(node, expression);
    }

    public XNode evalNode(String expression) {
        return xpathParser.evalNode(node, expression);
    }

    public Node getNode() {
        return node;
    }

    public String getName() {
        return name;
    }

    //以下是一些getBody的方法
    public String getStringBody() {
        return getStringBody(null);
    }

    /**
     * 返回是否有默认值
     *
     * @param def
     * @return
     */

    public String getStringBody(String def) {
        if (body == null) {
            return def;
        } else {
            return body;
        }
    }

    public Boolean getBooleanBody() {
        return getBooleanBody(null);
    }

    public Boolean getBooleanBody(Boolean def) {
        if (body == null) {
            return def;
        } else {
            return Boolean.valueOf(body);
        }
    }

    public Integer getIntBody() {
        return getIntBody(null);
    }

    public Integer getIntBody(Integer def) {
        if (body == null) {
            return def;
        } else {
            return Integer.parseInt(body);
        }
    }

    public Long getLongBody() {
        return getLongBody(null);
    }

    public Long getLongBody(Long def) {
        if (body == null) {
            return def;
        } else {
            return Long.parseLong(body);
        }
    }

    public Double getDoubleBody() {
        return getDoubleBody(null);
    }

    public Double getDoubleBody(Double def) {
        if (body == null) {
            return def;
        } else {
            return Double.parseDouble(body);
        }
    }

    public Float getFloatBody() {
        return getFloatBody(null);
    }

    public Float getFloatBody(Float def) {
        if (body == null) {
            return def;
        } else {
            return Float.parseFloat(body);
        }
    }

    //以下是一些getAttribute的方法
    public <T extends Enum<T>> T getEnumAttribute(Class<T> enumType, String name) {
        return getEnumAttribute(enumType, name, null);
    }

    public <T extends Enum<T>> T getEnumAttribute(Class<T> enumType, String name, T def) {
        String value = getStringAttribute(name);
        if (value == null) {
            return def;
        } else {
            return Enum.valueOf(enumType, value);
        }
    }

    public String getStringAttribute(String name) {
        return getStringAttribute(name, null);
    }

    public String getStringAttribute(String name, String def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return value;
        }
    }

    public Boolean getBooleanAttribute(String name) {
        return getBooleanAttribute(name, null);
    }

    public Boolean getBooleanAttribute(String name, Boolean def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Boolean.valueOf(value);
        }
    }

    public Integer getIntAttribute(String name) {
        return getIntAttribute(name, null);
    }

    public Integer getIntAttribute(String name, Integer def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Integer.parseInt(value);
        }
    }

    public Long getLongAttribute(String name) {
        return getLongAttribute(name, null);
    }

    public Long getLongAttribute(String name, Long def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Long.parseLong(value);
        }
    }

    public Double getDoubleAttribute(String name) {
        return getDoubleAttribute(name, null);
    }

    public Double getDoubleAttribute(String name, Double def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Double.parseDouble(value);
        }
    }

    public Float getFloatAttribute(String name) {
        return getFloatAttribute(name, null);
    }

    public Float getFloatAttribute(String name, Float def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Float.parseFloat(value);
        }
    }

    //得到孩子，原理是调用Node.getChildNodes
    public List<XNode> getChildren() {
        List<XNode> children = new ArrayList<XNode>();
        NodeList nodeList = node.getChildNodes();
        if (nodeList != null) {
            for (int i = 0, n = nodeList.getLength(); i < n; i++) {
                Node node = nodeList.item(i);
                //如果孩子类型是节点
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    children.add(new XNode(xpathParser, node, variables));
                }
            }
        }
        return children;
    }

    //得到孩子，返回Properties，孩子的格式肯定都有name,value属性
    public Properties getChildrenAsProperties() {
        Properties properties = new Properties();
        for (XNode child : getChildren()) {
            String name = child.getStringAttribute("name");
            String value = child.getStringAttribute("value");
            if (name != null && value != null) {
                properties.setProperty(name, value);
            }
        }
        return properties;
    }

    //打印信息，为了调试用
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<");
        builder.append(name);
        for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
            builder.append(" ");
            builder.append(entry.getKey());
            builder.append("=\"");
            builder.append(entry.getValue());
            builder.append("\"");
        }
        List<XNode> children = getChildren();
        if (!children.isEmpty()) {
            builder.append(">\n");
            for (XNode node : children) {
                //递归取得孩子的toString
                builder.append(node.toString());
            }
            builder.append("</");
            builder.append(name);
            builder.append(">");
        } else if (body != null) {
            builder.append(">");
            builder.append(body);
            builder.append("</");
            builder.append(name);
            builder.append(">");
        } else {
            builder.append("/>");
        }
        builder.append("\n");
        return builder.toString();
    }

    /**
     * 解析这个节点中配置的属性 <environment id="development"> 指这个id属性
     * <property name="driver" value="com.mysql.jdbc.Driver"/>
     * 解析完成后attributes-->[name-->drive,value-->com.mysql.jdbc.Driver]
     *
     * @param n
     * @return
     */
    private Properties parseAttributes(Node n) {
        Properties attributes = new Properties();
        //
        NamedNodeMap attributeNodes = n.getAttributes();
        if (attributeNodes != null) {
            for (int i = 0; i < attributeNodes.getLength(); i++) {
                Node attribute = attributeNodes.item(i);
                //如果这个属性配置中应用了全局配置，则需要进行替换 例如 ${}这中配置引用
                String value = PropertyParser.parse(attribute.getNodeValue(), variables);
                //配置的属性的名字和其值
                attributes.put(attribute.getNodeName(), value);
            }
        }
        return attributes;
    }

    /**
     * @param node
     * @return
     */
    private String parseBody(Node node) {
        //取不到body，循环取孩子的body，只要取到第一个，立即返回
        String data = getBodyData(node);
        if (data == null) {
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                data = getBodyData(child);
                if (data != null) {
                    break;
                }
            }
        }
        return data;
    }

    /**
     * <select id="selectById" resultType="sysUser" >
     *      select
     *              user_name userName,
     *              user_email userEmail
     *       from sys_user WHERE id=#{0} and user_name=#{1}
     * </select>
     *   获取下面的文本信息
     * @param child
     * @return
     */
    private String getBodyData(Node child) {
        if (child.getNodeType() == Node.CDATA_SECTION_NODE
                || child.getNodeType() == Node.TEXT_NODE) {
            String data = ((CharacterData) child).getData();
            data = PropertyParser.parse(data, variables);
            return data;
        }
        return null;
    }

}