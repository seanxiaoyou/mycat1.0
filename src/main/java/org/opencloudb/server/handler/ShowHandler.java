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
package org.opencloudb.server.handler;

import org.opencloudb.server.ServerConnection;
import org.opencloudb.server.parser.ServerParse;
import org.opencloudb.server.parser.ServerParseShow;
import org.opencloudb.server.response.ShowCobarCluster;
import org.opencloudb.server.response.ShowCobarStatus;
import org.opencloudb.server.response.ShowDatabases;
import org.opencloudb.util.StringUtil;
 
/** 
* show 语句处理
* @author mycat  
*  
*/ 

public final class ShowHandler {

    public static void handle(String stmt, ServerConnection c, int offset) {
    	
    	// 排除 “ ` ” 符号
    	stmt = StringUtil.replaceChars(stmt, "`", null);
    	
        switch (ServerParseShow.parse(stmt, offset)) {
        case ServerParseShow.DATABASES:
            ShowDatabases.response(c);
            break;
        case ServerParseShow.COBAR_STATUS:
            ShowCobarStatus.response(c);
            break;
        case ServerParseShow.COBAR_CLUSTER:
            ShowCobarCluster.response(c);
            break;
        default:
            c.execute(stmt, ServerParse.SHOW);
        }
    }

}