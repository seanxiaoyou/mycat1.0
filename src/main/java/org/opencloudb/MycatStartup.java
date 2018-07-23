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

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;
import org.apache.log4j.helpers.LogLog;
import org.opencloudb.config.model.SystemConfig;

/**
 * @author mycat
 */
public final class MycatStartup {
	private static final String dateFormat = "yyyy-MM-dd HH:mm:ss";
	private static final Logger traceLog = Logger.getLogger("RunTrace");

	/**
	 * 启动MyCat
	 * @param args
	 */
	public static void main(String[] args) {
		try {
				String home = SystemConfig.getHomePath();
				if (home == null) {
					System.out.println(SystemConfig.SYS_HOME + "  is not set.");
					System.exit(-1);
				}

				// myCat Server init

				MycatServer server = MycatServer.getInstance();
			    traceLog.info("MycatServer() -server ="+server+",init()");

				Log4jInitializer.configureAndWatch(home + "/conf/log4j.xml",
						60000L);
				//初始化日志和配置文件路径
				server.beforeStart();
				// startup
				server.startup();
				System.out.println("MyCAT Server startup successfully. see logs in logs/mycat.log");
		} catch (Throwable e) {
				SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
				LogLog.error(sdf.format(new Date()) + " startup error", e);
				System.exit(-1);
		}
	}
}