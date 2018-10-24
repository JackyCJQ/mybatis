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
package org.apache.ibatis.binding;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 把接口中的方法映射到相应的sql声明模块
 * 映射器方法
 */
public class MapperMethod {
    //把接口和配置文件 关联起来的一个纽带
    private final SqlCommand command;
    //方法签名
    private final MethodSignature method;

    //传递进接口   接口中的方法  以及全局配置
    public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
        this.command = new SqlCommand(config, mapperInterface, method);
        this.method = new MethodSignature(config, method);
    }

    //执行
    public Object execute(SqlSession sqlSession, Object[] args) {
        Object result;
        //可以看到执行时就是4种情况，insert|update|delete|select，分别调用SqlSession的4大类方法
        if (SqlCommandType.INSERT == command.getType()) {
            //param是一个Map 根据声明的顺序或者是@Param
            Object param = method.convertArgsToSqlCommandParam(args);
            //返回的是数字或者布尔类型 command.getName()为statementId+sqlId
            result = rowCountResult(sqlSession.insert(command.getName(), param));

        } else if (SqlCommandType.UPDATE == command.getType()) {
            Object param = method.convertArgsToSqlCommandParam(args);
            result = rowCountResult(sqlSession.update(command.getName(), param));
        } else if (SqlCommandType.DELETE == command.getType()) {
            Object param = method.convertArgsToSqlCommandParam(args);
            result = rowCountResult(sqlSession.delete(command.getName(), param));
        } else if (SqlCommandType.SELECT == command.getType()) {
            //如果查询的方法没有要求返回结果，接口上一定要求@ResultMap等能接受结果的注解
            if (method.returnsVoid() && method.hasResultHandler()) {
                //如果有结果处理器
                executeWithResultHandler(sqlSession, args);
                result = null;
            } else if (method.returnsMany()) {
                //如果结果有多条记录
                result = executeForMany(sqlSession, args);
            } else if (method.returnsMap()) {
                //如果结果是map
                result = executeForMap(sqlSession, args);
            } else {
                //否则就是一条记录
                Object param = method.convertArgsToSqlCommandParam(args);
                result = sqlSession.selectOne(command.getName(), param);
            }
        } else {
            throw new BindingException("Unknown execution method for: " + command.getName());
        }
        if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
            throw new BindingException("Mapper method '" + command.getName()
                    + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
        }
        return result;
    }

    /**
     * 更新，删除，修改 返回对应的 方法中定义的数据类型
     * @param rowCount
     * @return
     */
    private Object rowCountResult(int rowCount) {
        final Object result;
        //如果方法中定义为void，则不需要返回结果
        if (method.returnsVoid()) {
            result = null;
            //基本类型或者是包装类型
        } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
            //如果返回值是大int或小int
            result = Integer.valueOf(rowCount);
        } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
            //如果返回值是大long或小long
            result = Long.valueOf(rowCount);
        } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
            //影响行数大于0，则返回true，说明更新成功
            result = Boolean.valueOf(rowCount > 0);
        } else {
            throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
        }
        return result;
    }

    //结果处理器
    private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
        MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
        //？？？？
        if (void.class.equals(ms.getResultMaps().get(0).getType())) {
            throw new BindingException("method " + command.getName()
                    + " needs either a @ResultMap annotation, a @ResultType annotation,"
                    + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
        }
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
        } else {
            sqlSession.select(command.getName(), param, method.extractResultHandler(args));
        }
    }

    //多条记录
    private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
        List<E> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        //代入RowBounds
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.<E>selectList(command.getName(), param, rowBounds);
        } else {
            result = sqlSession.<E>selectList(command.getName(), param);
        }
        // issue #510 Collections & arrays support
        if (!method.getReturnType().isAssignableFrom(result.getClass())) {
            if (method.getReturnType().isArray()) {
                return convertToArray(result);
            } else {
                return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
            }
        }
        return result;
    }

    /**
     * 自定义的返回集合
     * @param config
     * @param list
     * @param <E>
     * @return
     */
    private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
        Object collection = config.getObjectFactory().create(method.getReturnType());
        MetaObject metaObject = config.newMetaObject(collection);
        metaObject.addAll(list);
        return collection;
    }

    @SuppressWarnings("unchecked")
    private <E> E[] convertToArray(List<E> list) {
        E[] array = (E[]) Array.newInstance(method.getReturnType().getComponentType(), list.size());
        array = list.toArray(array);
        return array;
    }

    private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
        Map<K, V> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey(), rowBounds);
        } else {
            result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey());
        }
        return result;
    }

    //如果没有相应的key，报错
    public static class ParamMap<V> extends HashMap<String, V> {

        private static final long serialVersionUID = -2212268410512043556L;

        @Override
        public V get(Object key) {
            if (!super.containsKey(key)) {
                throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
            }
            return super.get(key);
        }

    }

    /**
     * 对应sql的ID，执行类型
     *
     */
    public static class SqlCommand {
        // sql.xml中定义的meppedStatementId
        private final String name;
        //  sql执行类型  UNKNOWN, INSERT, UPDATE, DELETE, SELECT;
        private final SqlCommandType type;

        public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
            //statementName 为mapper接口的全限定名+.+方法的名字 作为全局唯一的标志
            String statementName = mapperInterface.getName() + "." + method.getName();
            //每一个方法执行的具体信息
            MappedStatement ms = null;
            //规则就是statementid为接口为mapper接口的全限定名 sql的id为接口中方法的名字 这样就把配置和接口联系起来
            //这个在解析xml.时就会生成对应的MappedStatement，此时只需要把两者关联起来
            if (configuration.hasStatement(statementName)) {
                ms = configuration.getMappedStatement(statementName);
            } else if (!mapperInterface.equals(method.getDeclaringClass().getName())) {
                //这个方法可能为Implements父接口，需要找到定义的接口
                String parentStatementName = method.getDeclaringClass().getName() + "." + method.getName();
                if (configuration.hasStatement(parentStatementName)) {
                    ms = configuration.getMappedStatement(parentStatementName);
                }
            }
            //如果没有找到，方法没有对应的sql信息，无法执行
            if (ms == null) {
                throw new BindingException("Invalid bound statement (not found): " + statementName);
            }
            //实际上应该就是 为mapper接口的全限定名+.+方法的名字 作为全局唯一的标志
            name = ms.getId();
            type = ms.getSqlCommandType();
            //sql类型要确定，才能往下具体执行
            if (type == SqlCommandType.UNKNOWN) {
                throw new BindingException("Unknown execution method for: " + name);
            }
        }

        public String getName() {
            return name;
        }

        public SqlCommandType getType() {
            return type;
        }
    }

    /**
     * 每个接口方法的签名
     */
    public static class MethodSignature {

        private final boolean returnsMany;//判断方法返回的结果是否是collection或是数组
        private final boolean returnsMap; //判断方法返回的结果是否是Map类型
        private final boolean returnsVoid;//判断方法返回的结果是否返回为void

        private final Class<?> returnType;//判断方法返回结果具体类型

        private final String mapKey; //方法上是有@MapKey注解，可以给返回的map结果指定key
        //记下resultHandler在方法声明中是第几个参数
        private final Integer resultHandlerIndex;
        //记下Rowbounds在方法声明中是第几个参数
        private final Integer rowBoundsIndex;
        //每个数字对应参数的位置 或者用@param来指定名称
        private final SortedMap<Integer, String> params;
        //判断方法签名上 是否有@Param注解
        private final boolean hasNamedParameters;

        public MethodSignature(Configuration configuration, Method method) {
            //获取返回值的类型
            this.returnType = method.getReturnType();
            //判断是否是有返回值
            this.returnsVoid = void.class.equals(this.returnType);
            //返回结果是集合或者是数组
            this.returnsMany = (configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray());
            //判断返回结果是否是map类型
            this.mapKey = getMapKey(method);
            //如果返回的不为map,则为null
            this.returnsMap = (this.mapKey != null);

            //看方法签名中是否有@Param注解
            this.hasNamedParameters = hasNamedParams(method);

            //记下RowBounds是方法参数中第几个参数
            this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
            //记下ResultHandler在方法中是第几个参数
            this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);

            this.params = Collections.unmodifiableSortedMap(getParams(method, this.hasNamedParameters));
        }

        /**
         * 把方法中的参数转换给sql中的参数
         *
         * @param args 方法运行传递的参数
         * @return
         */
        public Object convertArgsToSqlCommandParam(Object[] args) {
            //获取方法中声明的参数个数
            final int paramCount = params.size();
            //如果没参数
            if (args == null || paramCount == 0) {
                return null;
            } else if (!hasNamedParameters && paramCount == 1) {
                //如果只有一个参数 并且没有参数注解
                //注意这里不能直接取0，因为方法中定义没有参数，但是可能会有resultHandler，RowBounds
                return args[params.keySet().iterator().next().intValue()];
            } else {
                //否则，返回一个ParamMap，修改参数名，参数名就是其位置 不存在这个参数的话 就报错
                final Map<String, Object> param = new ParamMap<Object>();
                int i = 0;
                for (Map.Entry<Integer, String> entry : params.entrySet()) {
                    //entry.getValue()为数字或者是参数注解的名字
                    //就是按照顺序的声明 可能为对应的声明的顺序的数字 可能为@Param
                    param.put(entry.getValue(), args[entry.getKey().intValue()]);
                    final String genericParamName = "param" + String.valueOf(i + 1);

                    if (!param.containsKey(genericParamName)) {
                        //2.再加一个#{param1},#{param2}...参数
                        //你可以传递多个参数给一个映射器方法。如果你这样做了,
                        //默认情况下它们将会以它们在参数列表中的位置来命名,比如:#{param1},#{param2}等。
                        //如果你想改变参数的名称(只在多参数情况下) ,那么你可以在参数上使用@Param(“paramName”)注解。
                        param.put(genericParamName, args[entry.getKey()]);
                    }
                    i++;
                }
                return param;
            }
        }

        public boolean hasRowBounds() {
            return rowBoundsIndex != null;
        }

        public RowBounds extractRowBounds(Object[] args) {
            return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
        }

        public boolean hasResultHandler() {
            return resultHandlerIndex != null;
        }

        public ResultHandler extractResultHandler(Object[] args) {
            return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
        }

        public String getMapKey() {
            return mapKey;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        public boolean returnsMany() {
            return returnsMany;
        }

        public boolean returnsMap() {
            return returnsMap;
        }

        public boolean returnsVoid() {
            return returnsVoid;
        }

        /**
         * 查找特定参数在参数列表中的位置
         * @param method
         * @param paramType
         * @return
         */
        private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
            Integer index = null;
            //按照声明的顺序  依次获取参数的类型
            final Class<?>[] argTypes = method.getParameterTypes();
            for (int i = 0; i < argTypes.length; i++) {
                  //返回第一次出现的次数
                if (paramType.isAssignableFrom(argTypes[i])) {
                    if (index == null) {
                        index = i;
                    } else {
                        //相同类型的参数只能出现一次
                        throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
                    }
                }
            }
            return index;
        }

        /**
         *
         * @param method
         * @return
         */
        private String getMapKey(Method method) {
            String mapKey = null;
            //判断这个方法的返回值是否是map类型的
            if (Map.class.isAssignableFrom(method.getReturnType())) {
                //如果返回类型是map类型的，查看该method是否有@MapKey注解。
                final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
                //如果有这个注解，将这个注解的值作为map的key
                if (mapKeyAnnotation != null) {
                    mapKey = mapKeyAnnotation.value();
                }
            }
            return mapKey;
        }

        /**
         * 按照声明的顺序
         * @param method
         * @param hasNamedParameters
         * @return
         */
        private SortedMap<Integer, String> getParams(Method method, boolean hasNamedParameters) {
            //用一个TreeMap,这样就保证还是按参数的先后顺序
            final SortedMap<Integer, String> params = new TreeMap<Integer, String>();
            //按照声明的顺序呢获取方法的全部参数类型
            final Class<?>[] argTypes = method.getParameterTypes();

            for (int i = 0; i < argTypes.length; i++) {
                //不是RowBounds/ResultHandler类型的参数 这两个参数是系统添加进的
                if (!RowBounds.class.isAssignableFrom(argTypes[i]) && !ResultHandler.class.isAssignableFrom(argTypes[i])) {
                    //参数名字默认为0,1,2，这就是为什么xml里面可以用#{1}这样的写法来表示参数了
                    String paramName = String.valueOf(params.size());
                    //如果方法签名上有注释
                    if (hasNamedParameters) {
                        //还可以用注解@Param来重命名参数 如果声明了 就用声明的注解上的名字来取
                        paramName = getParamNameFromAnnotation(method, i, paramName);
                    }
                    //i 对应参数顺序 例如 [0->0，1->1，3->@param,4->3] 后面的顺序，前面的把RowBounds，ResultHandler空出来
                    params.put(i, paramName);
                }
            }
            return params;
        }

        private String getParamNameFromAnnotation(Method method, int i, String paramName) {
            //获取参数上的第i个注释
            final Object[] paramAnnos = method.getParameterAnnotations()[i];
            for (Object paramAnno : paramAnnos) {
                if (paramAnno instanceof Param) {
                    paramName = ((Param) paramAnno).value();
                }
            }
            return paramName;
        }

        /**
         * 查看参数中是否标有@Param注解
         * @param method
         * @return
         */
        private boolean hasNamedParams(Method method) {
            boolean hasNamedParams = false;
            //每个方法有多个参数，每个参数可能有多个注解
            final Object[][] paramAnnos = method.getParameterAnnotations();
            //遍历每一个参数
            for (Object[] paramAnno : paramAnnos) {
                //遍历每个参数的每个注解
                for (Object aParamAnno : paramAnno) {
                    if (aParamAnno instanceof Param) {
                        //查找@Param注解
                        hasNamedParams = true;
                        break;
                    }
                }
            }
            return hasNamedParams;
        }

    }

}
