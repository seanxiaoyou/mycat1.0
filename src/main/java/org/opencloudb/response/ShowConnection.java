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
package org.opencloudb.response;

import java.nio.ByteBuffer;

import org.opencloudb.MycatServer;
import org.opencloudb.buffer.BufferQueue;
import org.opencloudb.config.Fields;
import org.opencloudb.manager.ManagerConnection;
import org.opencloudb.mysql.PacketUtil;
import org.opencloudb.net.FrontendConnection;
import org.opencloudb.net.NIOProcessor;
import org.opencloudb.net.mysql.EOFPacket;
import org.opencloudb.net.mysql.FieldPacket;
import org.opencloudb.net.mysql.ResultSetHeaderPacket;
import org.opencloudb.net.mysql.RowDataPacket;
import org.opencloudb.server.ServerConnection;
import org.opencloudb.util.IntegerUtil;
import org.opencloudb.util.LongUtil;
import org.opencloudb.util.StringUtil;
import org.opencloudb.util.TimeUtil;

/**
 * 查看当前有效连接信息
 * 
 * @author mycat
 * @author mycat
 */
public final class ShowConnection {

	private static final int FIELD_COUNT = 14;
	private static final ResultSetHeaderPacket header = PacketUtil
			.getHeader(FIELD_COUNT);
	private static final FieldPacket[] fields = new FieldPacket[FIELD_COUNT];
	private static final EOFPacket eof = new EOFPacket();
	static {
		int i = 0;
		byte packetId = 0;
		header.packetId = ++packetId;

		fields[i] = PacketUtil.getField("PROCESSOR",
				Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("ID", Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("HOST", Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("PORT", Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("LOCAL_PORT", Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("SCHEMA", Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil
				.getField("CHARSET", Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("NET_IN", Fields.FIELD_TYPE_LONGLONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("NET_OUT", Fields.FIELD_TYPE_LONGLONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("ALIVE_TIME(S)",
				Fields.FIELD_TYPE_LONGLONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("WRITE_ATTEMPTS",
				Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("RECV_BUFFER", Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("SEND_QUEUE", Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("CHANNELS", Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		eof.packetId = ++packetId;
	}

	public static void execute(ManagerConnection c) {
		ByteBuffer buffer = c.allocate();

		// write header
		buffer = header.write(buffer, c);

		// write fields
		for (FieldPacket field : fields) {
			buffer = field.write(buffer, c);
		}

		// write eof
		buffer = eof.write(buffer, c);

		// write rows
		byte packetId = eof.packetId;
		String charset = c.getCharset();
		for (NIOProcessor p : MycatServer.getInstance().getProcessors()) {
			for (FrontendConnection fc : p.getFrontends().values()) {
				if (fc != null) {
					RowDataPacket row = getRow(fc, charset);
					row.packetId = ++packetId;
					buffer = row.write(buffer, c);
				}
			}
		}

		// write last eof
		EOFPacket lastEof = new EOFPacket();
		lastEof.packetId = ++packetId;
		buffer = lastEof.write(buffer, c);

		// write buffer
		c.write(buffer);
	}

	private static RowDataPacket getRow(FrontendConnection c, String charset) {
		RowDataPacket row = new RowDataPacket(FIELD_COUNT);
		row.add(c.getProcessor().getName().getBytes());
		row.add(LongUtil.toBytes(c.getId()));
		row.add(StringUtil.encode(c.getHost(), charset));
		row.add(IntegerUtil.toBytes(c.getPort()));
		row.add(IntegerUtil.toBytes(c.getLocalPort()));
		row.add(StringUtil.encode(c.getSchema(), charset));
		row.add(StringUtil.encode(c.getCharset(), charset));
		row.add(LongUtil.toBytes(c.getNetInBytes()));
		row.add(LongUtil.toBytes(c.getNetOutBytes()));
		row.add(LongUtil.toBytes((TimeUtil.currentTimeMillis() - c
				.getStartupTime()) / 1000L));
		row.add(IntegerUtil.toBytes(c.getWriteAttempts()));
		ByteBuffer bb = c.getReadBuffer();
		row.add(IntegerUtil.toBytes(bb == null ? 0 : bb.capacity()));
		BufferQueue bq = c.getWriteQueue();
		row.add(IntegerUtil.toBytes(bq == null ? 0 : bq.snapshotSize()));
		if (c instanceof ServerConnection) {
			ServerConnection sc = (ServerConnection) c;
			row.add(IntegerUtil.toBytes(sc.getSession2().getTargetCount()));
		} else {
			row.add(null);
		}
		return row;
	}

}