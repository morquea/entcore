/* Copyright © WebServices pour l'Éducation, 2014
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
 *
 */

package org.entcore.auth;

import org.entcore.auth.controllers.AuthController;
import org.entcore.auth.controllers.SamlController;
import org.entcore.auth.security.AuthResourcesProvider;
import org.entcore.auth.security.SamlValidator;
import org.entcore.auth.services.impl.DefaultServiceProviderFactory;
import org.entcore.auth.users.DefaultUserAuthAccount;
import org.entcore.auth.users.UserAuthAccount;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.BaseServer;
import fr.wseduc.webutils.request.filter.SecurityHandler;
import fr.wseduc.webutils.request.filter.UserAuthFilter;
import fr.wseduc.webutils.security.oauth.DefaultOAuthResourceProvider;
import org.entcore.common.neo4j.Neo;
import org.opensaml.xml.ConfigurationException;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.shareddata.ConcurrentSharedMap;

public class Auth extends BaseServer {

	@Override
	public void start() {
		final EventBus eb = getEventBus(vertx);
		SecurityHandler.clearFilters();
		SecurityHandler.addFilter(new UserAuthFilter(new DefaultOAuthResourceProvider(eb)));
		setResourceProvider( new AuthResourcesProvider(new Neo(vertx, eb, container.logger())));
		super.start();

		final UserAuthAccount userAuthAccount = new DefaultUserAuthAccount(vertx, container);
		final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(Auth.class.getSimpleName());

		AuthController authController = new AuthController();
		authController.setEventStore(eventStore);
		authController.setUserAuthAccount(userAuthAccount);
		addController(authController);

		final String samlMetadataFolder = config.getString("saml-metadata-folder");
		if (samlMetadataFolder != null && !samlMetadataFolder.trim().isEmpty()) {
			vertx.fileSystem().readDir(samlMetadataFolder, new Handler<AsyncResult<String[]>>() {
				@Override
				public void handle(AsyncResult<String[]> event) {
					if (event.succeeded() && event.result().length > 0) {
						try {
							SamlController samlController = new SamlController();
							JsonObject conf = new JsonObject()
									.putString("saml-metadata-folder", samlMetadataFolder)
									.putString("saml-private-key", config.getString("saml-private-key"));
							container.deployWorkerVerticle(SamlValidator.class.getName(), conf);
							samlController.setEventStore(eventStore);
							samlController.setUserAuthAccount(userAuthAccount);
							samlController.setServiceProviderFactory(new DefaultServiceProviderFactory(
									config.getObject("saml-services-providers")));
							addController(samlController);
							ConcurrentSharedMap<Object, Object> server = vertx.sharedData().getMap("server");
							if (server != null) {
								String loginUri = config.getString("loginUri");
								String callbackParam = config.getString("callbackParam");
								if (loginUri != null && !loginUri.trim().isEmpty()) {
									server.putIfAbsent("loginUri", loginUri);
								}
								if (callbackParam != null && !callbackParam.trim().isEmpty()) {
									server.putIfAbsent("callbackParam", callbackParam);
								}
							}
						} catch (ConfigurationException e) {
							log.error("Saml loading error.", e);
						}
					}
				}
			});
		}
	}

}
