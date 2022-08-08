package org.apache.alock.processor;

import org.apache.alock.domain.ALockConfig;

/**
 * 具体锁与远程存储交互逻辑
 * @author wy
 */
public interface ALockProcessor {

    /**
     * 查询当前uniqueKey对应的锁
     * @param lockKey
     * @return
     */
    String lockValue(String lockKey);

    /**
     * 抢占锁（若锁不存在的话）
     * @param config
     */
    void grabLock(ALockConfig config);

    /**
     * 延长锁失效时间
     * @param config
     */
    void expandLockExpire(ALockConfig config);

    /**
     * 释放锁
     * @param config
     */
    void releaseLock(ALockConfig config);

    /**
     * 是否 锁被释放或超时
     * @param lockKey
     * @return
     */
    boolean isLockFree(String lockKey);
}
