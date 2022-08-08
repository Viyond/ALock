package org.apache.alock.domain;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import org.apache.alock.utils.NetUtils;
import org.apache.commons.lang.StringUtils;

/**
 * 锁相关参数定义
 * @author wy
 */
public class ALockConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String ALOCK_PREFIX = "ALOCK";

    public static final String ALOCK_SEPRATOR = "_";

    private String lockType = "DEFAULT";

    private String lockTarget;

    private String lockUniqueKey;

    private int leaseTime;

    private TimeUnit leaseTimeUnit;

    private final String lockValue;

    public ALockConfig(String lockType, String lockTarget, int leaseTime, TimeUnit leaseTimeUnit){
        this.lockType = lockType;
        this.lockTarget = lockTarget;
        this.leaseTime = leaseTime;
        this.leaseTimeUnit = leaseTimeUnit;
        this.lockUniqueKey = ALOCK_PREFIX + ALOCK_SEPRATOR + lockType + ALOCK_SEPRATOR + StringUtils.trim(lockTarget);
        this.lockValue = generateLockValue();
    }

    public ALockConfig(String lockTarget, int leaseTime, TimeUnit leaseTimeUnit){
        this.lockTarget = lockTarget;
        this.leaseTime = leaseTime;
        this.leaseTimeUnit = leaseTimeUnit;
        this.lockUniqueKey = ALOCK_PREFIX + ALOCK_SEPRATOR + lockType + ALOCK_SEPRATOR + StringUtils.trim(lockTarget);
        this.lockValue = generateLockValue();
    }

    public ALockConfig(Builder builder){
        this.lockType = builder.lockType;
        this.lockTarget = builder.lockTarget;
        this.lockUniqueKey = ALOCK_PREFIX + ALOCK_SEPRATOR + lockType + ALOCK_SEPRATOR + StringUtils.trim(lockTarget);;
        this.leaseTime = builder.leaseTime;
        this.leaseTimeUnit = builder.leaseTimeUnit;
        this.lockValue = generateLockValue();
    }

    public static Builder builder(){
        return new Builder();
    }

    public String getLockType() {
        return lockType;
    }

    public String getLockTarget() {
        return lockTarget;
    }

    public String getLockUniqueKey() {
        return lockUniqueKey;
    }

    public int getLeaseTime() {
        return leaseTime;
    }

    public TimeUnit getLeaseTimeUnit() {
        return leaseTimeUnit;
    }

    /**
     * Get the lease of millis unit
     */
    public long getMillisLease() {
        return leaseTimeUnit.toMillis(leaseTime);
    }

    public String getLockValue() {
        return lockValue;
    }

    private String generateLockValue(){
        return NetUtils.getLocalAddress() + "-" + Thread.currentThread().getId();
    }

    public static class Builder{
        private String lockType = "DEFAULT";
        private String lockTarget;
        private int leaseTime;
        private TimeUnit leaseTimeUnit;

        private Builder(){}

        public Builder lockType(String lockType){
            this.lockType = lockType;
            return this;
        }

        public Builder lockTarget(String lockTarget){
            this.lockTarget = lockTarget;
            return this;
        }

        public Builder leaseTime(int leaseTime){
            this.leaseTime = leaseTime;
            return this;
        }

        public Builder leaseTimeUnit(TimeUnit leaseTimeUnit){
            this.leaseTimeUnit = leaseTimeUnit;
            return this;
        }

        public ALockConfig build(){
            return new ALockConfig(this);
        }
    }
}
