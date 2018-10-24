package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;


public class BlockingCache implements Cache {
    //设置阻塞时等待的时间
    private long timeout;
    //被装饰的缓存
    private final Cache delegate;
    //为了保证多线程访问安全 所以用了线程安全的map容器
    private final ConcurrentHashMap<Object, ReentrantLock> locks;

    public BlockingCache(Cache delegate) {
        this.delegate = delegate;
        this.locks = new ConcurrentHashMap<Object, ReentrantLock>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    /**
     * 放入的时候可以没有限制，只是取的时候不存在会阻塞
     */
    @Override
    public void putObject(Object key, Object value) {
        try {
            delegate.putObject(key, value);
        } finally {
            //每个key对应锁，减小锁的粒度
            releaseLock(key);
        }
    }

    @Override
    public Object getObject(Object key) {
        //先获取锁，如果获取不到说明不存在缓存
        acquireLock(key);
        Object value = delegate.getObject(key);
        //取到缓存时 就释放锁
        if (value != null) {
            releaseLock(key);
        }
        //如果确实取到为null会不会永久不会释放锁了？
        return value;
    }

    @Override
    public Object removeObject(Object key) {
        return delegate.removeObject(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return null;
    }

    private ReentrantLock getLockForKey(Object key) {
        ReentrantLock lock = new ReentrantLock();
        //如果先前已经放进去了 就返回以前的lock
        ReentrantLock previous = locks.putIfAbsent(key, lock);
        return previous == null ? lock : previous;
    }

    /**
     * 获取锁
     *
     * @param key
     */
    private void acquireLock(Object key) {
        Lock lock = getLockForKey(key);
        if (timeout > 0) {
            try {
                boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    throw new CacheException("Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
                }
            } catch (InterruptedException e) {
                throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
            }
        } else {
            lock.lock();
        }
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void releaseLock(Object key) {
        ReentrantLock lock = locks.get(key);
        //如果锁被当前线程持有 则释放锁
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}