package org.apache.alock.jedis;

import java.util.List;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * redis
 * @author wy
 */
public class JedisClient {

    private final JedisPool jedisPool;

    public JedisClient(JedisPool jedisPool){
        this.jedisPool = jedisPool;
    }

    /**
     * String get command
     *
     * @param key
     * @return
     */
    public String get(String key) {
        try(Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    /**
     * String set command
     *
     * @param key
     * @param value
     * @param nxxx
     * @param expx
     * @param time
     * @return
     */
    public String set(String key, String value, String nxxx, String expx, long time) {
        try(Jedis jedis = jedisPool.getResource()){
            return jedis.set(key, value, nxxx, expx, time);
        }
    }

    /**
     * Eval lua script command
     *
     * @param script
     * @param keys
     * @param args
     * @return
     */
    public Object eval(String script, List<String> keys, List<String> args) {
        try(Jedis jedis = jedisPool.getResource()){
            return jedis.eval(script, keys, args);
        }
    }

    /**
     * String delete command
     *
     * @param key
     * @return
     */
    public Long del(String key) {
        try(Jedis jedis = jedisPool.getResource()){
            return jedis.del(key);
        }
    }
}
