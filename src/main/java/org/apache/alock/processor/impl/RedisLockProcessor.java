package org.apache.alock.processor.impl;

import java.util.Arrays;

import org.apache.alock.domain.ALockConfig;
import org.apache.alock.exception.OptimisticLockingException;
import org.apache.alock.exception.RedisProcessException;
import org.apache.alock.jedis.JedisClient;
import org.apache.alock.processor.ALockProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于redis的锁处理器
 * @author wy
 */
public class RedisLockProcessor implements ALockProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(RedisLockProcessor.class);

    /**
     * Redis command & result code constant
     */
    private static final String SET_ARG_NOT_EXIST = "NX";
    private static final String SET_ARG_EXPIRE = "PX";
    private static final String RES_OK = "OK";

    private static final String expandScript = "if (redis.call('get', KEYS[1]) == ARGV[1]) then "
        + "    return redis.call('pexpire', KEYS[1], ARGV[2]); "
        + "else"
        + "    return nil; "
        + "end; ";

    private static final String unlockScript = "if (redis.call('get', KEYS[1]) == ARGV[1]) then "
        + "    return redis.call('del', KEYS[1]); "
        + "else "
        + "    return nil; "
        + "end;";

    private final JedisClient jedisClient;

    public RedisLockProcessor(JedisClient jedisClient){
        this.jedisClient = jedisClient;
    }

    @Override
    public String lockValue(String lockKey) {
        try{
            return jedisClient.get(lockKey);
        }catch (Exception e){
            LOG.error("lockValue error, lockKey:" + lockKey, e);
            throw new RedisProcessException("lockValue error, lockKey:" + lockKey, e);
        }
    }

    @Override
    public void grabLock(ALockConfig config) {
        String grabResult = null;
        try{
            grabResult = jedisClient.set(config.getLockUniqueKey(), config.getLockValue(), SET_ARG_NOT_EXIST,
                SET_ARG_EXPIRE, config.getMillisLease());
        }catch (Exception e){
            LOG.error("grabLock error, key:" + config.getLockUniqueKey(), e);
            throw new RedisProcessException("grabLock error, key:" + config.getLockUniqueKey(), e);
        }
        if (!RES_OK.equals(grabResult)){
            LOG.warn("grabLock failed, key:" + config.getLockUniqueKey());
            throw new OptimisticLockingException("grabLock failed, key:" + config.getLockUniqueKey());
        }
    }

    /**
     * Extend lease for lock with lua script.
     */
    @Override
    public void expandLockExpire(ALockConfig config) {
        Object expandRes = null;
        try{
            expandRes = jedisClient.eval(expandScript, Arrays.asList(config.getLockUniqueKey()),
                Arrays.asList(config.getLockValue(), config.getMillisLease() + ""));
        }catch (Exception e){
            LOG.error("expandLockExpire error, key:" + config.getLockUniqueKey(), e);
            throw new RedisProcessException("expandLockExpire error, key:" + config.getLockUniqueKey(), e);
        }
        if (null == expandRes){
            throw new OptimisticLockingException("failed to expand redis lock expire time, key:" + config.getLockUniqueKey());
        }
    }

    @Override
    public void releaseLock(ALockConfig config) {
        Object unlockRes = null;
        try{
            unlockRes = jedisClient.eval(unlockScript, Arrays.asList(config.getLockUniqueKey()),
                Arrays.asList(config.getLockValue()));
        }catch (Exception e){
            LOG.error("releaseLock error, key:{}, value:{}", config.getLockUniqueKey(), config.getLockValue());
            throw new RedisProcessException("releaseLock error, key:" + config.getLockUniqueKey()
                +", value:" + config.getLockValue(), e);
        }
        if (null == unlockRes){
            throw new OptimisticLockingException(String.format("releaseLock failed, maybe obtained by other process, key:{}, value:{}",
                config.getLockUniqueKey(), config.getLockValue()));
        }
    }

    @Override
    public boolean isLockFree(String lockKey) {
        return null == lockValue(lockKey);
    }
}
