/**
 * Copyright 2012 Hannu Kankaanpää
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 * @author Hannu Kankaanpää <hannu.kp@gmail.com>
 */
package org.kivawiki.site;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

public abstract class CacheUtils {
	private static final long LOCK_TIMEOUT_SECONDS = 60; // 1 minute timeout
	//private static final long LOCK_TIMEOUT_SECONDS = 4;
	
	public static final class LockMap {
		private Map<Object, Lock> map = new HashMap<Object, Lock>();
		
		public synchronized Lock getLock(Object key) {
			if (!map.containsKey(key)) {
				map.put(key, new ReentrantLock());
			}
			return map.get(key);
		}

		public synchronized void freeLock(Object key) {
			map.remove(key);
		}
	}

	public static interface Loader<T extends Serializable> {
		T load();
	}
	
	public static final class LoaderException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		
		public LoaderException(Throwable cause) {
			super(cause);
		}
		public LoaderException(String message) {
			super(message);
		}
	}
	
	public static final class ResultOrError<T extends Serializable> implements Serializable {
		private static final long serialVersionUID = 1L;
		private final T result;
		private final RuntimeException error;
		public ResultOrError(T result, RuntimeException error) {
			this.result = result;
			this.error = error;
		}
		public T get() {
			if (error != null) {
				throw error;
			}
			return result;
		}
	}

	public static <T extends Serializable> T getCached(Cache cache, Serializable key, Loader<T> loader) {
		return getCached(cache, key, 0, loader);
	}
	
	private static final LockMap lockMap = new LockMap();

	public static <T extends Serializable> T getCached(Cache cache, Serializable key, int errorTimeToLive, Loader<T> loader) {
		T cachedValue = getValue(cache, key);
		if (cachedValue != null) {
			//System.out.println("CACHED GET: " + key);
			return cachedValue;
		}
		
		// Locking is only used so that we don't unnecessarily try to calculate
		// the same thing twice in a row. It doesn't have to be perfect. If
		// lockMap returns us a different lock than someone else has who's calculating
		// the same thing, no problem; It just means we'll recalculate something
		// once.
		//
		// Even so, this logic should be checked carefully.
		//
		
		List<Serializable> lockKey = Arrays.asList(cache.getName(), key);
		Lock lock = lockMap.getLock(lockKey);
		boolean gotLock = false;
		try {
			gotLock = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e1) {
		}
		try {
			// Element might've been written by the one who was previously
			// holding the lock.
			cachedValue = getValue(cache, key);
			if (cachedValue != null) {
				return cachedValue;
			}
			T value;
			try {
				//System.out.println("DO GET: " + key);
				value = loader.load();
			} catch (RuntimeException e) {
				putError(cache, key, e, errorTimeToLive);
				throw e;
			}
			putResult(cache, key, value);
			return value;
		} finally {
			lockMap.freeLock(lockKey);
			if (gotLock) {
				lock.unlock();
			}
		}
	}
	
	/**
	 * Get a result value from cache.
	 * <p>
	 * Returns null if key is not cached (or cached value is invalid type).
	 * May throw a RuntimeException if an error has been cached.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Serializable> T getValue(Cache cache, Serializable key) {
		Element element = cache.get(key);
		if (element != null) {
			try {
				return ((ResultOrError<T>) element.getValue()).get();
			} catch (ClassCastException e) {
			}
		}
		return null;
	}
	
	/**
	 * Put a result value in cache.
	 */
	public static <T extends Serializable> void putResult(Cache cache, Serializable key, T value) {
		cache.put(new Element(key, new ResultOrError<T>(value, null)));
	}
	
	/**
	 * Put an error value in cache.
	 */
	public static <T extends Serializable> void putError(Cache cache, Serializable key, RuntimeException e, int errorTimeToLive) {
		if (errorTimeToLive != 0) {
			Element newElement = new Element(key, new ResultOrError<T>(null, e));
			newElement.setTimeToLive(errorTimeToLive);
			cache.put(newElement);
		}
	}

	public static String cacheName(Object ... parts) {
		StringBuilder b = new StringBuilder();
		for (Object part : parts) {
			b.append(part);
			b.append("{~///~}");
		}
		return b.toString();
	}

}
