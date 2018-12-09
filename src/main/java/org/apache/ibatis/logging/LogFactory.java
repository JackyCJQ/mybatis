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
package org.apache.ibatis.logging;

import java.lang.reflect.Constructor;

/**
 * 日志工厂
 */
public final class LogFactory {

    /**
     * Marker to be used by logging implementations that support markers
     */
    //给支持marker功能的logger使用(目前有slf4j, log4j2)
    public static final String MARKER = "MYBATIS";

    //具体究竟用哪个日志框架，那个框架所对应logger的构造函数，没此获取都是利用这个构造函数就行创建
    private static Constructor<? extends Log> logConstructor;

    static {
        //slf4j
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useSlf4jLogging();
            }
        });
        //common logging
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useCommonsLogging();
            }
        });
        //log4j2
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useLog4J2Logging();
            }
        });
        //log4j
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useLog4JLogging();
            }
        });
        //jdk logging
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useJdkLogging();
            }
        });
        //没有日志
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useNoLogging();
            }
        });
    }

    //单例模式，静态模式，不能被外部初始化
    private LogFactory() {
        // disable construction
    }

    //根据传入的类来构建Log，前缀应该是对应的类的名字
    public static Log getLog(Class<?> aClass) {
        return getLog(aClass.getName());
    }

    /**
     * logger 为日志实现类的全路径
     *
     * @param logger
     * @return
     */
    public static Log getLog(String logger) {
        try {
            //构造函数必须提供一个为String型，指明logger的名称，每次都需要new一个，能不能指定为一个静态的？
            return logConstructor.newInstance(new Object[]{logger});
        } catch (Throwable t) {
            throw new LogException("Error creating logger for logger " + logger + ".  Cause: " + t, t);
        }
    }

    /**
     * 日志自动发现机制，依次进行尝试，如果有用户定义的则会进行覆盖
     * @param clazz
     */
    public static synchronized void useCustomLogging(Class<? extends Log> clazz) {
        setImplementation(clazz);
    }

    public static synchronized void useSlf4jLogging() {
        setImplementation(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
    }

    public static synchronized void useCommonsLogging() {
        setImplementation(org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl.class);
    }

    public static synchronized void useLog4JLogging() {
        setImplementation(org.apache.ibatis.logging.log4j.Log4jImpl.class);
    }

    public static synchronized void useLog4J2Logging() {
        setImplementation(org.apache.ibatis.logging.log4j2.Log4j2Impl.class);
    }

    public static synchronized void useJdkLogging() {
        setImplementation(org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl.class);
    }

    //这个没用到
    public static synchronized void useStdOutLogging() {
        setImplementation(org.apache.ibatis.logging.stdout.StdOutImpl.class);
    }

    public static synchronized void useNoLogging() {
        setImplementation(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
    }

    /**
     *
     * @param runnable
     */
    private static void tryImplementation(Runnable runnable) {
        //如果已经发现了其他的日志实现，就不在查找
        if (logConstructor == null) {
            try {
                //这里调用的不是start,而是run！根本就没用多线程嘛！
                runnable.run();

            } catch (Throwable t) {
                //在这里把没有找到的异常给处理掉了
                // ignore
            }
        }
    }

    private static void setImplementation(Class<? extends Log> implClass) {
        try {
            //要有一个string类型的构造函数
            Constructor<? extends Log> candidate = implClass.getConstructor(new Class[]{String.class});
            //日志实现的具体全限定路径
            Log log = candidate.newInstance(new Object[]{LogFactory.class.getName()});
            log.debug("Logging initialized using '" + implClass + "' adapter.");
            //找到一个就不会在找其他的实现
            logConstructor = candidate;
        } catch (Throwable t) {
            throw new LogException("Error setting Log implementation.  Cause: " + t, t);
        }
    }

}
