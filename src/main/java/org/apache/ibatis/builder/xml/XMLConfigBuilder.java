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
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * XML配置构建器，建造者模式,继承BaseBuilder
 * 解析mybatis-config.xml 即全局配置文件
 */
public class XMLConfigBuilder extends BaseBuilder {

    //是否已解析 默认只解析一遍配置文件
    private boolean parsed;
    //XPath解析器，解析xml文件格式的文档
    private XPathParser parser;
    //对应的数据库环境 可以配置多个数据库环境，并且可以在运行的时候临时指定一个，通过不同的ID来区分不同的数据库环境
    private String environment;

    /**
     * 以下是通过字符流创建
     *
     * @param reader
     */
    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    /**
     * 以下是通过字节流创建
     *
     * @param inputStream
     */
    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    /**
     * 通过这里进行初始化配置
     *
     * @param parser      这里parser引用了一个document，一个document代表了对应的配置文件
     * @param environment
     * @param props
     */
    private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
        //在此处最先声明全局配置，做一些初始化的工作
        super(new Configuration());
        //清空错误的上下文 用来表示在解析过程中是否存在错误
        ErrorContext.instance().resource("SQL Mapper Configuration");
        //是否存在用户自己引入的配置文件 通过和spring整合时引入的配置文件
        this.configuration.setVariables(props);
        this.parsed = false;
        //构建时指定的数据库环境
        this.environment = environment;
        this.parser = parser;
    }

    /**
     * 核心方法 用来解析xml的配置文件
     *
     * @return
     */
    public Configuration parse() {
        //默认只能解析一次
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        parsed = true;
        /** 一个简单的配置文件
         <configuration>
         <environments default="development">
         <environment id="development">
         <transactionManager type="JDBC"/>
         <dataSource type="POOLED">
         <property name="driver" value="${driver}"/>
         <property name="url" value="${url}"/>
         <property name="username" value="${username}"/>
         <property name="password" value="${password}"/>
         </dataSource>
         </environment>
         </environments>
         <mappers>
         <mapper resource="org/mybatis/example/BlogMapper.xml"/>
         </mappers>
         </configuration>
         */
        //根节点是configuration
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    /**
     * 按照结构开始解析 解析各个配置
     *
     * @param root
     */
    private void parseConfiguration(XNode root) {
        try {
            //分步骤解析 每个模块的配置
            //1.properties
            propertiesElement(root.evalNode("properties"));
            //2.类型别名
            typeAliasesElement(root.evalNode("typeAliases"));
            //3.插件
            pluginElement(root.evalNode("plugins"));
            //4.对象工厂
            objectFactoryElement(root.evalNode("objectFactory"));
            //5.对象包装工厂
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            //6.全局设置
            settingsElement(root.evalNode("settings"));
            //7.环境
            environmentsElement(root.evalNode("environments"));
            //8.databaseIdProvider
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            //9.类型处理器
            typeHandlerElement(root.evalNode("typeHandlers"));
            //10.映射器
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    /**
     * 单个类进行配置
     * <typeAliases>
     * <typeAlias alias="Author" type="domain.blog.Author"/>
     * </typeAliases>
     * or
     * 自动扫描包
     * <typeAliases>
     * <package name="domain.blog"/>
     * </typeAliases>
     */
    private void typeAliasesElement(XNode parent) {
        //是否存在typeAliases节点
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                //如果是package扫描配置
                if ("package".equals(child.getName())) {
                    String typeAliasPackage = child.getStringAttribute("name");
                    //去包下找所有类,然后注册别名(有@Alias注解则用，没有则取类的simpleName)
                    configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
                } else {
                    //如果是typeAlias单个配置
                    String alias = child.getStringAttribute("alias");
                    String type = child.getStringAttribute("type");
                    try {
                        //反射加载这个class
                        Class<?> clazz = Resources.classForName(type);
                        if (alias == null) {
                            //如果配有注解，则会取注解的名字，没有则取类的simpleName
                            typeAliasRegistry.registerAlias(clazz);
                        } else {
                            //取在配置时的自定义的别名
                            typeAliasRegistry.registerAlias(alias, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
                    }
                }
            }
        }
    }


    /**
     * <plugins>
     * <plugin interceptor="org.mybatis.example.ExamplePlugin">
     * <property name="someProperty" value="100"/>
     * </plugin>
     * </plugins>
     */
    private void pluginElement(XNode parent) throws Exception {
        //如果存在plugins节点
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                String interceptor = child.getStringAttribute("interceptor");
                //插件下面配置的一些属性
                Properties properties = child.getChildrenAsProperties();
                //需要继承Interceptor 并动态初始化一个实例
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
                interceptorInstance.setProperties(properties);
                //调用InterceptorChain.addInterceptor添加进插件链
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }

    //对象工厂,可以自定义对象创建的方式,比如用对象池？

    /**
     * <objectFactory type="org.mybatis.example.ExampleObjectFactory">
     * <property name="someProperty" value="100"/>
     * </objectFactory>
     */
    private void objectFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties properties = context.getChildrenAsProperties();
            //public class ExampleObjectFactory extends DefaultObjectFactory需要继承这个类
            ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
            factory.setProperties(properties);
            //如果没有配置会采用默认的 new DefaultObjectFactory();
            configuration.setObjectFactory(factory);
        }
    }

    //5.对象包装工厂 这个好像用的不是很多
    private void objectWrapperFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
            //如果没有配置则采用  new DefaultObjectWrapperFactory();
            configuration.setObjectWrapperFactory(factory);
        }
    }

    /**
     * <properties resource="org/mybatis/example/config.properties">
     * <property name="username" value="dev_user"/>
     * <property name="password" value="F2Fa3!33TYyg"/>
     * </properties>
     * 生效的顺序应该是 引用的外部.properties文件>resource指定路径里面的><properties></properties>里面定义的属性
     */
    private void propertiesElement(XNode context) throws Exception {
        //如果存在properties节点，则进行解析
        if (context != null) {
            Properties defaults = context.getChildrenAsProperties();
            //获取配置的文件路径
            String resource = context.getStringAttribute("resource");
            String url = context.getStringAttribute("url");
            //配置properties时 只能配置resource url中的一种
            if (resource != null && url != null) {
                throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
            }
            //配置文件里面的配置会覆盖声明的子配置
            if (resource != null) {
                defaults.putAll(Resources.getResourceAsProperties(resource));
            } else if (url != null) {
                defaults.putAll(Resources.getUrlAsProperties(url));
            }
            Properties vars = configuration.getVariables();
            if (vars != null) {
                //这里会覆盖之前写的配置
                defaults.putAll(vars);
            }
            //在后续解析的时候会用到
            parser.setVariables(defaults);
            //更新全局配置文件
            configuration.setVariables(defaults);
        }
    }

    //这些是极其重要的调整, 它们会修改 MyBatis 在运行时的行为方式

    /**
     * <settings>
     * <setting name="cacheEnabled" value="true"/>
     * <setting name="lazyLoadingEnabled" value="true"/>
     * <setting name="multipleResultSetsEnabled" value="true"/>
     * <setting name="useColumnLabel" value="true"/>
     * <setting name="useGeneratedKeys" value="false"/>
     * <setting name="enhancementEnabled" value="false"/>
     * <setting name="defaultExecutorType" value="SIMPLE"/>
     * <setting name="defaultStatementTimeout" value="25000"/>
     * <setting name="safeRowBoundsEnabled" value="false"/>
     * <setting name="mapUnderscoreToCamelCase" value="false"/>
     * <setting name="localCacheScope" value="SESSION"/>
     * <setting name="jdbcTypeForNull" value="OTHER"/>
     * <setting name="lazyLoadTriggerMethods" value="equals,clone,hashCode,toString"/>
     * </settings>
     */
    private void settingsElement(XNode context) throws Exception {
        //如果存在settings节点
        if (context != null) {
            //获取下面所有配置的属性
            Properties props = context.getChildrenAsProperties();
            //检查下是否在Configuration类里都有相应的setter方法来检查name中写的设置是否有拼写错误
            MetaClass metaConfig = MetaClass.forClass(Configuration.class);
            for (Object key : props.keySet()) {
                if (!metaConfig.hasSetter(String.valueOf(key))) {
                    throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
                }
            }
            //如何自动映射列到字段/属性  默认是匹配不嵌套的
            configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
            //是否开启二级缓存。默认是开启的
            configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
            //proxyFactory (CGLIB | JAVASSIST)
            //延迟加载的核心技术就是用代理模式，CGLIB/JAVASSIST两者选一
            configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
            //延迟加载 默认不是延迟加载
            configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
            //设置积极延迟加载
            configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), true));
            //允不允许多种结果集从一个单独的语句中返回
            configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
            //使用列标签代替列名，所以可以通过设置别名来进行映射
            configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
            //允许 JDBC 支持生成的键
            configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
            //配置默认的执行器
            configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
            //超时时间 默认是没有设置超时时间
            configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
            //是否将DB字段自动映射到驼峰式Java属性（A_COLUMN-->aColumn）
            configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
            //嵌套语句上使用RowBounds
            configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
            //默认用session级别的缓存 默认是Session
            configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
            //为null值设置jdbctype的处理类型为other，这里可以指定为null型来处理
            configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
            //Object的哪些方法将触发延迟加载
            configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
            //使用安全的ResultHandler
            configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
            //动态SQL生成语言所使用的脚本语言,默认使用XMLLanguageDriver
            configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
            //当结果集中含有Null值时是否执行映射对象的setter或者Map对象的put方法。此设置对于原始类型如int,boolean等无效。
            configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
            //logger名字的前缀
            configuration.setLogPrefix(props.getProperty("logPrefix"));
            //显式定义用什么log框架，不定义则用默认的自动发现jar包机制
            configuration.setLogImpl(resolveClass(props.getProperty("logImpl")));
            //配置工厂
            configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
        }
    }

    /**
     * <environments default="development">
     * <environment id="development">
     * <transactionManager type="JDBC">
     * <property name="..." value="..."/>
     * </transactionManager>
     * <dataSource type="POOLED">
     * <property name="driver" value="${driver}"/>
     * <property name="url" value="${url}"/>
     * <property name="username" value="${username}"/>
     * <property name="password" value="${password}"/>
     * </dataSource>
     * </environment>
     * </environments>
     */
    private void environmentsElement(XNode context) throws Exception {
        //是否存在environments节点
        if (context != null) {
            if (environment == null) {
                //使用<environments default="development">
                environment = context.getStringAttribute("default");
            }
            //可能会配置多个数据源，循环解析每个数据源
            for (XNode child : context.getChildren()) {
                //获取每个配置的数据源的ID
                String id = child.getStringAttribute("id");
                //说明一个environments标签可以配置多个environment，比如可以配置一个测试的和一个生产的
                //找到与default指定的一致的环境
                if (isSpecifiedEnvironment(id)) {
                    //与环境相关的 就是数据环境和事务
                    TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                    DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));

                    DataSource dataSource = dsFactory.getDataSource();
                    //builder模式创建
                    Environment.Builder environmentBuilder = new Environment.Builder(id)
                            .transactionFactory(txFactory)
                            .dataSource(dataSource);
                    //就是设置一个environment 里面包含datasource和transaction
                    configuration.setEnvironment(environmentBuilder.build());
                }
            }
        }
    }

    //可以根据不同数据库执行不同的SQL，sql要加databaseId属性
    //这个功能感觉不是很实用，真要多数据库支持，那SQL工作量将会成倍增长，用mybatis以后一般就绑死在一个数据库上了。但也是一个不得已的方法吧
//	<databaseIdProvider type="VENDOR">
//	  <property name="SQL Server" value="sqlserver"/>
//	  <property name="DB2" value="db2"/>        
//	  <property name="Oracle" value="oracle" />
//	</databaseIdProvider>
    private void databaseIdProviderElement(XNode context) throws Exception {
        DatabaseIdProvider databaseIdProvider = null;
        if (context != null) {
            String type = context.getStringAttribute("type");
            //与老版本兼容
            if ("VENDOR".equals(type)) {
                type = "DB_VENDOR";
            }
            Properties properties = context.getChildrenAsProperties();
            //"DB_VENDOR"-->VendorDatabaseIdProvider
            databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
            databaseIdProvider.setProperties(properties);
        }
        Environment environment = configuration.getEnvironment();
        if (environment != null && databaseIdProvider != null) {
            //得到当前的databaseId，可以调用DatabaseMetaData.getDatabaseProductName()得到诸如"Oracle (DataDirect)"的字符串，
            //然后和预定义的property比较,得出目前究竟用的是什么数据库
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            configuration.setDatabaseId(databaseId);
        }
    }

    //事务管理器

    /**
     * <transactionManager type="JDBC">
     * <property name="..." value="..."/>
     * </transactionManager>
     */

    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            //根据type="JDBC"解析返回适当的TransactionFactory 这个是提前已经注册好的，就是两种（JdbcTransactionFactory，ManagedTransactionFactory）
            TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    //数据源

    /**
     * <dataSource type="POOLED">
     * <property name="driver" value="${driver}"/>
     * <property name="url" value="${url}"/>
     * <property name="username" value="${username}"/>
     * <property name="password" value="${password}"/>
     * </dataSource>
     */

    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            //根据type="POOLED"解析返回适当的DataSourceFactory 也是提前注册好的（JndiDataSourceFactory，PooledDataSourceFactory，UnpooledDataSourceFactory）
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    //类型处理，一般会在ExampleTypeHandler上进行注解的声明

    /**
     * <typeHandlers>
     * <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
     * </typeHandlers>
     * or
     * <typeHandlers>
     * <package name="org.mybatis.example"/>
     * </typeHandlers>
     */
    private void typeHandlerElement(XNode parent) throws Exception {
        //如果存在typeHandlers
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                //如果是package
                if ("package".equals(child.getName())) {
                    String typeHandlerPackage = child.getStringAttribute("name");
                    //整包资源进行注册
                    typeHandlerRegistry.register(typeHandlerPackage);
                } else {
                    //如果是typeHandler，手动指定的属性
                    String javaTypeName = child.getStringAttribute("javaType");
                    String jdbcTypeName = child.getStringAttribute("jdbcType");
                    String handlerTypeName = child.getStringAttribute("handler");
                    Class<?> javaTypeClass = resolveClass(javaTypeName);
                    JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
                    Class<?> typeHandlerClass = resolveClass(handlerTypeName);
                    //开始根据javaType和jdbcType进行注册 typeHandler不能为空
                    if (javaTypeClass != null) {
                        if (jdbcType == null) {
                            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
                        } else {
                            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
                        }
                    } else {
                        //如果只是注册一个类，则说明详细信息存在注解上
                        typeHandlerRegistry.register(typeHandlerClass);
                    }
                }
            }
        }
    }

    //映射器

    /**
     * 1使用类路径
     * <mappers>
     * <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
     * <mapper resource="org/mybatis/builder/BlogMapper.xml"/>
     * <mapper resource="org/mybatis/builder/PostMapper.xml"/>
     * </mappers>
     * <p>
     * 2使用绝对url路径
     * <mappers>
     * <mapper url="file:///var/mappers/AuthorMapper.xml"/>
     * <mapper url="file:///var/mappers/BlogMapper.xml"/>
     * <mapper url="file:///var/mappers/PostMapper.xml"/>
     * </mappers>
     * <p>
     * 3使用java类名
     * <mappers>
     * <mapper class="org.mybatis.builder.AuthorMapper"/>
     * <mapper class="org.mybatis.builder.BlogMapper"/>
     * <mapper class="org.mybatis.builder.PostMapper"/>
     * </mappers>
     * <p>
     * 4自动扫描包下所有映射器
     * <mappers>
     * <package name="org.mybatis.builder"/>
     * </mappers>
     */
    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    //自动扫描包下所有映射器
                    String mapperPackage = child.getStringAttribute("name");
                    //在添加接口的时候 会自动加载并解析同目录下的.xml配置文件
                    configuration.addMappers(mapperPackage);
                } else {
                    String resource = child.getStringAttribute("resource");
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    //加载xml文件以及接口的方式
                    if (resource != null && url == null && mapperClass == null) {
                        ErrorContext.instance().resource(resource);
                        //根据配置的路径 读取这个xml文件
                        InputStream inputStream = Resources.getResourceAsStream(resource);
                        //注意在for循环里每个mapper都重新new一个XMLMapperBuilder，来解析
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                        mapperParser.parse();
                    } else if (resource == null && url != null && mapperClass == null) {
                        ErrorContext.instance().resource(url);
                        InputStream inputStream = Resources.getUrlAsStream(url);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                        mapperParser.parse();
                    } else if (resource == null && url == null && mapperClass != null) {
                        //10.3使用java类名
                        Class<?> mapperInterface = Resources.classForName(mapperClass);
                        //加入接口的时候 已加载了对应的.xml配置文件
                        configuration.addMapper(mapperInterface);
                    } else {
                        throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
                    }
                }
            }
        }
    }

    //比较id和environment是否相等，比较是是否是需要的数据库
    private boolean isSpecifiedEnvironment(String id) {
        if (environment == null) {
            throw new BuilderException("No environment specified.");
        } else if (id == null) {
            throw new BuilderException("Environment requires an id attribute.");
        } else if (environment.equals(id)) {
            return true;
        }
        return false;
    }

}
