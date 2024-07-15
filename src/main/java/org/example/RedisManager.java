package org.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class RedisManager {

    private static String redisHost;
    private static String redisPort;
    private static String redisEnabledFromProps = "FALSE";
    private static boolean redisEnabled = false;
    private static String redisPwd;
    public static boolean redisStatus = false;
    private static String redisSslFromProps = "FALSE";
    private static boolean redisSsl = false;

    private static JedisPoolConfig poolConfig = null;
    private static JedisPool jedisPool = null;
    private static Jedis myCurrentJedis = null;

    public static void start(Properties properties) {
        poolConfig = null;
        jedisPool = null;
        myCurrentJedis = null;
        redisStatus = false;
        redisEnabled = false;
        redisSsl = false;

        redisEnabledFromProps = properties.getProperty("mtp.cache.redis.enabled");
        redisEnabled = Boolean.valueOf(redisEnabledFromProps);
        redisPort = properties.getProperty("mtp.cache.redis.port");
        redisHost = properties.getProperty("mtp.cache.redis.host");
        redisPwd = properties.getProperty("mtp.cache.redis.password");
        redisSslFromProps = properties.getProperty("mtp.cache.redis.ssl");
        redisSsl = Boolean.valueOf(redisSslFromProps);

        startJedisInstance();
    }

    private synchronized static void startJedisInstance() {
        if (!redisStatus && redisEnabled && myCurrentJedis == null && poolConfig == null && jedisPool == null) {
            new RedisManager();
        }
    }

    private RedisManager() {
        System.out.println("Connecting to Redis with params: REDIS_HOST: " + redisHost
                + " REDIS_PORT: " + redisPort + " REDIS_SSL: " + redisSsl);

        poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(2);
        poolConfig.setMaxIdle(1);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        int timeout = 20000;

        jedisPool = new JedisPool(poolConfig, redisHost, Integer.valueOf(redisPort), timeout, redisPwd, redisSsl);

        System.out.println("Connected to Redis...OK!");

        redisStatus = true;
        myCurrentJedis = null;
    }

    public synchronized static Jedis getJedis() {
        if (redisStatus && redisEnabled && poolConfig != null && jedisPool != null) {
            if (myCurrentJedis == null) {
                return getJedisInternal();
            } else {
                String requestEcho = "iAmConnected";
                try {
                    String responseEcho = myCurrentJedis.echo(requestEcho);
                    if (responseEcho == null || !(requestEcho.equalsIgnoreCase(responseEcho))) {
                        myCurrentJedis = null;
                        return getJedisInternal();
                    }
                } catch (Exception e) {
                    myCurrentJedis = null;
                    return getJedisInternal();
                }
            }
        } else if (redisEnabled && jedisPool == null) {
            poolConfig = null;
            jedisPool = null;
            myCurrentJedis = null;
        }
        return null;
    }

    private static Jedis getJedisInternal() {
        if (redisStatus && redisEnabled) {
            boolean triesOn = true;
            int triesCount = 0;
            int maxTriesCount = 3;
            while (triesOn && triesCount < maxTriesCount) {
                try (Jedis jedis = jedisPool.getResource()) {
                    triesOn = false;
                    myCurrentJedis = jedis;
                    myCurrentJedis.auth(redisPwd);
                    return myCurrentJedis;
                } catch (JedisConnectionException e) {
                    triesCount++;
                    System.out.println("Connection exception, try to reconnect...");
                } catch (JedisException e) {
                    triesOn = false;
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static void main(String[] args) {
        Properties properties = new Properties();
        try (InputStream input = RedisManager.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return;
            }
            properties.load(input);
            RedisManager.start(properties);

            // Additional code block
            String keyToFind = "aChave";
            String valueFound = null;

            Jedis jedis = RedisManager.getJedis();
            if (jedis != null) {
                valueFound = jedis.get(keyToFind);
                System.out.println("Value found for key " + keyToFind + ": " + valueFound);
            } else {
                System.out.println("Failed to get Jedis instance.");
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
