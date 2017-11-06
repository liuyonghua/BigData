package org.kin.framework.concurrent;

/**
 * Created by huangjianqin on 2017/10/26.
 */
public class HashPartitioner<K> implements Partitioner<K> {
    public int toPartition(K key, int numPartition) {
        return (key.hashCode() & Integer.MAX_VALUE) % numPartition;
    }
}
