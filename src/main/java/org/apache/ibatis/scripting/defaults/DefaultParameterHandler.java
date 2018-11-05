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
package org.apache.ibatis.scripting.defaults;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 默认参数处理器，在预编译sql语句之后，进行参数的填充
 */
public class DefaultParameterHandler implements ParameterHandler {

    //类型处理注册器
    private final TypeHandlerRegistry typeHandlerRegistry;

    private final MappedStatement mappedStatement;
    //参数
    private final Object parameterObject;
    //对应的sql
    private BoundSql boundSql;
    //全局的配置文件
    private Configuration configuration;

    public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        this.mappedStatement = mappedStatement;
        this.configuration = mappedStatement.getConfiguration();
        this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
        this.parameterObject = parameterObject;
        this.boundSql = boundSql;
    }

    @Override
    public Object getParameterObject() {
        return parameterObject;
    }

    //设置参数
    @Override
    public void setParameters(PreparedStatement ps) throws SQLException {
        //设置一个上下文 用来跟踪设置参数的过程 会不会出错
        ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
        //得到参数映射
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

        if (parameterMappings != null) {
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                //如果是in则为查询过程，需要设置参数
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    Object value;
                    //获取属性值
                    String propertyName = parameterMapping.getProperty();
                    //设置额外参数
                    if (boundSql.hasAdditionalParameter(propertyName)) {
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) {
                        //若参数为null，直接设null
                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                        //若参数有相应的TypeHandler，直接设object
                        value = parameterObject;
                    } else {
                        //除此以外，MetaObject.getValue反射取得值设进去
                        MetaObject metaObject = configuration.newMetaObject(parameterObject);
                        value = metaObject.getValue(propertyName);
                    }
                    TypeHandler typeHandler = parameterMapping.getTypeHandler();
                    JdbcType jdbcType = parameterMapping.getJdbcType();
                    if (value == null && jdbcType == null) {
                        //不管是数据为null,还是jdbc为Null,设置的时候需要数据库自适应
                        jdbcType = configuration.getJdbcTypeForNull();
                    }
                    //最终设置参数
                    typeHandler.setParameter(ps, i + 1, value, jdbcType);
                }
            }
        }
    }

}
