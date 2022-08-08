package org.apache.alock;

import java.util.concurrent.TimeUnit;

/**
 * 锁接口定义，待完善
 * @author wy
 */
public interface ALock {

    void lock();

    boolean tryLock();

    boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException;

    void unlock();
}
