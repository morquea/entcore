/*
 * Copyright © WebServices pour l'Éducation, 2015
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.feeder.aaf1d;

import org.entcore.feeder.Feed;
import org.entcore.feeder.dictionary.structures.Importer;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

public class Aaf1dFeeder implements Feed {

	private final Vertx vertx;
	private final String path;

	public Aaf1dFeeder(Vertx vertx, String path) {
		this.vertx = vertx;
		this.path = path;
	}

	@Override
	public void launch(Importer importer, Handler<Message<JsonObject>> handler) throws Exception {
		new StructureImportProcessing1d(path,vertx).start(handler);
	}

	@Override
	public String getSource() {
		return "AAF1D";
	}

}
