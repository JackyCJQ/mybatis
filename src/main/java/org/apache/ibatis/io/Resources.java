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

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Properties;

/**
 * 对ClassLoaderWrapper进行了进一步封装
 * 用户只需调用Resources相关api即可
 */
public class Resources {
    //对ClassLoader的封装的引用 文件加载的实际执行者,私有变量
    private static ClassLoaderWrapper classLoaderWrapper = new ClassLoaderWrapper();
    /*
     *当把字节流转为字符流时 指定的编码格式  inputStream-->reader
     */
    private static Charset charset;

    Resources() {
    }

    /*
     * Returns the default classloader (may be null).
     *
     * @return The default classloader
     */
    public static ClassLoader getDefaultClassLoader() {
        return classLoaderWrapper.defaultClassLoader;
    }

    /*
     * Sets the default classloader
     * @param defaultClassLoader - the new default ClassLoader
     */
    public static void setDefaultClassLoader(ClassLoader defaultClassLoader) {
        classLoaderWrapper.defaultClassLoader = defaultClassLoader;
    }

    /*
     * Returns the URL of the resource on the classpath
     *  这个Resource路径是一个相对路径
     */
    public static URL getResourceURL(String resource) throws IOException {
        // issue #625
        return getResourceURL(null, resource);
    }

    /*
     * Returns the URL of the resource on the classpath
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static URL getResourceURL(ClassLoader loader, String resource) throws IOException {
        URL url = classLoaderWrapper.getResourceAsURL(resource, loader);
        if (url == null) {
            throw new IOException("Could not find resource " + resource);
        }
        return url;
    }

    /*
     * Returns a resource on the classpath as a Stream object
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static InputStream getResourceAsStream(String resource) throws IOException {
        return getResourceAsStream(null, resource);
    }

    /*
     * Returns a resource on the classpath as a Stream object
     *  例如 ： InputStream in=Resources.getResourceAsStream("mybatis-config.xml");
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static InputStream getResourceAsStream(ClassLoader loader, String resource) throws IOException {
        InputStream in = classLoaderWrapper.getResourceAsStream(resource, loader);
        if (in == null) {
            throw new IOException("Could not find resource " + resource);
        }
        return in;
    }

    /*
     * Returns a resource on the classpath as a Properties object
     *
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static Properties getResourceAsProperties(String resource) throws IOException {
        //这个为什么不直接调用getResourceAsProperties(null,resource)？
        Properties props = new Properties();
        //先以流的方式加载 然互在从流中读取 转化为properties
        InputStream in = getResourceAsStream(resource);
        props.load(in);
        in.close();
        return props;
    }

    /*
     * Returns a resource on the classpath as a Properties object
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static Properties getResourceAsProperties(ClassLoader loader, String resource) throws IOException {
        Properties props = new Properties();
        InputStream in = getResourceAsStream(loader, resource);
        props.load(in);
        in.close();
        return props;
    }

    /*
     * Returns a resource on the classpath as a Reader object
     * 例如：Reader reader= Resources.getResourceAsReader("mybatis-config.xml");
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static Reader getResourceAsReader(String resource) throws IOException {
        //为什么不直接调用呢？getResourceAsReader(null,  resource)
        Reader reader;
        if (charset == null) {
            //inputStream 转化为 Reader 采用默认字符集
            reader = new InputStreamReader(getResourceAsStream(resource));
        } else {
            reader = new InputStreamReader(getResourceAsStream(resource), charset);
        }
        return reader;
    }

    /*
     * Returns a resource on the classpath as a Reader object
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static Reader getResourceAsReader(ClassLoader loader, String resource) throws IOException {
        Reader reader;
        if (charset == null) {
            reader = new InputStreamReader(getResourceAsStream(loader, resource));
        } else {
            reader = new InputStreamReader(getResourceAsStream(loader, resource), charset);
        }
        return reader;
    }

    /*
     * Returns a resource on the classpath as a File object
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static File getResourceAsFile(String resource) throws IOException {
        //通过将给定的 file: URI 转换成一个抽象路径名来创建一个新的 File 实例
        //getResourceURL(resource).getFile()-->/D:/ideaWorkSpace/mybaitsSstudy/target/classes/mybatis-config.xml
        return new File(getResourceURL(resource).getFile());
    }

    /*
     * Returns a resource on the classpath as a File object
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static File getResourceAsFile(ClassLoader loader, String resource) throws IOException {
        return new File(getResourceURL(loader, resource).getFile());
    }

    /*
     * Gets a URL as an input stream
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static InputStream getUrlAsStream(String urlString) throws IOException {
        //把一个url格式的路径转化为流
        URL url = new URL(urlString);
        URLConnection conn = url.openConnection();
        return conn.getInputStream();
    }

    /*
     * Gets a URL as a Reader
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static Reader getUrlAsReader(String urlString) throws IOException {
        Reader reader;
        //Reader 几乎都是从 stream中转化过来的
        if (charset == null) {
            reader = new InputStreamReader(getUrlAsStream(urlString));
        } else {
            reader = new InputStreamReader(getUrlAsStream(urlString), charset);
        }
        return reader;
    }

    /*
     * Gets a URL as a Properties object
     * @throws java.io.IOException If the resource cannot be found or read
     */
    public static Properties getUrlAsProperties(String urlString) throws IOException {
        Properties props = new Properties();
        InputStream in = getUrlAsStream(urlString);
        props.load(in);
        in.close();
        return props;
    }

    /*
     * Loads a class
     * @throws ClassNotFoundException If the class cannot be found (duh!)
     */
    public static Class<?> classForName(String className) throws ClassNotFoundException {
        //反射生成
        return classLoaderWrapper.classForName(className);
    }

    //设置编码格式
    public static Charset getCharset() {
        return charset;
    }

    public static void setCharset(Charset charset) {
        Resources.charset = charset;
    }

}
