/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;

import com.intellij.openapi.util.Getter;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.*;

abstract class RefValueHashMap<K, V> implements Map<K, V> {
    private final Map<K, MyReference<K, V>> myMap;
    private final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

    @NotNull
    static IncorrectOperationException pointlessContainsKey() {
        return new IncorrectOperationException("containsKey() makes no sense for weak/soft map because GC can clear the value any moment now");
    }

    @NotNull
    static IncorrectOperationException pointlessContainsValue() {
        return new IncorrectOperationException("containsValue() makes no sense for weak/soft map because GC can clear the key any moment now");
    }

    protected interface MyReference<K, T> extends Getter<T> {
        @NotNull
        K getKey();
    }

    RefValueHashMap() {
        myMap = new THashMap<K, MyReference<K, V>>();
    }

    RefValueHashMap(@NotNull TObjectHashingStrategy<K> strategy) {
        myMap = new THashMap<K, MyReference<K, V>>(strategy);
    }

    protected abstract MyReference<K, V> createReference(@NotNull K key, V value, @NotNull ReferenceQueue<V> queue);

    private void processQueue() {
        while (true) {
            @SuppressWarnings("unchecked")
            MyReference<K, V> ref = (MyReference<K, V>) myQueue.poll();
            if (ref == null) {
                return;
            }
            K key = ref.getKey();
            if (myMap.get(key) == ref) {
                myMap.remove(key);
            }
        }
    }

    @Override
    public V get(Object key) {
        MyReference<K, V> ref = myMap.get(key);
        return SoftReference.deref(ref);
    }

    @Override
    public V put(@NotNull K key, V value) {
        processQueue();
        MyReference<K, V> reference = createReference(key, value, myQueue);
        MyReference<K, V> oldRef = myMap.put(key, reference);
        return SoftReference.deref(oldRef);
    }

    @Override
    public V remove(Object key) {
        processQueue();
        MyReference<K, V> ref = myMap.remove(key);
        return SoftReference.deref(ref);
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> t) {
        throw new RuntimeException("method not implemented");
    }

    @Override
    public void clear() {
        myMap.clear();
    }

    @Override
    public int size() {
        return myMap.size(); //?
    }

    @Override
    public boolean isEmpty() {
        return myMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        throw pointlessContainsKey();
    }

    @Override
    public boolean containsValue(Object value) {
        throw new RuntimeException("method not implemented");
    }

    @NotNull
    @Override
    public Set<K> keySet() {
        return myMap.keySet();
    }

    @NotNull
    @Override
    public Collection<V> values() {
        List<V> result = new ArrayList<V>();
        final Collection<MyReference<K, V>> refs = myMap.values();
        for (MyReference<K, V> ref : refs) {
            final V value = ref.get();
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new RuntimeException("method not implemented");
    }
}
