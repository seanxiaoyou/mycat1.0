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
package org.opencloudb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.opencloudb.backend.PhysicalDBPool;
import org.opencloudb.cache.CacheService;
import org.opencloudb.config.model.SystemConfig;
import org.opencloudb.manager.ManagerConnectionFactory;
import org.opencloudb.net.NIOAcceptor;
import org.opencloudb.net.NIOConnector;
import org.opencloudb.net.NIOProcessor;
import org.opencloudb.route.RouteService;
import org.opencloudb.server.ServerConnectionFactory;
import org.opencloudb.statistic.SQLRecorder;
import org.opencloudb.util.ExecutorUtil;
import org.opencloudb.util.NameableExecutor;
import org.opencloudb.util.TimeUtil;

/**
 * @author mycat
 */
public class MycatServer {

	private static final Logger traceLog = Logger.getLogger("RunTrace");

	public static final String NAME = "MyCat";
	private static final long LOG_WATCH_DELAY = 60000L;
	private static final long TIME_UPDATE_PERIOD = 20L;
	private static final MycatServer INSTANCE = new MycatServer();
	private static final Logger LOGGER = Logger.getLogger(MycatServer.class);
	private final RouteService routerService;
	private final CacheService cacheService;
	private Properties dnIndexProperties;

	public static final MycatServer getInstance() {
		return INSTANCE;
	}

	private final MycatConfig config;
	private final Timer timer;
	private final NameableExecutor managerExecutor;
	private final NameableExecutor timerExecutor;
	private final SQLRecorder sqlRecorder;
	private final AtomicBoolean isOnline;
	private final long startupTime;
	private NIOProcessor[] processors;
	private NIOConnector connector;
	private NIOAcceptor manager;
	private NIOAcceptor server;

	private MycatServer() {
		this.config = new MycatConfig();
		SystemConfig system = config.getSystem();
		traceLog.info("init config");
		this.timer = new Timer(NAME + "Timer", true);
		traceLog.info("init timer");
		this.timerExecutor = ExecutorUtil.create("TimerExecutor",
				system.getTimerExecutor());
		traceLog.info("init timer timerExecutor");
		this.managerExecutor = ExecutorUtil.create("ManagerExecutor",
				system.getManagerExecutor());
		traceLog.info("init timer managerExecutor");
		this.sqlRecorder = new SQLRecorder(system.getSqlRecordCount());
		traceLog.info("init  sqlRecorder 统计排序记录器 默认是10个");
		this.isOnline = new AtomicBoolean(true);
		traceLog.info("set online is true!");
		cacheService = new CacheService();
		traceLog.info("init cache!");
		routerService = new RouteService(cacheService);
		traceLog.info("init RouteService! and set cache");
		this.startupTime = TimeUtil.currentTimeMillis();
	}

	public MycatConfig getConfig() {
		return config;
	}

	public void beforeStart() {

/*
		String home = SystemConfig.getHomePath();
		Log4jInitializer.configureAndWatch(home + "/conf/log4j.xml",
				LOG_WATCH_DELAY);
*/

	}

	public void startup() throws IOException {
		// server startup
		LOGGER.info("===============================================");
		LOGGER.info(NAME + " is ready to startup ...");

		SystemConfig system = config.getSystem();

		LOGGER.info("sysconfig params:" + system.toString());
		timer.schedule(updateTime(), 0L, TIME_UPDATE_PERIOD);
		// load datanode active index from properties
		dnIndexProperties = loadDnIndexProps();
		// startup processors
		int executor = system.getProcessorExecutor();

		// int handler = system.getProcessorHandler();

        //启动前后端 reactor 模型处理组
		processors = new NIOProcessor[system.getProcessors()];
		int processBuferPool = system.getProcessorBufferPool();
		int processBufferChunk = system.getProcessorBufferChunk();
		LOGGER.info("Startup processors ...,total processors:"
				+ processors.length + " each procssors's thread pool size:"
				+ executor
				+ "    \r\n each process allocated socket buffer pool "
				+ processBuferPool + " bytes ,buffer chunk size:"
				+ processBufferChunk
				+ "  buffer pool's capacity(buferPool/bufferChunk) is:"
				+ processBuferPool / processBufferChunk);
		for (int i = 0; i < processors.length; i++) {
			processors[i] = new NIOProcessor("Processor" + i, processBuferPool,
					processBufferChunk, executor);
			processors[i].startup();
		}


		//资源回收
		timer.schedule(processorCheck(), 0L, system.getProcessorCheckPeriod());

		// startup connector
		LOGGER.info("Startup connector ...");

		//客户端连接管理
		connector = new NIOConnector(NAME + "Connector");
		connector.setProcessors(processors);
		connector.start();

		// 初始化代理的Mysql数据库连接池 并启动心跳验证程序
		Map<String, PhysicalDBPool> dataHosts = config.getDataHosts();
		LOGGER.info("Initialize dataHost ...");
		for (PhysicalDBPool node : dataHosts.values()) {
			String index = dnIndexProperties.getProperty(node.getHostName(),
					"0");
			if (!"0".equals(index)) {
				LOGGER.info("init datahost: " + node.getHostName()
						+ "  to use datasource index:" + index);
			}
			node.init(Integer.valueOf(index));
			node.startHeartbeat();
		}
		long dataNodeIldeCheckPeriod = system.getDataNodeIdleCheckPeriod();
		timer.schedule(dataNodeConHeartBeatCheck(dataNodeIldeCheckPeriod), 0L,
				dataNodeIldeCheckPeriod);
		timer.schedule(dataNodeHeartbeat(), 0L,
				system.getDataNodeHeartbeatPeriod());

		// startup manager
		//启动管理连接 并管理 processors处理类
		ManagerConnectionFactory mf = new ManagerConnectionFactory();
		mf.setCharset(system.getCharset());
		mf.setIdleTimeout(system.getIdleTimeout());
		manager = new NIOAcceptor(NAME + "Manager", system.getManagerPort(), mf);
		manager.setProcessors(processors);
		manager.start();
		LOGGER.info(manager.getName() + " is started and listening on "
				+ manager.getPort());
		// startup server
		//服务连接管理Reactor
		ServerConnectionFactory sf = new ServerConnectionFactory();
		sf.setCharset(system.getCharset());
		sf.setIdleTimeout(system.getIdleTimeout());
		server = new NIOAcceptor(NAME + "Server", system.getServerPort(), sf);
		server.setProcessors(processors);
		server.start();
		timer.schedule(clusterHeartbeat(), 0L,system.getClusterHeartbeatPeriod());

		// server started
		LOGGER.info(server.getName() + " is started and listening on "+ server.getPort());
		LOGGER.info("===============================================");
	}

	private Properties loadDnIndexProps() {
		Properties prop = new Properties();
		File file = new File(SystemConfig.getHomePath(), "conf"
				+ File.separator + "dnindex.properties");
		if (!file.exists()) {
			return prop;
		}
		FileInputStream filein = null;
		try {
			filein = new FileInputStream(file);
			prop.load(filein);
		} catch (Exception e) {
			LOGGER.warn("load DataNodeIndex err:" + e);
		} finally {
			if (filein != null) {
				try {
					filein.close();
				} catch (IOException e) {
				}
			}
		}
		return prop;
	}

	/**
	 * save cur datanode index to properties file
	 * 
	 * @param dataNode
	 * @param curIndex
	 */
	public synchronized void saveDataHostIndex(String dataHost, int curIndex) {

		File file = new File(SystemConfig.getHomePath(), "conf"
				+ File.separator + "dnindex.properties");
		FileOutputStream fileOut = null;
		try {
			String oldIndex = dnIndexProperties.getProperty(dataHost);
			String newIndex = String.valueOf(curIndex);
			if (newIndex.equals(oldIndex)) {
				return;
			}
			dnIndexProperties.setProperty(dataHost, newIndex);
			LOGGER.info("save DataHost index  " + dataHost + " cur index "
					+ curIndex);
			fileOut = new FileOutputStream(file);
			dnIndexProperties.store(fileOut, "update");
		} catch (Exception e) {
			LOGGER.warn("saveDataNodeIndex err:" + e);
		} finally {
			if (fileOut != null) {
				try {
					fileOut.close();
				} catch (IOException e) {
				}
			}
		}

	}

	public RouteService getRouterService() {
		return routerService;
	}

	public CacheService getCacheService() {
		return cacheService;
	}

	public RouteService getRouterservice() {
		return routerService;
	}

	public NIOProcessor[] getProcessors() {
		return processors;
	}

	public NIOConnector getConnector() {
		return connector;
	}

	public NameableExecutor getManagerExecutor() {
		return managerExecutor;
	}

	public NameableExecutor getTimerExecutor() {
		return timerExecutor;
	}

	public SQLRecorder getSqlRecorder() {
		return sqlRecorder;
	}

	public long getStartupTime() {
		return startupTime;
	}

	public boolean isOnline() {
		return isOnline.get();
	}

	public void offline() {
		isOnline.set(false);
	}

	public void online() {
		isOnline.set(true);
	}

	// 系统时间定时更新任务
	private TimerTask updateTime() {
		return new TimerTask() {
			@Override
			public void run() {
				TimeUtil.update();
			}
		};
	}

	// 处理器定时检查任务
	private TimerTask processorCheck() {
		return new TimerTask() {
			@Override
			public void run() {
				timerExecutor.execute(new Runnable() {
					@Override
					public void run() {
						for (NIOProcessor p : processors) {
							p.checkBackendCons();
						}

					}
				});
				timerExecutor.execute(new Runnable() {
					@Override
					public void run() {
						for (NIOProcessor p : processors) {
							p.checkFrontCons();
						}
					}
				});
			}
		};
	}

	// 数据节点定时连接空闲超时检查任务
	private TimerTask dataNodeConHeartBeatCheck(final long heartPeriod) {
		return new TimerTask() {
			@Override
			public void run() {
				timerExecutor.execute(new Runnable() {
					@Override
					public void run() {
						Map<String, PhysicalDBPool> nodes = config
								.getDataHosts();
						for (PhysicalDBPool node : nodes.values()) {
							node.heartbeatCheck(heartPeriod);
						}
						Map<String, PhysicalDBPool> _nodes = config
								.getBackupDataHosts();
						if (_nodes != null) {
							for (PhysicalDBPool node : _nodes.values()) {
								node.heartbeatCheck(heartPeriod);
							}
						}
					}
				});
			}
		};
	}

	// 数据节点定时心跳任务
	private TimerTask dataNodeHeartbeat() {
		return new TimerTask() {
			@Override
			public void run() {
				timerExecutor.execute(new Runnable() {
					@Override
					public void run() {
						Map<String, PhysicalDBPool> nodes = config
								.getDataHosts();
						for (PhysicalDBPool node : nodes.values()) {
							node.doHeartbeat();
						}
					}
				});
			}
		};
	}

	// 集群节点定时心跳任务
	private TimerTask clusterHeartbeat() {
		return new TimerTask() {
			@Override
			public void run() {
				timerExecutor.execute(new Runnable() {
					@Override
					public void run() {
						Map<String, MycatNode> nodes = config.getCluster()
								.getNodes();
						for (MycatNode node : nodes.values()) {
							node.doHeartbeat();
						}
					}
				});
			}
		};
	}

}