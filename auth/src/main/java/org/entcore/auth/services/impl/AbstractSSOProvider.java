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

package org.entcore.auth.services.impl;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Utils;
import org.entcore.auth.services.SamlServiceProvider;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.xml.XMLObject;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

public abstract class AbstractSSOProvider implements SamlServiceProvider {

	protected boolean validConditions(Assertion assertion, Handler<Either<String, JsonObject>> handler) {
		if (Utils.validationParamsNull(handler, "invalid.assertion", assertion)) return false;

		Conditions conditions = assertion.getConditions();
		if (conditions.getNotBefore() == null || !conditions.getNotBefore().isBeforeNow() ||
				conditions.getNotOnOrAfter() == null || !conditions.getNotOnOrAfter().isAfterNow()) {
			handler.handle(new Either.Left<String, JsonObject>("invalid.conditions"));
			return false;
		}
		return true;
	}

	protected String getAttribute(Assertion assertion, String attr) {
		if (assertion.getAttributeStatements() != null) {
			for (AttributeStatement statement : assertion.getAttributeStatements()) {
				for (Attribute attribute : statement.getAttributes()) {
					if (attr.equals(attribute.getName())) {
						for (XMLObject o : attribute.getAttributeValues()) {
							if (o.getDOM() != null) {
								return o.getDOM().getTextContent();
							}
						}
					}
				}
			}
		}
		return null;
	}

	protected void executeQuery(String query, final JsonObject params, final Handler<Either<String, JsonObject>> handler) {
		executeQuery(query, params, null, handler);
	}

	protected void executeQuery(String query, final JsonObject params, final Assertion assertion,
			final Handler<Either<String, JsonObject>> handler) {
		query +="RETURN DISTINCT u.id as id, u.activationCode as activationCode, " +
				"u.login as login, u.email as email, u.mobile as mobile, u.federated";
		Neo4j.getInstance().execute(query, params, Neo4jResult.validUniqueResultHandler(
				new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(final Either<String, JsonObject> event) {
				if (event.isRight() && event.right().getValue().getBoolean("federated") == null &&
						event.right().getValue().getString("id") != null) {
					String query = "MATCH (u:User {id: {id}}) SET u.federated = true ";
					JsonObject params = new JsonObject().putString("id", event.right().getValue().getString("id"));
					if (assertion != null && assertion.getIssuer() != null &&
							assertion.getIssuer().getValue() != null && !assertion.getIssuer().getValue().trim().isEmpty()) {
						query += ", u.federatedIDP = {idp} ";
						params.putString("idp", assertion.getIssuer().getValue());
					}
					Neo4j.getInstance().execute(query, params, new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event2) {
							handler.handle(event);
						}
					});
				} else {
					handler.handle(event);
				}
			}
		}));
	}

}
