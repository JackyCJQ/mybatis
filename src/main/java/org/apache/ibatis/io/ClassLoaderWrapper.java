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
package org.apache.ibatis.io;

import java.io.InputStream;
import java.net.URL;

/**
 * 封装了5个类加载器 只要能有一个加载成功即可
 * 主要是利用
 * InputStream-->CLassLoader.getResourceAsStream(String name)；
 * URL -->CLassLoader.getResource(String name) ;
 * @author Clinton Begin
 */
public class ClassLoaderWrapper {

    //用户可以设置这个默认类加载器
    ClassLoader defaultClassLoader;
    ClassLoader systemClassLoader;

    ClassLoaderWrapper() {
        try {
            //systemClassLoader-->appCLassLoader
            systemClassLoader = ClassLoader.getSystemClassLoader();
        } catch (SecurityException ignored) {
        }
    }

    /*
     * Get a resource as a URL using the current class path
     * @return the resource or null
     */
    public URL getResourceAsURL(String resource) {
        return getResourceAsURL(resource, getClassLoaders(null));
    }

    /*
     * Get a resource from the classpath, starting with a specific class loader
     * @return the stream or null
     */
    public URL getResourceAsURL(String resource, ClassLoader classLoader) {
        return getResourceAsURL(resource, getClassLoaders(classLoader));
    }

    /*
     * Get a resource from the classpath
     * @return the stream or null
     */
    public InputStream getResourceAsStream(String resource) {
        return getResourceAsStream(resource, getClassLoaders(null));
    }

    /*
     * Get a resource from the classpath, starting with a specific class loader
     * @return the stream or null
     */
    public InputStream getResourceAsStream(String resource, ClassLoader classLoader) {
        return getResourceAsStream(resource, getClassLoaders(classLoader));
    }

    /*
     * Find a class on the classpath (or die trying)
     * @throws ClassNotFoundException Duh.
     */
    public Class<?> classForName(String name) throws ClassNotFoundException {
        return classForName(name, getClassLoaders(null));
    }

    /*
     * Find a class on the classpath, starting with a specific classloader (or die trying)
     * @throws ClassNotFoundException Duh.
     */
    public Class<?> classForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
        return classForName(name, getClassLoaders(classLoader));
    }

    /*
     * Try to get a resource from a group of classloaders
     * 用5个类加载器一个个查找资源，只要其中任何一个找到，就返回
     * @return the resource or null
     */
    InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
        for (ClassLoader cl : classLoader) {
            if (null != cl) {

                // try to find the resource as passed
                //通过类加载器 进行加载
                InputStream returnValue = cl.getResourceAsStream(resource);
                // now, some class loaders want this leading "/", so we'll add it and try again if we didn't find the resource
                if (null == returnValue) {
                    returnValue = cl.getResourceAsStream("/" + resource);
                }
                if (null != returnValue) {
                    return returnValue;
                }
            }
        }
        return null;
    }

    /*
     * Get a resource as a URL using the current class path
     * 用5个类加载器一个个查找资源，只要其中任何一个找到，就返回
     * @return the resource or null
     */
    URL getResourceAsURL(String resource, ClassLoader[] classLoader) {
        URL url;
        for (ClassLoader cl : classLoader) {
            if (null != cl) {
                // look for the resource as passed in...
                url = cl.getResource(resource);
                // ...but some class loaders want this leading "/", so we'll add it
                // and try again if we didn't find the resource
                if (null == url) {
                    url = cl.getResource("/" + resource);
                }
                // "It's always in the last place I look for it!"
                // ... because only an idiot would keep looking for it after finding it, so stop looking already.
                if (null != url) {
                    return url;
                }
            }
        }
        // didn't find it anywhere.
        return null;
    }

    /*
     * Attempt to load a class from a group of classloaders
     * 用5个类加载器一个个调用Class.forName(加载类)，只要其中任何一个加载成功，就返回
     * @throws ClassNotFoundException - Remember the wisdom of Judge Smails: Well, the world needs ditch diggers, too.
     */
    Class<?> classForName(String name, ClassLoader[] classLoader) throws ClassNotFoundException {
        for (ClassLoader cl : classLoader) {
            if (null != cl) {
                try {
                    Class<?> c = Class.forName(name, true, cl);
                    if (null != c) {
                        return c;
                    }
                } catch (ClassNotFoundException e) {
                }
            }
        }
        //如果最终都没找到则 抛出异常
        throw new ClassNotFoundException("Cannot find class: " + name);
    }

    //一共5个类加载器
    ClassLoader[] getClassLoaders(ClassLoader classLoader) {
        return new ClassLoader[]{
                classLoader,
                defaultClassLoader, //用户自定义的类加载器
                Thread.currentThread().getContextClassLoader(),//当前线程的类加载器
                getClass().getClassLoader(),//加载当前类的 类加载器
                systemClassLoader};//AppClassLoader类加载器
    }
}
