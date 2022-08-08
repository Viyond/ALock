package org.apache.alock;

import org.apache.alock.domain.ALockConfig;
import org.apache.alock.exception.ALockProcessException;
import org.apache.alock.exception.OptimisticLockingException;
import org.apache.alock.processor.ALockProcessor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * 基于redis的可重入锁实现
 * @author wy
 */
public class DistributedReentrantLock implements ALock{

    private final ALockConfig lockConfig;

    private final ALockProcessor lockProcessor;

    private final AtomicReference<Node> head = new AtomicReference<>();
    private final AtomicReference<Node> tail = new AtomicReference<>();

    //通过线程持有 实现 可重入
    private final AtomicReference<Thread> exclusiveOwnerThread = new AtomicReference<>();
    private final AtomicInteger holdCnt = new AtomicInteger(0);

    private final AtomicReference<RetryLockThread> retryLockRef = new AtomicReference<>();
    private final AtomicReference<ContinueLockLeaseThread> continueLockLeaseRef = new AtomicReference<>();

    static class Node{
        final AtomicReference<Node> prev = new AtomicReference<>();
        final AtomicReference<Node> next = new AtomicReference<>();
        final Thread t;

        Node(Thread t){
            this.t = t;
        }
        Node(){
            t = null;
        }
    }

    public DistributedReentrantLock(ALockConfig lockConfig, ALockProcessor lockProcessor){
        this.lockConfig = lockConfig;
        this.lockProcessor = lockProcessor;
    }

    @Override
    public void lock() {
        if (!tryLock()){
            acquireQueueNode(addWaiter());
        }
    }

    @Override
    public boolean tryLock() {
        if (Thread.currentThread() == exclusiveOwnerThread.get()){
            holdCnt.incrementAndGet();
            return true;
        }
        boolean locked = false;
        try{
            lockProcessor.grabLock(lockConfig);
            locked = true;
        }catch (OptimisticLockingException | ALockProcessException e){
        }
        if (locked){
            exclusiveOwnerThread.set(Thread.currentThread());
            holdCnt.set(1);

            shutdownRetryThread();

            //continue lease
            startContinueLockLeaseThread();
        }

        return locked;
    }

    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        if (tryLock()){
            return true;
        }
        return acquireQueueNodeWithTimeout(addWaiter(), unit.toMillis(timeout));
    }

    @Override
    public void unlock() {
        if (exclusiveOwnerThread.get() != Thread.currentThread()){
            throw new IllegalStateException("current thread does not hold the lock.");
        }
        if (holdCnt.decrementAndGet() > 0){
            return;
        }
        try{
            lockProcessor.releaseLock(lockConfig);
        }catch (ALockProcessException | OptimisticLockingException e){
            //Lock will release after expire time
        }finally {
            exclusiveOwnerThread.compareAndSet(Thread.currentThread(), null);

            shutdownContinueLockLeaseThread();

            unparkQueueNode();
        }
    }

    /**
     * 等待当前节点就绪
     */
    private void acquireQueueNode(final Node node){
        for (;;){
            Node p = node.prev.get();
            if (p == head.get() && tryLock()){
                head.set(node);
                p.next.set(null);
                node.prev.set(null);//help gc
                break;
            }

            if (exclusiveOwnerThread.get() == null){
                startRetryThread();
            }

            LockSupport.park(this);
        }
    }

    /**
     * 指定超时时间 获取等待锁
     * @param node
     * @param timeoutInMs
     */
    private boolean acquireQueueNodeWithTimeout(final Node node, long timeoutInMs){
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutInMs);
        final long deadline = System.nanoTime() + timeoutNanos;
        for (;;){
            Node p = node.prev.get();
            if (p == head.get() && tryLock()){
                head.set(node);
                p.next.set(null);
                node.prev.set(null);//help gc
                return true;
            }

            timeoutNanos = deadline - System.nanoTime();
            if (timeoutNanos <= 0){
                return false;
            }
            if (exclusiveOwnerThread.get() == null){
                startRetryThread();
            }

            LockSupport.parkNanos(this, timeoutNanos);
        }
    }

    /**
     * 添加到等待队列
     */
    private Node addWaiter(){
        Node n = new Node(Thread.currentThread());
        Node t = tail.get();
        //Try the fast path of enq; backup to full enq on failure
        if (t != null){
            n.prev.set(t);
            if (tail.compareAndSet(t, n)){
                t.next.set(n);
                return n;
            }
        }
        enqueue(n);
        return n;
    }

    /**
     * 返回前驱节点
     */
    private Node enqueue(Node node){
        for (;;){
            Node t = tail.get();
            if (t == null){
                Node h = new Node();
                h.next.set(node);
                node.prev.set(h);
                if (head.compareAndSet(null, h)){
                    tail.set(node);
                    return h;
                }
            }else {
                node.prev.set(t);
                if (tail.compareAndSet(t, node)){
                    t.next.set(node);
                    return t;
                }
            }
        }
    }

    private void unparkQueueNode(){
        Node h = head.get();
        if (h != null && h.next.get() != null){
            LockSupport.unpark(h.next.get().t);
        }
    }

    private void startRetryThread(){
        RetryLockThread t = retryLockRef.get();
        while (t == null || t.getState() == Thread.State.TERMINATED){
            RetryLockThread nt = new RetryLockThread(lockConfig.getMillisLease() / 10,
                    lockConfig.getMillisLease() / 5);
            retryLockRef.compareAndSet(t, nt);
            t = retryLockRef.get();
        }
        if (t.startState.compareAndSet(false, true)){
            t.start();
        }
    }

    private void shutdownRetryThread(){
        RetryLockThread t = retryLockRef.get();
        if (t != null && t.isAlive()){
            t.interrupt();
        }
    }

    private void startContinueLockLeaseThread(){
        ContinueLockLeaseThread t = continueLockLeaseRef.get();
        while (t == null || t.getState() == Thread.State.TERMINATED){
            long delay = (long)(0.5 * lockConfig.getMillisLease());
            long retryInterval = (long)(0.75 * lockConfig.getMillisLease());
            ContinueLockLeaseThread nt = new ContinueLockLeaseThread(delay, retryInterval);
            continueLockLeaseRef.compareAndSet(t, nt);

            t = continueLockLeaseRef.get();
        }
        if (t.startState.compareAndSet(false, true)){
            t.start();
        }
    }

    private void shutdownContinueLockLeaseThread(){
        ContinueLockLeaseThread t = continueLockLeaseRef.get();
        if (t != null && t.isAlive()){
            t.interrupt();
        }
    }

    /**
     * 内置线程
     */
    abstract class LockThread extends Thread{
        final Object sync = new Object();
        final long delayInMs;
        final long retryIntervalInMs;
        final AtomicBoolean startState = new AtomicBoolean(false);
        private volatile boolean shouldShutdown = false;
        private volatile boolean firstRunning = true;

        LockThread(String name, long delayInMs, long retryIntervalInMs){
            setDaemon(true);
            this.delayInMs = delayInMs;
            this.retryIntervalInMs = retryIntervalInMs;
            setName(name + getId());
        }

        @Override
        public void run() {
            while (!shouldShutdown){
                try{
                    if (firstRunning && delayInMs > 0){
                        firstRunning = false;
                        synchronized (sync){
                            sync.wait(delayInMs);
                        }
                    }

                    execute();

                    synchronized (sync){
                        sync.wait(retryIntervalInMs);
                    }
                }catch (InterruptedException e){
                    shouldShutdown = true;
                }
            }

            beforeShutdown();
        }

        abstract void execute()throws InterruptedException;

        protected void beforeShutdown(){}
    }

    private class RetryLockThread extends LockThread{
        RetryLockThread(long delayInMs, long retryIntervalInMs){
            super("RetryLockThread", delayInMs, retryIntervalInMs);
        }

        @Override
        void execute() throws InterruptedException{
            if (exclusiveOwnerThread.get() != null){
                throw new InterruptedException(String.format("Lock:%s has running thread.", lockConfig.getLockTarget()));
            }
            Node h = head.get();
            if (h == null){
                throw new InterruptedException(String.format("Lock:%s has no waiting thread.", lockConfig.getLockTarget()));
            }
            boolean needRetry = false;
            try{
                needRetry = lockProcessor.isLockFree(lockConfig.getLockUniqueKey());
            }catch (ALockProcessException e){
                needRetry = true;
            }
            if (needRetry){
                unparkQueueNode();
            }
        }

        @Override
        protected void beforeShutdown() {
            retryLockRef.compareAndSet(this, null);
        }
    }

    private class ContinueLockLeaseThread extends LockThread{

        ContinueLockLeaseThread(long delayInMs, long retryIntervalInMs){
            super("ContinueLockLeaseThread", delayInMs, retryIntervalInMs);
        }

        @Override
        void execute() throws InterruptedException {
            try{
                lockProcessor.expandLockExpire(lockConfig);
            }catch (OptimisticLockingException oe){
                throw new InterruptedException(String.format("Lock:%s has released.", lockConfig.getLockTarget()));
            }catch (ALockProcessException e){
                //retry
            }
        }

        @Override
        protected void beforeShutdown() {
            continueLockLeaseRef.compareAndSet(this, null);
        }
    }
}
