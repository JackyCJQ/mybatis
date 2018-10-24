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
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.*;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.io.InputStream;
import java.io.Reader;
import java.util.*;

/**
 * XML映射构建器，建造者模式,继承BaseBuilder
 */
public class XMLMapperBuilder extends BaseBuilder {
    //xml文档解析器 解析mapper.xml文件 生成document
    private XPathParser parser;
    //每一个mapper.xml对应一个构建助手
    private MapperBuilderAssistant builderAssistant;
    //对应SQL片段 key-->mapperNameSpace+<sql>的ID，未解析的sql片段
    private Map<String, XNode> sqlFragments;
    /**
     * resoure代表这个mapper.xml的全路径（）
     * <mapper resource="org/mybatis/builder/PostMapper.xml"/>
     * 或者是<mapper url="file:///var/mappers/PostMapper.xml"/>
     */
    private String resource;

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(reader, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    /**
     * 构造函数
     *
     * @param inputStream   read from mapper.xml
     * @param configuration the global configuration
     * @param resource      mapper.xml的路径
     * @param sqlFragments  sql碎片 在congiruration中统一保存
     * @param namespace     mapper.xml的namespace
     */
    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(inputStream, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    /**
     * 构造函数
     * 通过流构造一个xml解析器
     *
     * @param inputStream   read from mapper.xml
     * @param configuration 全局配置
     * @param resource      mapper.xml的路径
     * @param sqlFragments  sql碎片 在congiruration中统一保存
     */
    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    /**
     * 所有的构造函数 最终合流到这个地方
     *
     * @param parser        mapper.xml的解析器
     * @param configuration 全局配置
     * @param resource      mapper.xml的路径
     * @param sqlFragments  sql碎片 在congiruration中统一保存
     */
    private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        super(configuration);
        this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
        this.parser = parser;
        this.sqlFragments = sqlFragments;
        this.resource = resource;
    }

    /**
     * 解析mapper.xml文件
     */
    public void parse() {
        //确定是否已经加载过，防止重复加载
        if (!configuration.isResourceLoaded(resource)) {
            //开始解析
            configurationElement(parser.evalNode("/mapper"));
            //标记一下，已经加载过了
            configuration.addLoadedResource(resource);
            //mapper.xml和mapper接口绑定到一起
            bindMapperForNamespace();
        }
        //还有没解析完的东东这里接着解析？
        parsePendingResultMaps();
        parsePendingChacheRefs();
        parsePendingStatements();
    }

    /**
     * 返回对应的sql片段
     *
     * @param refid
     * @return
     */
    public XNode getSqlFragment(String refid) {
        return sqlFragments.get(refid);
    }


    /**
     * 按照不同的元素解析
     * <mapper namespace="org.mybatis.example.BlogMapper">
     * <select id="selectBlog" parameterType="int" resultType="Blog">
     * select * from Blog where id = #{id}
     * </select>
     * </mapper>
     */
    private void configurationElement(XNode context) {
        try {
            //1.配置namespace，不能为空
            String namespace = context.getStringAttribute("namespace");
            if (namespace.equals("")) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }
            //每一个mapper配置文件对应一个builderAssistant，每个mapper配置文件用namespace来区分
            builderAssistant.setCurrentNamespace(namespace);
            //2.配置cache-ref
            cacheRefElement(context.evalNode("cache-ref"));
            //3.配置cache 配置此mapper缓存的方式
            cacheElement(context.evalNode("cache"));
            //4.配置parameterMap(已经废弃,老式风格的参数映射)
            parameterMapElement(context.evalNodes("/mapper/parameterMap"));
            //5.配置resultMap(高级功能) 这个可以配置有多个
            resultMapElements(context.evalNodes("/mapper/resultMap"));
            //6.配置sql(定义可重用的 SQL 代码段) 这个也可以配置多个
            sqlElement(context.evalNodes("/mapper/sql"));
            //7.配置select|insert|update|delete 这个配置文件中也可以配置多个
            buildStatementFromContext(context.evalNodes("select|insert|update|delete"));

        } catch (Exception e) {
            throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
        }
    }

    /**
     * 配置select|insert|update|delete 因为一个mapper.xml文件中可能会有多个配置，所以是一个list集合
     *
     * @param list
     */
    private void buildStatementFromContext(List<XNode> list) {
        //如果指定了所用的database
        if (configuration.getDatabaseId() != null) {
            buildStatementFromContext(list, configuration.getDatabaseId());
        }
        buildStatementFromContext(list, null);
    }

    /**
     * 解析mapper.xml文件中的配置
     *
     * @param list
     * @param requiredDatabaseId
     */
    private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
        for (XNode context : list) {
            //构建所有语句,一个mapper下可以有很多select|insert|update|delete
            final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
            try {
                //核心XMLStatementBuilder.parseStatementNode
                statementParser.parseStatementNode();
            } catch (IncompleteElementException e) {
                //如果出现SQL语句不完整，把它记下来，塞到configuration去
                configuration.addIncompleteStatement(statementParser);
            }
        }
    }

    private void parsePendingResultMaps() {
        Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
        synchronized (incompleteResultMaps) {
            Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolve();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // ResultMap is still missing a resource...
                }
            }
        }
    }

    private void parsePendingChacheRefs() {
        Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
        synchronized (incompleteCacheRefs) {
            Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolveCacheRef();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // Cache ref is still missing a resource...
                }
            }
        }
    }

    private void parsePendingStatements() {
        Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
        synchronized (incompleteStatements) {
            Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().parseStatementNode();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // Statement is still missing a resource...
                }
            }
        }
    }

    /**
     * 2.配置cache-ref,你可以使用
     * <cache-ref namespace="com.someone.application.data.SomeMapper"/>元素来引用另外一个缓存。
     *
     * @param context
     */
    private void cacheRefElement(XNode context) {
        if (context != null) {
            //一个Namespace引用另一个Namespace
            configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
            //缓存引用解析器，解析引用的缓存
            CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
            try {
                cacheRefResolver.resolveCacheRef();
            } catch (IncompleteElementException e) {
                configuration.addIncompleteCacheRef(cacheRefResolver);
            }
        }
    }

    /**
     * 3.配置cache
     * <cache type="" eviction="FIFO" flushInterval="60000" size="512" readOnly="true"/>
     */
    private void cacheElement(XNode context) throws Exception {
        if (context != null) {
            //缓存的实现类
            String type = context.getStringAttribute("type", "PERPETUAL");
            Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
            //缓存的算法
            String eviction = context.getStringAttribute("eviction", "LRU");
            Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
            //刷新间隔
            Long flushInterval = context.getLongAttribute("flushInterval");
            //缓存的数量
            Integer size = context.getIntAttribute("size");
            //是否为只读
            boolean readWrite = !context.getBooleanAttribute("readOnly", false);
            //是否是阻塞的 即如果缓存中没有则会一直等待
            boolean blocking = context.getBooleanAttribute("blocking", false);
            /**
             * 如果是第三方缓存，则可能还配置了一些其他属性
             * <cache type="com.domain.something.MyCustomCache">
             *      <property name="cacheFile" value="/tmp/my-custom-cache.tmp"/>
             * </cache>
             */
            Properties props = context.getChildrenAsProperties();
            //根据配置 构建一个新的缓存
            builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
        }
    }

    //4.配置parameterMap
    //已经被废弃了!老式风格的参数映射。可以忽略
    private void parameterMapElement(List<XNode> list) throws Exception {
        for (XNode parameterMapNode : list) {
            String id = parameterMapNode.getStringAttribute("id");
            String type = parameterMapNode.getStringAttribute("type");
            Class<?> parameterClass = resolveClass(type);
            List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
            List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
            for (XNode parameterNode : parameterNodes) {
                String property = parameterNode.getStringAttribute("property");
                String javaType = parameterNode.getStringAttribute("javaType");
                String jdbcType = parameterNode.getStringAttribute("jdbcType");
                String resultMap = parameterNode.getStringAttribute("resultMap");
                String mode = parameterNode.getStringAttribute("mode");
                String typeHandler = parameterNode.getStringAttribute("typeHandler");
                Integer numericScale = parameterNode.getIntAttribute("numericScale");
                ParameterMode modeEnum = resolveParameterMode(mode);
                Class<?> javaTypeClass = resolveClass(javaType);
                JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
                @SuppressWarnings("unchecked")
                Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
                ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
                parameterMappings.add(parameterMapping);
            }
            builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
        }
    }

    /**
     * 5.配置resultMap
     * <resultMap id="detailedBlogResultMap" type="Blog">
     * <constructor>
     * <idArg column="blog_id" javaType="int"/>
     * </constructor>
     * <result property="title" column="blog_title"/>
     * <association property="author" javaType="Author">
     * <id property="id" column="author_id"/>
     * <result property="username" column="author_username"/>
     * <result property="password" column="author_password"/>
     * <result property="email" column="author_email"/>
     * <result property="bio" column="author_bio"/>
     * <result property="favouriteSection" column="author_favourite_section"/>
     * </association>
     * <collection property="posts" ofType="Post">
     * <id property="id" column="post_id"/>
     * <result property="subject" column="post_subject"/>
     * <association property="author" javaType="Author"/>
     * <collection property="comments" ofType="Comment">
     * <id property="id" column="comment_id"/>
     * </collection>
     * <collection property="tags" ofType="Tag" >
     * <id property="id" column="tag_id"/>
     * </collection>
     * <discriminator javaType="int" column="draft">
     * <case value="1" resultType="DraftPost"/>
     * </discriminator>
     * </collection>
     * </resultMap>
     *
     * @param list resultMap可能不止一个，所以这里传入的参数为list
     * @throws Exception
     */
    private void resultMapElements(List<XNode> list) throws Exception {
        for (XNode resultMapNode : list) {
            try {
                resultMapElement(resultMapNode);
            } catch (IncompleteElementException e) {
                // ignore, it will be retried
            }
        }
    }

    /**
     * 5.1 解析每一个配置的resultMap
     *
     * @param resultMapNode
     * @return
     * @throws Exception
     */
    private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
        return resultMapElement(resultMapNode, Collections.<ResultMapping>emptyList());
    }

    /**
     * 解析配置的resultMap
     * <resultMap id="userResultMap" type="User">
     * <id property="id" column="user_id" />
     * <result property="username" column="username"/>
     * <result property="password" column="password"/>
     * </resultMap>
     *
     * @param resultMapNode            resultMap节点
     * @param additionalResultMappings 对应解析后的List<ResultMapping>
     * @return
     * @throws Exception
     */
    private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
        ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
        //如果没有指定ID 会生成一个唯一标识符来代替ID
        String id = resultMapNode.getStringAttribute("id", resultMapNode.getValueBasedIdentifier());
        //resultMap-->type，connection-->ofType associate-->resultye Discriminator->javaType
        String type = resultMapNode.getStringAttribute("type",
                resultMapNode.getStringAttribute("ofType", resultMapNode.getStringAttribute("resultType", resultMapNode.getStringAttribute("javaType"))));
        /**
         * 继承功能 如果继承其他mapper.xml 可以引用本mapper中，也可以引用另一个mapper中的resultMap（namespace+resultMap的id）
         *  <resultMap id="carResult" type="Car" extends="vehicleResult">
         * <result property="doorCount" column="door_count" />
         * </resultMap>
         */
        String extend = resultMapNode.getStringAttribute("extends");
        //属性和数据库列名(或者是别名)之间自动映射,默认应该是自动匹配的
        Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
        //解析resultmap映射的Java类
        Class<?> typeClass = resolveClass(type);
        //鉴别器
        Discriminator discriminator = null;
        //匹配的所有结果
        List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
        resultMappings.addAll(additionalResultMappings);
        /**
         * 获取每个Java属性的具体配置，每个属性对应一个ResultMapping
         */
        List<XNode> resultChildren = resultMapNode.getChildren();
        //结下resultMap下的所有有记过
        for (XNode resultChild : resultChildren) {
            //如果是构造器中含有一部分属性 则通过调用有惨构造器
            if ("constructor".equals(resultChild.getName())) {
                //解析resultMap的constructor
                processConstructorElement(resultChild, typeClass, resultMappings);
            } else if ("discriminator".equals(resultChild.getName())) {
                //解析result map的discriminator
                discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
            } else {
                List<ResultFlag> flags = new ArrayList<ResultFlag>();
                if ("id".equals(resultChild.getName())) {
                    flags.add(ResultFlag.ID);
                }
                //调5.1.1 buildResultMappingFromContext,得到ResultMapping
                resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
            }
        }
        //最后再调ResultMapResolver得到ResultMap
        ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
        try {
            return resultMapResolver.resolve();
        } catch (IncompleteElementException e) {
            configuration.addIncompleteResultMap(resultMapResolver);
            throw e;
        }
    }

    /**
     * 解析result map的constructor
     * <constructor>
     * <idArg column="blog_id" javaType="int"/>
     * <Arg column="",javaType=""/>
     * </constructor>
     *
     * @param resultChild    对应<constructor>节点
     * @param resultType     映射的Java类
     * @param resultMappings 映射的结果
     * @throws Exception
     */
    private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
        List<XNode> argChildren = resultChild.getChildren();
        for (XNode argChild : argChildren) {
            //可能是ID或者是构造参数
            List<ResultFlag> flags = new ArrayList<ResultFlag>();
            //肯定都是args
            flags.add(ResultFlag.CONSTRUCTOR);
            //有的可能同时也是ID
            if ("idArg".equals(argChild.getName())) {
                flags.add(ResultFlag.ID);
            }
            resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
        }
    }

    //解析result map的discriminator
//<discriminator javaType="int" column="draft">
//  <case value="1" resultType="DraftPost"/>
//</discriminator>
    private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String typeHandler = context.getStringAttribute("typeHandler");
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Map<String, String> discriminatorMap = new HashMap<String, String>();
        for (XNode caseChild : context.getChildren()) {
            String value = caseChild.getStringAttribute("value");
            String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
            discriminatorMap.put(value, resultMap);
        }
        return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
    }

    //6 配置sql(定义可重用的 SQL 代码段)
    private void sqlElement(List<XNode> list) throws Exception {
        if (configuration.getDatabaseId() != null) {
            sqlElement(list, configuration.getDatabaseId());
        }
        sqlElement(list, null);
    }

    //6.1 配置sql
    //<sql id="userColumns"> id,username,password </sql>
    private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
        for (XNode context : list) {
            String databaseId = context.getStringAttribute("databaseId");
            String id = context.getStringAttribute("id");
            //ID就是namespace+配置的id
            //这里的sql片段 有一些规则 要么就是nameSapce.name  要么就是name(name已经是nameSapce.name这种格式)
            id = builderAssistant.applyCurrentNamespace(id, false);
            //比较简单，就是将sql片段放入hashmap,不过此时还没有解析sql片段
            if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
                sqlFragments.put(id, context);
            }
        }
    }

    private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
        if (requiredDatabaseId != null) {
            if (!requiredDatabaseId.equals(databaseId)) {
                return false;
            }
        } else {
            if (databaseId != null) {
                return false;
            }
            // skip this fragment if there is a previous one with a not null databaseId
            //如果有重名的id了
            //<sql id="userColumns"> id,username,password </sql>
            if (this.sqlFragments.containsKey(id)) {
                XNode context = this.sqlFragments.get(id);
                //如果之前那个重名的sql id有databaseId，则false，否则难道true？这样新的sql覆盖老的sql？？？
                if (context.getStringAttribute("databaseId") != null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 5.1.1 构建resultMap
     * <constructor>
     * <idArg column="blog_id" javaType="int"/>
     * <Arg column="",javaType=""/>
     * <id property="id" column="author_id"/>
     * <result property="username" column="author_username"/>
     * </constructor>
     *
     * @param context    每一行子节点
     * @param resultType 对应的Java类
     * @param flags      标记可能是ID，可能是CONSTRUCTOR
     * @return
     * @throws Exception
     */

    private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
        //对应的java中的属性
        String property = context.getStringAttribute("property");
        //对应数据库的列名
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        //是否是通过其他sql操作获取的这个值
        String nestedSelect = context.getStringAttribute("select");
        //处理嵌套的resultmap Java中的构造参数可能为pojo，也对应一个resultMap
        String nestedResultMap = context.getStringAttribute("resultMap",
                //如果是内部嵌套一个resultMap,则继续解析，返回一个resultMap的ID，否则返回null
                processNestedResultMappings(context, Collections.<ResultMapping>emptyList()));
        String notNullColumn = context.getStringAttribute("notNullColumn");
        String columnPrefix = context.getStringAttribute("columnPrefix");
        String typeHandler = context.getStringAttribute("typeHandler");
        String resulSet = context.getStringAttribute("resultSet");
        String foreignColumn = context.getStringAttribute("foreignColumn");
        //判断是否是懒加载
        boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
        //解析JavaType
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        //调builderAssistant.buildResultMapping
        return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap,
                notNullColumn, columnPrefix, typeHandlerClass, flags, resulSet, foreignColumn, lazy);
    }

    /**
     * 处理嵌套的result map
     *
     * @param context
     * @param resultMappings
     * @return
     * @throws Exception
     */
    private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
        //处理不同嵌套的类型 association|collection|case
        if ("association".equals(context.getName())
                || "collection".equals(context.getName())
                || "case".equals(context.getName())) {
            /**
             * 	<resultMap id="blogResult" type="Blog">
             *   <association property="author" column="author_id" javaType="Author" select="selectAuthor"/>
             * </resultMap>
             */
            if (context.getStringAttribute("select") == null) {
                //则递归调用，在此解析，最终返回每个ResultMap对应的ID
                ResultMap resultMap = resultMapElement(context, resultMappings);
                return resultMap.getId();
            }
        }
        return null;
    }

    /**
     * 把mapper.xml和mapper接口绑定在一起
     * 规则：mapper.xml的namespace和mapper接口的路径一致，才能根据namespace自动匹配到对应的接口
     */
    private void bindMapperForNamespace() {
        String namespace = builderAssistant.getCurrentNamespace();
        if (namespace != null) {
            Class<?> boundType = null;
            try {
                boundType = Resources.classForName(namespace);
            } catch (ClassNotFoundException e) {
                //ignore, bound type is not required
            }
            if (boundType != null) {
                //如果全局配置中还没有加载对应的mapper接口
                if (!configuration.hasMapper(boundType)) {
                    //把mapper.xml和mapper接口都标示为已经加载过了
                    configuration.addLoadedResource("namespace:" + namespace);
                    configuration.addMapper(boundType);
                }
            }
        }
    }

}
