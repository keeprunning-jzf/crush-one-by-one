/*******************************************************************************
 * Copyright 2014 Sergey Tarasevich
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
 *******************************************************************************/
package com.nostra13.universalimageloader.cache.disc.impl.ext;

import android.graphics.Bitmap;
import com.nostra13.universalimageloader.cache.disc.DiskCache;
import com.nostra13.universalimageloader.cache.disc.naming.FileNameGenerator;
import com.nostra13.universalimageloader.utils.IoUtils;
import com.nostra13.universalimageloader.utils.L;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Disk cache based on "Least-Recently Used" principle. Adapter pattern, adapts
 * {@link DiskLruCache DiskLruCache} to
 * {@link com.nostra13.universalimageloader.cache.disc.DiskCache DiskCache}
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @see FileNameGenerator
 * @since 1.9.2
 */
public class LruDiskCache implements DiskCache {
	/** {@value */
	public static final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32 Kb
	/** {@value */
	public static final android.graphics.Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = android.graphics.Bitmap.CompressFormat.PNG;
	/** {@value */
	public static final int DEFAULT_COMPRESS_QUALITY = 100;

	private static final String ERROR_ARG_NULL = " argument must be not null";
	private static final String ERROR_ARG_NEGATIVE = " argument must be positive number";

	protected com.nostra13.universalimageloader.cache.disc.impl.ext.DiskLruCache cache;
	private java.io.File reserveCacheDir;

	protected final FileNameGenerator fileNameGenerator;

	protected int bufferSize = DEFAULT_BUFFER_SIZE;

	protected android.graphics.Bitmap.CompressFormat compressFormat = DEFAULT_COMPRESS_FORMAT;
	protected int compressQuality = DEFAULT_COMPRESS_QUALITY;

	/**
	 * @param cacheDir          Directory for file caching
	 * @param fileNameGenerator {@linkplain com.nostra13.universalimageloader.cache.disc.naming.FileNameGenerator
	 *                          Name generator} for cached files. Generated names must match the regex
	 *                          <strong>[a-z0-9_-]{1,64}</strong>
	 * @param cacheMaxSize      Max cache size in bytes. <b>0</b> means cache size is unlimited.
	 * @throws IOException if cache can't be initialized (e.g. "No space left on device")
	 */
	public LruDiskCache(java.io.File cacheDir, FileNameGenerator fileNameGenerator, long cacheMaxSize) throws java.io.IOException {
		this(cacheDir, null, fileNameGenerator, cacheMaxSize, 0);
	}

	/**
	 * @param cacheDir          Directory for file caching
	 * @param reserveCacheDir   null-ok; Reserve directory for file caching. It's used when the primary directory isn't available.
	 * @param fileNameGenerator {@linkplain com.nostra13.universalimageloader.cache.disc.naming.FileNameGenerator
	 *                          Name generator} for cached files. Generated names must match the regex
	 *                          <strong>[a-z0-9_-]{1,64}</strong>
	 * @param cacheMaxSize      Max cache size in bytes. <b>0</b> means cache size is unlimited.
	 * @param cacheMaxFileCount Max file count in cache. <b>0</b> means file count is unlimited.
	 * @throws IOException if cache can't be initialized (e.g. "No space left on device")
	 */
	public LruDiskCache(java.io.File cacheDir, java.io.File reserveCacheDir, FileNameGenerator fileNameGenerator, long cacheMaxSize,
                        int cacheMaxFileCount) throws java.io.IOException {
		if (cacheDir == null) {
			throw new IllegalArgumentException("cacheDir" + ERROR_ARG_NULL);
		}
		if (cacheMaxSize < 0) {
			throw new IllegalArgumentException("cacheMaxSize" + ERROR_ARG_NEGATIVE);
		}
		if (cacheMaxFileCount < 0) {
			throw new IllegalArgumentException("cacheMaxFileCount" + ERROR_ARG_NEGATIVE);
		}
		if (fileNameGenerator == null) {
			throw new IllegalArgumentException("fileNameGenerator" + ERROR_ARG_NULL);
		}

		if (cacheMaxSize == 0) {
			cacheMaxSize = Long.MAX_VALUE;
		}
		if (cacheMaxFileCount == 0) {
			cacheMaxFileCount = Integer.MAX_VALUE;
		}

		this.reserveCacheDir = reserveCacheDir;
		this.fileNameGenerator = fileNameGenerator;
		initCache(cacheDir, reserveCacheDir, cacheMaxSize, cacheMaxFileCount);
	}

	private void initCache(java.io.File cacheDir, java.io.File reserveCacheDir, long cacheMaxSize, int cacheMaxFileCount)
			throws java.io.IOException {
		try {
			cache = com.nostra13.universalimageloader.cache.disc.impl.ext.DiskLruCache.open(cacheDir, 1, 1, cacheMaxSize, cacheMaxFileCount);
		} catch (java.io.IOException e) {
			com.nostra13.universalimageloader.utils.L.e(e);
			if (reserveCacheDir != null) {
				initCache(reserveCacheDir, null, cacheMaxSize, cacheMaxFileCount);
			}
			if (cache == null) {
				throw e; //new RuntimeException("Can't initialize disk cache", e);
			}
		}
	}

	@Override
	public java.io.File getDirectory() {
		return cache.getDirectory();
	}

	@Override
	public java.io.File get(String imageUri) {
		com.nostra13.universalimageloader.cache.disc.impl.ext.DiskLruCache.Snapshot snapshot = null;
		try {
			snapshot = cache.get(getKey(imageUri));
			return snapshot == null ? null : snapshot.getFile(0);
		} catch (java.io.IOException e) {
			com.nostra13.universalimageloader.utils.L.e(e);
			return null;
		} finally {
			if (snapshot != null) {
				snapshot.close();
			}
		}
	}

	@Override
	public boolean save(String imageUri, java.io.InputStream imageStream, com.nostra13.universalimageloader.utils.IoUtils.CopyListener listener) throws java.io.IOException {
		com.nostra13.universalimageloader.cache.disc.impl.ext.DiskLruCache.Editor editor = cache.edit(getKey(imageUri));
		if (editor == null) {
			return false;
		}

		java.io.OutputStream os = new java.io.BufferedOutputStream(editor.newOutputStream(0), bufferSize);
		boolean copied = false;
		try {
			copied = com.nostra13.universalimageloader.utils.IoUtils.copyStream(imageStream, os, listener, bufferSize);
		} finally {
			com.nostra13.universalimageloader.utils.IoUtils.closeSilently(os);
			if (copied) {
				editor.commit();
			} else {
				editor.abort();
			}
		}
		return copied;
	}

	@Override
	public boolean save(String imageUri, android.graphics.Bitmap bitmap) throws java.io.IOException {
		com.nostra13.universalimageloader.cache.disc.impl.ext.DiskLruCache.Editor editor = cache.edit(getKey(imageUri));
		if (editor == null) {
			return false;
		}

		java.io.OutputStream os = new java.io.BufferedOutputStream(editor.newOutputStream(0), bufferSize);
		boolean savedSuccessfully = false;
		try {
			savedSuccessfully = bitmap.compress(compressFormat, compressQuality, os);
		} finally {
			com.nostra13.universalimageloader.utils.IoUtils.closeSilently(os);
		}
		if (savedSuccessfully) {
			editor.commit();
		} else {
			editor.abort();
		}
		return savedSuccessfully;
	}

	@Override
	public boolean remove(String imageUri) {
		try {
			return cache.remove(getKey(imageUri));
		} catch (java.io.IOException e) {
			com.nostra13.universalimageloader.utils.L.e(e);
			return false;
		}
	}

	@Override
	public void close() {
		try {
			cache.close();
		} catch (java.io.IOException e) {
			com.nostra13.universalimageloader.utils.L.e(e);
		}
		cache = null;
	}

	@Override
	public void clear() {
		try {
			cache.delete();
		} catch (java.io.IOException e) {
			com.nostra13.universalimageloader.utils.L.e(e);
		}
		try {
			initCache(cache.getDirectory(), reserveCacheDir, cache.getMaxSize(), cache.getMaxFileCount());
		} catch (java.io.IOException e) {
			com.nostra13.universalimageloader.utils.L.e(e);
		}
	}

	private String getKey(String imageUri) {
		return fileNameGenerator.generate(imageUri);
	}

	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	public void setCompressFormat(android.graphics.Bitmap.CompressFormat compressFormat) {
		this.compressFormat = compressFormat;
	}

	public void setCompressQuality(int compressQuality) {
		this.compressQuality = compressQuality;
	}
}
