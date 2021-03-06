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
package org.apache.ibatis.executor.resultset;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.loader.ResultLoader;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.executor.result.DefaultResultHandler;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.lang.reflect.Constructor;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * 默认数据结果处理器，很重要
 */
public class DefaultResultSetHandler implements ResultSetHandler {

    //没有返回结果集 默认的结果集
    private static final Object NO_VALUE = new Object();
    //执行器
    private final Executor executor;
    private final Configuration configuration;
    private final MappedStatement mappedStatement;
    private final RowBounds rowBounds;
    //参数处理器
    private final ParameterHandler parameterHandler;
    //结果处理器
    private final ResultHandler resultHandler;
    private final BoundSql boundSql;
    private final TypeHandlerRegistry typeHandlerRegistry;
    //对象生产工厂
    private final ObjectFactory objectFactory;

    // nested resultmaps 嵌套的结果集
    private final Map<CacheKey, Object> nestedResultObjects = new HashMap<CacheKey, Object>();
    private final Map<CacheKey, Object> ancestorObjects = new HashMap<CacheKey, Object>();
    private final Map<String, String> ancestorColumnPrefix = new HashMap<String, String>();

    // multiple resultsets
    private final Map<String, ResultMapping> nextResultMaps = new HashMap<String, ResultMapping>();
    private final Map<CacheKey, List<PendingRelation>> pendingRelations = new HashMap<CacheKey, List<PendingRelation>>();

    /**
     * 这个是不是延迟加载的？？？
     */
    private static class PendingRelation {
        public MetaObject metaObject;
        public ResultMapping propertyMapping;
    }

    //数据几乎都是传递进来的
    public DefaultResultSetHandler(Executor executor, MappedStatement mappedStatement, ParameterHandler parameterHandler, ResultHandler resultHandler, BoundSql boundSql,
                                   RowBounds rowBounds) {
        this.executor = executor;
        this.configuration = mappedStatement.getConfiguration();
        this.mappedStatement = mappedStatement;
        this.rowBounds = rowBounds;
        this.parameterHandler = parameterHandler;
        this.boundSql = boundSql;
        this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        this.objectFactory = configuration.getObjectFactory();
        this.resultHandler = resultHandler;
    }

    //
    // HANDLE OUTPUT PARAMETER
    //
    //处理存储过程
    @Override
    public void handleOutputParameters(CallableStatement cs) throws SQLException {
        //获取参数
        final Object parameterObject = parameterHandler.getParameterObject();
        final MetaObject metaParam = configuration.newMetaObject(parameterObject);
        //获取参数映射
        final List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        //循环处理每个参数
        for (int i = 0; i < parameterMappings.size(); i++) {
            final ParameterMapping parameterMapping = parameterMappings.get(i);
            //只处理OUT|INOUT
            if (parameterMapping.getMode() == ParameterMode.OUT || parameterMapping.getMode() == ParameterMode.INOUT) {
                if (ResultSet.class.equals(parameterMapping.getJavaType())) {
                    //如果是ResultSet型(游标)
                    //#{result, jdbcType=CURSOR, mode=OUT, javaType=ResultSet, resultMap=userResultMap}
                    //先用CallableStatement.getObject取得这个游标，作为参数传进去
                    handleRefCursorOutputParameter((ResultSet) cs.getObject(i + 1), parameterMapping, metaParam);
                } else {
                    //否则是普通型，核心就是CallableStatement.getXXX取得值
                    final TypeHandler<?> typeHandler = parameterMapping.getTypeHandler();
                    //直接获取值进行回填
                    metaParam.setValue(parameterMapping.getProperty(), typeHandler.getResult(cs, i + 1));
                }
            }
        }
    }

    //处理游标(OUT参数)
    private void handleRefCursorOutputParameter(ResultSet rs, ParameterMapping parameterMapping, MetaObject metaParam) throws SQLException {
        try {
            //获取对应的resultMap
            final String resultMapId = parameterMapping.getResultMapId();
            //获取配置的resultMap
            final ResultMap resultMap = configuration.getResultMap(resultMapId);
            //默认结果处理器
            final DefaultResultHandler resultHandler = new DefaultResultHandler(objectFactory);
            //包装结果集
            final ResultSetWrapper rsw = new ResultSetWrapper(rs, configuration);
            //里面就和一般ResultSet处理没两样了
            handleRowValues(rsw, resultMap, resultHandler, new RowBounds(), null);
            //利用反射，设置结果
            metaParam.setValue(parameterMapping.getProperty(), resultHandler.getResultList());
        } finally {
            // issue #228 (close resultsets)
            closeResultSet(rs);
        }
    }

    /**
     * 处理查询返回的结果集
     */
    @Override
    public List<Object> handleResultSets(Statement stmt) throws SQLException {
        ErrorContext.instance().activity("handling results").object(mappedStatement.getId());
        //存放最终的结果集
        final List<Object> multipleResults = new ArrayList<Object>();
        //已经处理的结果的数量
        int resultSetCount = 0;
        //包装结果级 封装返回结果的一些详细信息
        ResultSetWrapper rsw = getFirstResultSet(stmt);

        //得到定义的所有resultMap，来和结果集进行映射
        List<ResultMap> resultMaps = mappedStatement.getResultMaps();
        //一个mapper.xml中定义的resultMap的数量
        int resultMapCount = resultMaps.size();

        //当有结果集时要保证有对应的映射map（如果是pojo,或是基本类型也会存在 ）
        validateResultMapsCount(rsw, resultMapCount);

        while (rsw != null && resultMapCount > resultSetCount) {
            //获取每一个resultMap进行匹配
            ResultMap resultMap = resultMaps.get(resultSetCount);
            //开始进行处理
            handleResultSet(rsw, resultMap, multipleResults, null);
            rsw = getNextResultSet(stmt);
            cleanUpAfterHandlingResultSet();
            resultSetCount++;
        }

        String[] resultSets = mappedStatement.getResulSets();
        if (resultSets != null) {
            while (rsw != null && resultSetCount < resultSets.length) {
                ResultMapping parentMapping = nextResultMaps.get(resultSets[resultSetCount]);
                if (parentMapping != null) {
                    String nestedResultMapId = parentMapping.getNestedResultMapId();
                    ResultMap resultMap = configuration.getResultMap(nestedResultMapId);
                    handleResultSet(rsw, resultMap, null, parentMapping);
                }
                rsw = getNextResultSet(stmt);
                cleanUpAfterHandlingResultSet();
                resultSetCount++;
            }
        }

        return collapseSingleResultList(multipleResults);
    }

    /**
     * 包装一下结果集
     */
    private ResultSetWrapper getFirstResultSet(Statement stmt) throws SQLException {
        //获取到查询的结果集
        ResultSet rs = stmt.getResultSet();
        //HSQLDB2.1特殊情况处理
        while (rs == null) {
            // move forward to get the first resultset in case the driver
            // doesn't return the resultset as the first result (HSQLDB 2.1)
            if (stmt.getMoreResults()) {
                rs = stmt.getResultSet();
            } else {
                if (stmt.getUpdateCount() == -1) {
                    // no more results. Must be no resultset
                    break;
                }
            }
        }
        //返回一个对结果集的包装
        return rs != null ? new ResultSetWrapper(rs, configuration) : null;
    }

    private ResultSetWrapper getNextResultSet(Statement stmt) throws SQLException {
        // Making this method tolerant of bad JDBC drivers
        try {
            if (stmt.getConnection().getMetaData().supportsMultipleResultSets()) {
                // Crazy Standard JDBC way of determining if there are more results
                if (!((!stmt.getMoreResults()) && (stmt.getUpdateCount() == -1))) {
                    ResultSet rs = stmt.getResultSet();
                    return rs != null ? new ResultSetWrapper(rs, configuration) : null;
                }
            }
        } catch (Exception e) {
            // Intentionally ignored.
        }
        return null;
    }

    private void closeResultSet(ResultSet rs) {
        try {
            if (rs != null) {
                rs.close();
            }
        } catch (SQLException e) {
            // ignore
        }
    }

    private void cleanUpAfterHandlingResultSet() {
        nestedResultObjects.clear();
        ancestorColumnPrefix.clear();
    }

    /**
     * 是否有对应的resultMap
     *
     * @param rsw
     * @param resultMapCount
     */
    private void validateResultMapsCount(ResultSetWrapper rsw, int resultMapCount) {
        if (rsw != null && resultMapCount < 1) {
            throw new ExecutorException("A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
                    + "'.  It's likely that neither a Result Type nor a Result Map was specified.");
        }
    }

    //处理结果集
    private void handleResultSet(ResultSetWrapper rsw, ResultMap resultMap, List<Object> multipleResults, ResultMapping parentMapping) throws SQLException {
        try {
            if (parentMapping != null) {
                handleRowValues(rsw, resultMap, null, RowBounds.DEFAULT, parentMapping);
            } else {
                if (resultHandler == null) {
                    //如果没有resultHandler
                    //新建DefaultResultHandler
                    DefaultResultHandler defaultResultHandler = new DefaultResultHandler(objectFactory);
                    //调用自己的handleRowValues
                    handleRowValues(rsw, resultMap, defaultResultHandler, rowBounds, null);
                    //得到记录的list
                    multipleResults.add(defaultResultHandler.getResultList());
                } else {
                    //如果有resultHandler
                    handleRowValues(rsw, resultMap, resultHandler, rowBounds, null);
                }
            }
        } finally {
            //最后别忘了关闭结果集，这个居然出bug了
            // issue #228 (close resultsets)
            closeResultSet(rsw.getResultSet());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> collapseSingleResultList(List<Object> multipleResults) {
        return multipleResults.size() == 1 ? (List<Object>) multipleResults.get(0) : multipleResults;
    }

    //
    // HANDLE ROWS FOR SIMPLE RESULTMAP
    //
    //处理每一行结果集
    private void handleRowValues(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
        //对应的resultMap是否有嵌套查询
        if (resultMap.hasNestedResultMaps()) {
            ensureNoRowBounds();
            checkResultHandler();
            handleRowValuesForNestedResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
        } else {
            //如果没有嵌套查询
            handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
        }
    }

    private void ensureNoRowBounds() {
        if (configuration.isSafeRowBoundsEnabled() && rowBounds != null && (rowBounds.getLimit() < RowBounds.NO_ROW_LIMIT || rowBounds.getOffset() > RowBounds.NO_ROW_OFFSET)) {
            throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely constrained by RowBounds. "
                    + "Use safeRowBoundsEnabled=false setting to bypass this check.");
        }
    }

    protected void checkResultHandler() {
        if (resultHandler != null && configuration.isSafeResultHandlerEnabled() && !mappedStatement.isResultOrdered()) {
            throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely used with a custom ResultHandler. "
                    + "Use safeResultHandlerEnabled=false setting to bypass this check "
                    + "or ensure your statement returns ordered data and set resultOrdered=true on it.");
        }
    }

    /**
     * 简单的结果集映射
     */
    private void handleRowValuesForSimpleResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler resultHandler, RowBounds rowBounds, ResultMapping parentMapping)
            throws SQLException {
        DefaultResultContext resultContext = new DefaultResultContext();
        //定位到RowBounds设置的位置，即分页开始的位置
        skipRows(rsw.getResultSet(), rowBounds);
        //如果且还存在结果集，且还没有到分页的结尾
        while (shouldProcessMoreRows(resultContext, rowBounds) && rsw.getResultSet().next()) {
            //如果存在鉴别器 则找到对应的ResultMap
            ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw.getResultSet(), resultMap, null);
            Object rowValue = getRowValue(rsw, discriminatedResultMap);
            storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
        }
    }

    private void storeObject(ResultHandler resultHandler, DefaultResultContext resultContext, Object rowValue, ResultMapping parentMapping, ResultSet rs) throws SQLException {
        if (parentMapping != null) {
            linkToParents(rs, parentMapping, rowValue);
        } else {
            callResultHandler(resultHandler, resultContext, rowValue);
        }
    }

    private void callResultHandler(ResultHandler resultHandler, DefaultResultContext resultContext, Object rowValue) {
        resultContext.nextResultObject(rowValue);
        resultHandler.handleResult(resultContext);
    }

    /**
     * 是否还继续处理结果集以及是否已经到了分页的结尾
     */
    private boolean shouldProcessMoreRows(ResultContext context, RowBounds rowBounds) throws SQLException {
        return !context.isStopped() && context.getResultCount() < rowBounds.getLimit();
    }

    /**
     * 如果有内存分页，通过移动游标获取对应的结果集的位置
     */
    private void skipRows(ResultSet rs, RowBounds rowBounds) throws SQLException {
        //如果可以直接定位游标
        if (rs.getType() != ResultSet.TYPE_FORWARD_ONLY) {
            //移动指定的偏移量
            if (rowBounds.getOffset() != RowBounds.NO_ROW_OFFSET) {
                rs.absolute(rowBounds.getOffset());
            }
        } else {
            for (int i = 0; i < rowBounds.getOffset(); i++) {
                //移动游标
                rs.next();
            }
        }
    }

    //
    // GET VALUE FROM ROW FOR SIMPLE RESULT MAP
    //

    //核心，取得一行的值
    private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap) throws SQLException {
        //实例化ResultLoaderMap(延迟加载器)
        final ResultLoaderMap lazyLoader = new ResultLoaderMap();
        //调用自己的createResultObject,内部就是new一个对象(如果是简单类型，new完也把值赋进去)
        Object resultObject = createResultObject(rsw, resultMap, lazyLoader, null);
        if (resultObject != null && !typeHandlerRegistry.hasTypeHandler(resultMap.getType())) {
            //一般不是简单类型不会有typehandler,这个if会进来
            final MetaObject metaObject = configuration.newMetaObject(resultObject);
            boolean foundValues = !resultMap.getConstructorResultMappings().isEmpty();
            if (shouldApplyAutomaticMappings(resultMap, false)) {
                //自动映射咯
                //这里把每个列的值都赋到相应的字段里去了
                foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, null) || foundValues;
            }
            foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, null) || foundValues;
            foundValues = lazyLoader.size() > 0 || foundValues;
            resultObject = foundValues ? resultObject : null;
            return resultObject;
        }
        return resultObject;
    }

    private boolean shouldApplyAutomaticMappings(ResultMap resultMap, boolean isNested) {
        if (resultMap.getAutoMapping() != null) {
            return resultMap.getAutoMapping();
        } else {
            if (isNested) {
                return AutoMappingBehavior.FULL == configuration.getAutoMappingBehavior();
            } else {
                return AutoMappingBehavior.NONE != configuration.getAutoMappingBehavior();
            }
        }
    }

    //
    // PROPERTY MAPPINGS
    //

    private boolean applyPropertyMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, ResultLoaderMap lazyLoader, String columnPrefix)
            throws SQLException {
        final List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
        boolean foundValues = false;
        final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
        for (ResultMapping propertyMapping : propertyMappings) {
            final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
            if (propertyMapping.isCompositeResult()
                    || (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH)))
                    || propertyMapping.getResultSet() != null) {
                Object value = getPropertyMappingValue(rsw.getResultSet(), metaObject, propertyMapping, lazyLoader, columnPrefix);
                // issue #541 make property optional
                final String property = propertyMapping.getProperty();
                // issue #377, call setter on nulls
                if (value != NO_VALUE && property != null && (value != null || configuration.isCallSettersOnNulls())) {
                    if (value != null || !metaObject.getSetterType(property).isPrimitive()) {
                        metaObject.setValue(property, value);
                    }
                    foundValues = true;
                }
            }
        }
        return foundValues;
    }

    private Object getPropertyMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
            throws SQLException {
        if (propertyMapping.getNestedQueryId() != null) {
            return getNestedQueryMappingValue(rs, metaResultObject, propertyMapping, lazyLoader, columnPrefix);
        } else if (propertyMapping.getResultSet() != null) {
            addPendingChildRelation(rs, metaResultObject, propertyMapping);
            return NO_VALUE;
        } else if (propertyMapping.getNestedResultMapId() != null) {
            // the user added a column attribute to a nested result map, ignore it
            return NO_VALUE;
        } else {
            final TypeHandler<?> typeHandler = propertyMapping.getTypeHandler();
            final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
            return typeHandler.getResult(rs, column);
        }
    }

    //自动映射咯
    private boolean applyAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
        final List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);
        boolean foundValues = false;
        for (String columnName : unmappedColumnNames) {
            String propertyName = columnName;
            if (columnPrefix != null && !columnPrefix.isEmpty()) {
                // When columnPrefix is specified,
                // ignore columns without the prefix.
                if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
                    propertyName = columnName.substring(columnPrefix.length());
                } else {
                    continue;
                }
            }
            final String property = metaObject.findProperty(propertyName, configuration.isMapUnderscoreToCamelCase());
            if (property != null && metaObject.hasSetter(property)) {
                final Class<?> propertyType = metaObject.getSetterType(property);
                if (typeHandlerRegistry.hasTypeHandler(propertyType)) {
                    final TypeHandler<?> typeHandler = rsw.getTypeHandler(propertyType, columnName);
                    //巧妙的用TypeHandler取得结果
                    final Object value = typeHandler.getResult(rsw.getResultSet(), columnName);
                    // issue #377, call setter on nulls
                    if (value != null || configuration.isCallSettersOnNulls()) {
                        if (value != null || !propertyType.isPrimitive()) {
                            //然后巧妙的用反射来设置到对象
                            metaObject.setValue(property, value);
                        }
                        foundValues = true;
                    }
                }
            }
        }
        return foundValues;
    }

    // MULTIPLE RESULT SETS

    private void linkToParents(ResultSet rs, ResultMapping parentMapping, Object rowValue) throws SQLException {
        CacheKey parentKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(), parentMapping.getForeignColumn());
        List<PendingRelation> parents = pendingRelations.get(parentKey);
        for (PendingRelation parent : parents) {
            if (parent != null) {
                final Object collectionProperty = instantiateCollectionPropertyIfAppropriate(parent.propertyMapping, parent.metaObject);
                if (rowValue != null) {
                    if (collectionProperty != null) {
                        final MetaObject targetMetaObject = configuration.newMetaObject(collectionProperty);
                        targetMetaObject.add(rowValue);
                    } else {
                        parent.metaObject.setValue(parent.propertyMapping.getProperty(), rowValue);
                    }
                }
            }
        }
    }

    private Object instantiateCollectionPropertyIfAppropriate(ResultMapping resultMapping, MetaObject metaObject) {
        final String propertyName = resultMapping.getProperty();
        Object propertyValue = metaObject.getValue(propertyName);
        if (propertyValue == null) {
            Class<?> type = resultMapping.getJavaType();
            if (type == null) {
                type = metaObject.getSetterType(propertyName);
            }
            try {
                if (objectFactory.isCollection(type)) {
                    propertyValue = objectFactory.create(type);
                    metaObject.setValue(propertyName, propertyValue);
                    return propertyValue;
                }
            } catch (Exception e) {
                throw new ExecutorException("Error instantiating collection property for result '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
            }
        } else if (objectFactory.isCollection(propertyValue.getClass())) {
            return propertyValue;
        }
        return null;
    }

    private void addPendingChildRelation(ResultSet rs, MetaObject metaResultObject, ResultMapping parentMapping) throws SQLException {
        CacheKey cacheKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(), parentMapping.getColumn());
        PendingRelation deferLoad = new PendingRelation();
        deferLoad.metaObject = metaResultObject;
        deferLoad.propertyMapping = parentMapping;
        List<PendingRelation> relations = pendingRelations.get(cacheKey);
        // issue #255
        if (relations == null) {
            relations = new ArrayList<PendingRelation>();
            pendingRelations.put(cacheKey, relations);
        }
        relations.add(deferLoad);
        ResultMapping previous = nextResultMaps.get(parentMapping.getResultSet());
        if (previous == null) {
            nextResultMaps.put(parentMapping.getResultSet(), parentMapping);
        } else {
            if (!previous.equals(parentMapping)) {
                throw new ExecutorException("Two different properties are mapped to the same resultSet");
            }
        }
    }

    private CacheKey createKeyForMultipleResults(ResultSet rs, ResultMapping resultMapping, String names, String columns) throws SQLException {
        CacheKey cacheKey = new CacheKey();
        cacheKey.update(resultMapping);
        if (columns != null && names != null) {
            String[] columnsArray = columns.split(",");
            String[] namesArray = names.split(",");
            for (int i = 0; i < columnsArray.length; i++) {
                Object value = rs.getString(columnsArray[i]);
                if (value != null) {
                    cacheKey.update(namesArray[i]);
                    cacheKey.update(value);
                }
            }
        }
        return cacheKey;
    }

    //
    // INSTANTIATION & CONSTRUCTOR MAPPING
    //
    //创建结果集的映射对象
    private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, ResultLoaderMap lazyLoader, String columnPrefix) throws SQLException {

        final List<Class<?>> constructorArgTypes = new ArrayList<Class<?>>();
        final List<Object> constructorArgs = new ArrayList<Object>();
        //创建结果对象
        final Object resultObject = createResultObject(rsw, resultMap, constructorArgTypes, constructorArgs, columnPrefix);
        //如果不存在对应的结果集处理器，为复合类型
        if (resultObject != null && !typeHandlerRegistry.hasTypeHandler(resultMap.getType())) {
            final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
            for (ResultMapping propertyMapping : propertyMappings) {
                // issue gcode #109 && issue #149
                //如果存在嵌套查询
                if (propertyMapping.getNestedQueryId() != null && propertyMapping.isLazy()) {
                    //TODO 使用代理(cglib/javaassist)
                    return configuration.getProxyFactory().createProxy(resultObject, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
                }
            }
        }
        return resultObject;
    }

    //创建结果对象
    private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix)
            throws SQLException {
        //得到result type即对应的pojo类型
        final Class<?> resultType = resultMap.getType();
        final MetaClass metaType = MetaClass.forClass(resultType);
        //获取xml中配置的初始化的参数映射关系
        final List<ResultMapping> constructorMappings = resultMap.getConstructorResultMappings();
        //如果有对应的类型处理器  基本型
        if (typeHandlerRegistry.hasTypeHandler(resultType)) {
            return createPrimitiveResultObject(rsw, resultMap, columnPrefix);
        } else if (!constructorMappings.isEmpty()) {
            //有参数的构造函数
            return createParameterizedResultObject(rsw, resultType, constructorMappings, constructorArgTypes, constructorArgs, columnPrefix);
        } else if (resultType.isInterface() || metaType.hasDefaultConstructor()) {
            //普通bean类型
            return objectFactory.create(resultType);
        } else if (shouldApplyAutomaticMappings(resultMap, false)) {
            //自动映射
            return createByConstructorSignature(rsw, resultType, constructorArgTypes, constructorArgs, columnPrefix);
        }
        throw new ExecutorException("Do not know how to create an instance of " + resultType);
    }

    private Object createParameterizedResultObject(ResultSetWrapper rsw, Class<?> resultType, List<ResultMapping> constructorMappings,
                                                   List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix) throws SQLException {
        boolean foundValues = false;
        //构造参数映射
        for (ResultMapping constructorMapping : constructorMappings) {
            final Class<?> parameterType = constructorMapping.getJavaType();
            final String column = constructorMapping.getColumn();
            final Object value;
            //是否有嵌套查询
            if (constructorMapping.getNestedQueryId() != null) {
                value = getNestedQueryConstructorValue(rsw.getResultSet(), constructorMapping, columnPrefix);
            } else if (constructorMapping.getNestedResultMapId() != null) {
                final ResultMap resultMap = configuration.getResultMap(constructorMapping.getNestedResultMapId());
                value = getRowValue(rsw, resultMap);
            } else {
                final TypeHandler<?> typeHandler = constructorMapping.getTypeHandler();
                value = typeHandler.getResult(rsw.getResultSet(), prependPrefix(column, columnPrefix));
            }
            constructorArgTypes.add(parameterType);
            constructorArgs.add(value);
            foundValues = value != null || foundValues;
        }
        //创建有参数的构造函数
        return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
    }

    private Object createByConstructorSignature(ResultSetWrapper rsw, Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs,
                                                String columnPrefix) throws SQLException {
        for (Constructor<?> constructor : resultType.getDeclaredConstructors()) {
            if (typeNames(constructor.getParameterTypes()).equals(rsw.getClassNames())) {
                boolean foundValues = false;
                for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                    Class<?> parameterType = constructor.getParameterTypes()[i];
                    String columnName = rsw.getColumnNames().get(i);
                    TypeHandler<?> typeHandler = rsw.getTypeHandler(parameterType, columnName);
                    Object value = typeHandler.getResult(rsw.getResultSet(), prependPrefix(columnName, columnPrefix));
                    constructorArgTypes.add(parameterType);
                    constructorArgs.add(value);
                    foundValues = value != null || foundValues;
                }
                //上面是构造函数创建对象，下面是对象工厂来创建
                return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
            }
        }
        throw new ExecutorException("No constructor found in " + resultType.getName() + " matching " + rsw.getClassNames());
    }

    private List<String> typeNames(Class<?>[] parameterTypes) {
        List<String> names = new ArrayList<String>();
        for (Class<?> type : parameterTypes) {
            names.add(type.getName());
        }
        return names;
    }

    /**
     * 结果集为简单类型
     */
    private Object createPrimitiveResultObject(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix) throws SQLException {
        final Class<?> resultType = resultMap.getType();
        final String columnName;
        if (!resultMap.getResultMappings().isEmpty()) {
            final List<ResultMapping> resultMappingList = resultMap.getResultMappings();
            final ResultMapping mapping = resultMappingList.get(0);
            columnName = prependPrefix(mapping.getColumn(), columnPrefix);
        } else {
            //因为只有1列，所以取得这一列的名字
            columnName = rsw.getColumnNames().get(0);
        }
        //找到对应的处理器
        final TypeHandler<?> typeHandler = rsw.getTypeHandler(resultType, columnName);
        //获取对应类型的返回值
        return typeHandler.getResult(rsw.getResultSet(), columnName);
    }

    //
    // NESTED QUERY
    //

    private Object getNestedQueryConstructorValue(ResultSet rs, ResultMapping constructorMapping, String columnPrefix) throws SQLException {
        final String nestedQueryId = constructorMapping.getNestedQueryId();
        final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
        final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();
        final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, constructorMapping, nestedQueryParameterType, columnPrefix);
        Object value = null;
        if (nestedQueryParameterObject != null) {
            final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
            final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
            final Class<?> targetType = constructorMapping.getJavaType();
            final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
            value = resultLoader.loadResult();
        }
        return value;
    }

    //得到嵌套查询值
    private Object getNestedQueryMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
            throws SQLException {
        final String nestedQueryId = propertyMapping.getNestedQueryId();
        final String property = propertyMapping.getProperty();
        final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
        final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();
        final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, propertyMapping, nestedQueryParameterType, columnPrefix);
        Object value = NO_VALUE;
        if (nestedQueryParameterObject != null) {
            final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
            final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
            final Class<?> targetType = propertyMapping.getJavaType();
            if (executor.isCached(nestedQuery, key)) {
                //如果已经有一级缓存了，则延迟加载(实际上deferLoad方法中可以看到则是立即加载)
                executor.deferLoad(nestedQuery, metaResultObject, property, key, targetType);
            } else {
                //否则lazyLoader.addLoader 需要延迟加载则addLoader
                //或者ResultLoader.loadResult 不需要延迟加载则立即加载
                final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
                if (propertyMapping.isLazy()) {
                    lazyLoader.addLoader(property, metaResultObject, resultLoader);
                } else {
                    value = resultLoader.loadResult();
                }
            }
        }
        return value;
    }

    private Object prepareParameterForNestedQuery(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
        if (resultMapping.isCompositeResult()) {
            return prepareCompositeKeyParameter(rs, resultMapping, parameterType, columnPrefix);
        } else {
            return prepareSimpleKeyParameter(rs, resultMapping, parameterType, columnPrefix);
        }
    }

    private Object prepareSimpleKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
        final TypeHandler<?> typeHandler;
        if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
            typeHandler = typeHandlerRegistry.getTypeHandler(parameterType);
        } else {
            typeHandler = typeHandlerRegistry.getUnknownTypeHandler();
        }
        return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
    }

    private Object prepareCompositeKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
        final Object parameterObject = instantiateParameterObject(parameterType);
        final MetaObject metaObject = configuration.newMetaObject(parameterObject);
        boolean foundValues = false;
        for (ResultMapping innerResultMapping : resultMapping.getComposites()) {
            final Class<?> propType = metaObject.getSetterType(innerResultMapping.getProperty());
            final TypeHandler<?> typeHandler = typeHandlerRegistry.getTypeHandler(propType);
            final Object propValue = typeHandler.getResult(rs, prependPrefix(innerResultMapping.getColumn(), columnPrefix));
            // issue #353 & #560 do not execute nested query if key is null
            if (propValue != null) {
                metaObject.setValue(innerResultMapping.getProperty(), propValue);
                foundValues = true;
            }
        }
        return foundValues ? parameterObject : null;
    }

    private Object instantiateParameterObject(Class<?> parameterType) {
        if (parameterType == null) {
            return new HashMap<Object, Object>();
        } else {
            return objectFactory.create(parameterType);
        }
    }

    //
    // DISCRIMINATOR
    //

    /**
     * @param rs           查询返回的结果集
     * @param resultMap    配置的映射map
     * @param columnPrefix 配置的列名的前缀
     * @return
     * @throws SQLException
     */
    public ResultMap resolveDiscriminatedResultMap(ResultSet rs, ResultMap resultMap, String columnPrefix) throws SQLException {
        Set<String> pastDiscriminators = new HashSet<String>();
        Discriminator discriminator = resultMap.getDiscriminator();
        //判断是否有鉴别器
        while (discriminator != null) {
            //判断是否有鉴别器
            final Object value = getDiscriminatorValue(rs, discriminator, columnPrefix);

            final String discriminatedMapId = discriminator.getMapIdFor(String.valueOf(value));
            if (configuration.hasResultMap(discriminatedMapId)) {
                resultMap = configuration.getResultMap(discriminatedMapId);
                Discriminator lastDiscriminator = discriminator;
                discriminator = resultMap.getDiscriminator();
                if (discriminator == lastDiscriminator || !pastDiscriminators.add(discriminatedMapId)) {
                    break;
                }
            } else {
                break;
            }
        }
        return resultMap;
    }

    private Object getDiscriminatorValue(ResultSet rs, Discriminator discriminator, String columnPrefix) throws SQLException {
        final ResultMapping resultMapping = discriminator.getResultMapping();
        final TypeHandler<?> typeHandler = resultMapping.getTypeHandler();
        return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
    }

    private String prependPrefix(String columnName, String prefix) {
        if (columnName == null || columnName.length() == 0 || prefix == null || prefix.length() == 0) {
            return columnName;
        }
        return prefix + columnName;
    }

    //
    // HANDLE NESTED RESULT MAPS
    //

    private void handleRowValuesForNestedResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
        final DefaultResultContext resultContext = new DefaultResultContext();
        skipRows(rsw.getResultSet(), rowBounds);
        Object rowValue = null;
        while (shouldProcessMoreRows(resultContext, rowBounds) && rsw.getResultSet().next()) {
            final ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw.getResultSet(), resultMap, null);
            final CacheKey rowKey = createRowKey(discriminatedResultMap, rsw, null);
            Object partialObject = nestedResultObjects.get(rowKey);
            // issue #577 && #542
            if (mappedStatement.isResultOrdered()) {
                if (partialObject == null && rowValue != null) {
                    nestedResultObjects.clear();
                    storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
                }
                rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, rowKey, null, partialObject);
            } else {
                rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, rowKey, null, partialObject);
                if (partialObject == null) {
                    storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
                }
            }
        }
        if (rowValue != null && mappedStatement.isResultOrdered() && shouldProcessMoreRows(resultContext, rowBounds)) {
            storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
        }
    }

    //
    // GET VALUE FROM ROW FOR NESTED RESULT MAP
    //

    private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap, CacheKey combinedKey, CacheKey absoluteKey, String columnPrefix, Object partialObject) throws SQLException {
        final String resultMapId = resultMap.getId();
        Object resultObject = partialObject;
        if (resultObject != null) {
            final MetaObject metaObject = configuration.newMetaObject(resultObject);
            putAncestor(absoluteKey, resultObject, resultMapId, columnPrefix);
            applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, false);
            ancestorObjects.remove(absoluteKey);
        } else {
            final ResultLoaderMap lazyLoader = new ResultLoaderMap();
            resultObject = createResultObject(rsw, resultMap, lazyLoader, columnPrefix);
            if (resultObject != null && !typeHandlerRegistry.hasTypeHandler(resultMap.getType())) {
                final MetaObject metaObject = configuration.newMetaObject(resultObject);
                boolean foundValues = !resultMap.getConstructorResultMappings().isEmpty();
                if (shouldApplyAutomaticMappings(resultMap, true)) {
                    foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
                }
                foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, columnPrefix) || foundValues;
                putAncestor(absoluteKey, resultObject, resultMapId, columnPrefix);
                foundValues = applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, true) || foundValues;
                ancestorObjects.remove(absoluteKey);
                foundValues = lazyLoader.size() > 0 || foundValues;
                resultObject = foundValues ? resultObject : null;
            }
            if (combinedKey != CacheKey.NULL_CACHE_KEY) {
                nestedResultObjects.put(combinedKey, resultObject);
            }
        }
        return resultObject;
    }

    private void putAncestor(CacheKey rowKey, Object resultObject, String resultMapId, String columnPrefix) {
        if (!ancestorColumnPrefix.containsKey(resultMapId)) {
            ancestorColumnPrefix.put(resultMapId, columnPrefix);
        }
        ancestorObjects.put(rowKey, resultObject);
    }

    //
    // NESTED RESULT MAP (JOIN MAPPING)
    //

    private boolean applyNestedResultMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String parentPrefix, CacheKey parentRowKey, boolean newObject) {
        boolean foundValues = false;
        for (ResultMapping resultMapping : resultMap.getPropertyResultMappings()) {
            final String nestedResultMapId = resultMapping.getNestedResultMapId();
            if (nestedResultMapId != null && resultMapping.getResultSet() == null) {
                try {
                    final String columnPrefix = getColumnPrefix(parentPrefix, resultMapping);
                    final ResultMap nestedResultMap = getNestedResultMap(rsw.getResultSet(), nestedResultMapId, columnPrefix);
                    CacheKey rowKey = null;
                    Object ancestorObject = null;
                    if (ancestorColumnPrefix.containsKey(nestedResultMapId)) {
                        rowKey = createRowKey(nestedResultMap, rsw, ancestorColumnPrefix.get(nestedResultMapId));
                        ancestorObject = ancestorObjects.get(rowKey);
                    }
                    if (ancestorObject != null) {
                        if (newObject) {
                            metaObject.setValue(resultMapping.getProperty(), ancestorObject);
                        }
                    } else {
                        rowKey = createRowKey(nestedResultMap, rsw, columnPrefix);
                        final CacheKey combinedKey = combineKeys(rowKey, parentRowKey);
                        Object rowValue = nestedResultObjects.get(combinedKey);
                        boolean knownValue = (rowValue != null);
                        final Object collectionProperty = instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject);
                        if (anyNotNullColumnHasValue(resultMapping, columnPrefix, rsw.getResultSet())) {
                            rowValue = getRowValue(rsw, nestedResultMap, combinedKey, rowKey, columnPrefix, rowValue);
                            if (rowValue != null && !knownValue) {
                                if (collectionProperty != null) {
                                    final MetaObject targetMetaObject = configuration.newMetaObject(collectionProperty);
                                    targetMetaObject.add(rowValue);
                                } else {
                                    metaObject.setValue(resultMapping.getProperty(), rowValue);
                                }
                                foundValues = true;
                            }
                        }
                    }
                } catch (SQLException e) {
                    throw new ExecutorException("Error getting nested result map values for '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
                }
            }
        }
        return foundValues;
    }

    private String getColumnPrefix(String parentPrefix, ResultMapping resultMapping) {
        final StringBuilder columnPrefixBuilder = new StringBuilder();
        if (parentPrefix != null) {
            columnPrefixBuilder.append(parentPrefix);
        }
        if (resultMapping.getColumnPrefix() != null) {
            columnPrefixBuilder.append(resultMapping.getColumnPrefix());
        }
        return columnPrefixBuilder.length() == 0 ? null : columnPrefixBuilder.toString().toUpperCase(Locale.ENGLISH);
    }

    private boolean anyNotNullColumnHasValue(ResultMapping resultMapping, String columnPrefix, ResultSet rs) throws SQLException {
        Set<String> notNullColumns = resultMapping.getNotNullColumns();
        boolean anyNotNullColumnHasValue = true;
        if (notNullColumns != null && !notNullColumns.isEmpty()) {
            anyNotNullColumnHasValue = false;
            for (String column : notNullColumns) {
                rs.getObject(prependPrefix(column, columnPrefix));
                if (!rs.wasNull()) {
                    anyNotNullColumnHasValue = true;
                    break;
                }
            }
        }
        return anyNotNullColumnHasValue;
    }

    private ResultMap getNestedResultMap(ResultSet rs, String nestedResultMapId, String columnPrefix) throws SQLException {
        ResultMap nestedResultMap = configuration.getResultMap(nestedResultMapId);
        return resolveDiscriminatedResultMap(rs, nestedResultMap, columnPrefix);
    }

    //
    // UNIQUE RESULT KEY
    //

    private CacheKey createRowKey(ResultMap resultMap, ResultSetWrapper rsw, String columnPrefix) throws SQLException {
        final CacheKey cacheKey = new CacheKey();
        cacheKey.update(resultMap.getId());
        List<ResultMapping> resultMappings = getResultMappingsForRowKey(resultMap);
        if (resultMappings.size() == 0) {
            if (Map.class.isAssignableFrom(resultMap.getType())) {
                createRowKeyForMap(rsw, cacheKey);
            } else {
                createRowKeyForUnmappedProperties(resultMap, rsw, cacheKey, columnPrefix);
            }
        } else {
            createRowKeyForMappedProperties(resultMap, rsw, cacheKey, resultMappings, columnPrefix);
        }
        return cacheKey;
    }

    private CacheKey combineKeys(CacheKey rowKey, CacheKey parentRowKey) {
        if (rowKey.getUpdateCount() > 1 && parentRowKey.getUpdateCount() > 1) {
            CacheKey combinedKey;
            try {
                combinedKey = rowKey.clone();
            } catch (CloneNotSupportedException e) {
                throw new ExecutorException("Error cloning cache key.  Cause: " + e, e);
            }
            combinedKey.update(parentRowKey);
            return combinedKey;
        }
        return CacheKey.NULL_CACHE_KEY;
    }

    private List<ResultMapping> getResultMappingsForRowKey(ResultMap resultMap) {
        List<ResultMapping> resultMappings = resultMap.getIdResultMappings();
        if (resultMappings.size() == 0) {
            resultMappings = resultMap.getPropertyResultMappings();
        }
        return resultMappings;
    }

    private void createRowKeyForMappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, List<ResultMapping> resultMappings, String columnPrefix) throws SQLException {
        for (ResultMapping resultMapping : resultMappings) {
            if (resultMapping.getNestedResultMapId() != null && resultMapping.getResultSet() == null) {
                // Issue #392
                final ResultMap nestedResultMap = configuration.getResultMap(resultMapping.getNestedResultMapId());
                createRowKeyForMappedProperties(nestedResultMap, rsw, cacheKey, nestedResultMap.getConstructorResultMappings(),
                        prependPrefix(resultMapping.getColumnPrefix(), columnPrefix));
            } else if (resultMapping.getNestedQueryId() == null) {
                final String column = prependPrefix(resultMapping.getColumn(), columnPrefix);
                final TypeHandler<?> th = resultMapping.getTypeHandler();
                List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
                // Issue #114
                if (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) {
                    final Object value = th.getResult(rsw.getResultSet(), column);
                    if (value != null) {
                        cacheKey.update(column);
                        cacheKey.update(value);
                    }
                }
            }
        }
    }

    private void createRowKeyForUnmappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, String columnPrefix) throws SQLException {
        final MetaClass metaType = MetaClass.forClass(resultMap.getType());
        List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);
        for (String column : unmappedColumnNames) {
            String property = column;
            if (columnPrefix != null && !columnPrefix.isEmpty()) {
                // When columnPrefix is specified, ignore columns without the prefix.
                if (column.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
                    property = column.substring(columnPrefix.length());
                } else {
                    continue;
                }
            }
            if (metaType.findProperty(property, configuration.isMapUnderscoreToCamelCase()) != null) {
                String value = rsw.getResultSet().getString(column);
                if (value != null) {
                    cacheKey.update(column);
                    cacheKey.update(value);
                }
            }
        }
    }

    private void createRowKeyForMap(ResultSetWrapper rsw, CacheKey cacheKey) throws SQLException {
        List<String> columnNames = rsw.getColumnNames();
        for (String columnName : columnNames) {
            final String value = rsw.getResultSet().getString(columnName);
            if (value != null) {
                cacheKey.update(columnName);
                cacheKey.update(value);
            }
        }
    }

}
