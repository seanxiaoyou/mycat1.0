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
package org.opencloudb.mysql.nio.handler;

import java.util.List;

import org.apache.log4j.Logger;
import org.opencloudb.backend.PhysicalConnection;

public class SimpleLogHandler implements ResponseHandler{
	private static final Logger LOGGER = Logger
			.getLogger(SimpleLogHandler.class);
	@Override
	public void connectionError(Throwable e, PhysicalConnection conn) {
		LOGGER.warn("connectionError "+e);
		
	}

	@Override
	public void connectionAcquired(PhysicalConnection conn) {
		LOGGER.info("connectionAcquired "+conn);
		
	}

	@Override
	public void errorResponse(byte[] err, PhysicalConnection conn) {
		LOGGER.warn("caught error resp: " + conn + " " + new String(err));
	}

	@Override
	public void okResponse(byte[] ok, PhysicalConnection conn) {
		LOGGER.info("okResponse: " + conn );
		
	}

	@Override
	public void fieldEofResponse(byte[] header, List<byte[]> fields,
			byte[] eof, PhysicalConnection conn) {
		LOGGER.info("fieldEofResponse: " + conn );
		
	}

	@Override
	public void rowResponse(byte[] row, PhysicalConnection conn) {
		LOGGER.info("rowResponse: " + conn );
		
	}

	@Override
	public void rowEofResponse(byte[] eof, PhysicalConnection conn) {
		LOGGER.info("rowEofResponse: " + conn );
		
	}

	@Override
	public void writeQueueAvailable() {
		
		
	}

	@Override
	public void connectionClose(PhysicalConnection conn, String reason) {
		
		
	}

}