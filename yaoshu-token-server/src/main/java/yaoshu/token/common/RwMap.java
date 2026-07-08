package yaoshu.token.pojo.dto;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Map;

/**
 * 线程安全泛型 Map（读写锁）  * <p>
 * Go 使用 sync.RWMutex，Java 等价为 ReentrantReadWriteLock。
 * 内部使用 ConcurrentHashMap 作为基础存储，读写锁保护复合操作。
 */
public class RwMap<K, V> {

    private final Map<K, V> data;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public RwMap() {
        this.data = new ConcurrentHashMap<>();
    }

    public V get(K key) {
        lock.readLock().lock();
        try {
            return data.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void set(K key, V value) {
        lock.writeLock().lock();
        try {
            data.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void putAll(Map<? extends K, ? extends V> other) {
        lock.writeLock().lock();
        try {
            data.putAll(other);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            data.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<K, V> readAll() {
        lock.readLock().lock();
        try {
            return new ConcurrentHashMap<>(data);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return data.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
