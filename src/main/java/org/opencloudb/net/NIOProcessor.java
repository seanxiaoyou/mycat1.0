/*
 * Copyright (c) 2013, OpenCloudDB/MyCAT and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software;Designed and Developed mainly by many Chinese 
 * opensource volunteers. you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License version 2 only, as published by the
 * Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Any questions about this component can be directed to it's project Web address 
 * https://code.google.com/p/opencloudb/.
 *
 */
package org.opencloudb.net;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opencloudb.buffer.BufferPool;
import org.opencloudb.statistic.CommandCount;
import org.opencloudb.util.ExecutorUtil;
import org.opencloudb.util.NameableExecutor;

/**
 * @author mycat
 */
public final class NIOProcessor {
	private final String name;
	private final NIOReactor reactor;
	private final BufferPool bufferPool;
	// private final NameableExecutor handler;
	private final NameableExecutor executor;
	private final ConcurrentMap<Long, FrontendConnection> frontends;
	private final ConcurrentMap<Long, BackendConnection> backends;
	private final CommandCount commands;
	private long netInBytes;
	private long netOutBytes;

	public NIOProcessor(String name, int bufferPoolSize, int bufferchunk,
			int threadPoolSize) throws IOException {
		this.name = name;
		this.reactor = new NIOReactor(name);
		this.bufferPool = new BufferPool(bufferPoolSize, bufferchunk);
		this.executor = (threadPoolSize > 0) ? ExecutorUtil.create(name + "-E",
				threadPoolSize) : null;
		this.frontends = new ConcurrentHashMap<Long, FrontendConnection>();
		this.backends = new ConcurrentHashMap<Long, BackendConnection>();
		this.commands = new CommandCount();
	}

	public String getName() {
		return name;
	}

	public BufferPool getBufferPool() {
		return bufferPool;
	}

	public int getRegisterQueueSize() {
		return reactor.getRegisterQueue().size();
	}

	public int getWriteQueueSize() {
		int total = 0;
		for (FrontendConnection fron : frontends.values()) {
			total += fron.getWriteQueue().snapshotSize();
		}
		for (BackendConnection back : backends.values()) {
			total += back.getWriteQueue().snapshotSize();
		}
		return total;

	}

	// public NameableExecutor getHandler() {
	// return handler;
	// }

	public NameableExecutor getExecutor() {
		return executor;
	}

	public void startup() {
		reactor.startup();
	}

	public void postRegister(NIOConnection c) {
		reactor.postRegister(c);
	}

	public CommandCount getCommands() {
		return commands;
	}

	public long getNetInBytes() {
		return netInBytes;
	}

	public void addNetInBytes(long bytes) {
		netInBytes += bytes;
	}

	public long getNetOutBytes() {
		return netOutBytes;
	}

	public void addNetOutBytes(long bytes) {
		netOutBytes += bytes;
	}

	public long getReactCount() {
		return reactor.getReactCount();
	}

	public void addFrontend(FrontendConnection c) {
		frontends.put(c.getId(), c);
	}

	public ConcurrentMap<Long, FrontendConnection> getFrontends() {
		return frontends;
	}

	public void addBackend(BackendConnection c) {
		backends.put(c.getId(), c);
	}

	public ConcurrentMap<Long, BackendConnection> getBackends() {
		return backends;
	}

	/**
	 * 定时执行该方法，回收部分资源。
	 */
	public void checkBackendCons() {
		backendCheck();
	}

	/**
	 * 定时执行该方法，回收部分资源。
	 */
	public void checkFrontCons() {
		frontendCheck();
	}

	// 前端连接检查
	private void frontendCheck() {
		Iterator<Entry<Long, FrontendConnection>> it = frontends.entrySet()
				.iterator();
		while (it.hasNext()) {
			FrontendConnection c = it.next().getValue();

			// 删除空连接
			if (c == null) {
				it.remove();
				continue;
			}

			// 清理已关闭连接，否则空闲检查。
			if (c.isClosed()) {
				c.cleanup();
				it.remove();
			} else {
				c.idleCheck();
			}
		}
	}

	// 后端连接检查
	private void backendCheck() {
		Iterator<Entry<Long, BackendConnection>> it = backends.entrySet()
				.iterator();
		while (it.hasNext()) {
			BackendConnection c = it.next().getValue();

			// 删除空连接
			if (c == null) {
				it.remove();
				continue;
			}

			// 清理已关闭连接，否则空闲检查。
			if (c.isClosed()) {
				c.cleanup();
				it.remove();

			} else {
				c.idleCheck();
			}
		}
	}

}