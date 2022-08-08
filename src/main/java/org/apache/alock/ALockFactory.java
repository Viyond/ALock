package org.apache.alock;

import org.apache.alock.domain.ALockConfig;
import org.apache.alock.jedis.JedisClient;
import org.apache.alock.processor.impl.RedisLockProcessor;
import redis.clients.jedis.JedisPool;

public class ALockFactory {

    /**
     * 创建一个基于redis的分布式锁
     * @return
     */
    public static ALock createDistributedReentrantLockBasedOnRedis(ALockConfig lockConfig,
                                                                   JedisPool jedisPool){
        JedisClient jedisClient = new JedisClient(jedisPool);
        return new DistributedReentrantLock(lockConfig, new RedisLockProcessor(jedisClient));
    }
}
