package $04_ReadWriteLock;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lmmarise.j@gmail.com
 * @since 2022/2/24 2:16 PM
 */
public class ReadWriteLock {
    private final Map<Thread, Integer> readingThreads = new HashMap<>();        // 记录读线程的重入次数
    private int writeAccesses = 0;
    private int writeRequests = 0;
    private Thread writingThread = null;

    public synchronized void lockRead() throws InterruptedException {
        Thread currentThread = Thread.currentThread();
        while (!hasReadAccess(currentThread)) {
            wait();
        }
        readingThreads.put(currentThread, (readReentrantCount(currentThread) + 1));     // 读计数
    }

    private boolean hasReadAccess(Thread currentThread) {
        if (holdWriteLock(currentThread)) return true;  // 同一个线程读写不互斥
        if (isWriter()) return false;                   // 不同线程读写互斥
        if (holdReadLock(currentThread)) return true;   // 读可重入
        return !hasWriteRequests();                     // 避免写饥饿
    }

    public synchronized void unlockRead() {
        Thread currentThread = Thread.currentThread();
        if (!holdReadLock(currentThread)) {                             // 检查当前线程是否持有读锁
            throw new IllegalMonitorStateException("Calling Thread does not hold a read lock on this ReadWriteLock");
        }
        int accessCount = readReentrantCount(currentThread);        // 读重入次数
        if (accessCount == 1) {
            readingThreads.remove(currentThread);                   // 重入一次，可以完全释放锁
        } else {
            readingThreads.put(currentThread, (accessCount - 1));   // 读重入多次，读锁计数器计数减一
        }
        notifyAll();
    }

    public synchronized void lockWrite() throws InterruptedException {
        writeRequests++;                                // 写请求计数，避免当出现大量读线程时，写线程完全没机会，造成写线程饥饿
        Thread callingThread = Thread.currentThread();
        while (!hasWriteAccess(callingThread)) {
            wait();     // 内存可见性？
        }
        writeRequests--;
        writeAccesses++;                                // 写锁重入计数器
        writingThread = callingThread;                  // 锁住
    }

    public synchronized void unlockWrite() throws InterruptedException {
        if (!holdWriteLock(Thread.currentThread())) {
            throw new IllegalMonitorStateException("Calling Thread does not hold the write lock on this ReadWriteLock");
        }
        writeAccesses--;
        if (writeAccesses == 0) {
            writingThread = null;
        }
        notifyAll();
    }

    private boolean hasWriteAccess(Thread callingThread) {
        if (isOnlyReader(callingThread)) return true;       // 当前线程是唯一的读锁持有者，允许读升写
        if (hasReaders()) return false;                     // 读写线程互斥
        if (writingThread == null) return true;             // 当前线程时唯一写线程
        return holdWriteLock(callingThread);                     // 锁线程重入
    }

    private int readReentrantCount(Thread callingThread) {
        Integer accessCount = readingThreads.get(callingThread);
        if (accessCount == null) return 0;
        return accessCount;
    }


    private boolean hasReaders() {
        return readingThreads.size() > 0;
    }

    private boolean holdReadLock(Thread callingThread) {
        return readingThreads.get(callingThread) != null;
    }

    private boolean isOnlyReader(Thread callingThread) {
        return readingThreads.size() == 1 && readingThreads.get(callingThread) != null;
    }

    private boolean isWriter() {
        return writingThread != null;
    }

    private boolean holdWriteLock(Thread callingThread) {
        return writingThread == callingThread;
    }

    private boolean hasWriteRequests() {
        return this.writeRequests > 0;
    }
}
