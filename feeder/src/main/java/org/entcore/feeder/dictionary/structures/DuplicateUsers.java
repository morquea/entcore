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

package org.entcore.feeder.dictionary.structures;

import org.entcore.feeder.ManualFeeder;
import org.entcore.feeder.exceptions.TransactionException;
import org.entcore.feeder.utils.TransactionHelper;
import org.entcore.feeder.utils.TransactionManager;
import org.entcore.feeder.utils.Validator;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateUsers {

	private static final Logger log = LoggerFactory.getLogger(DuplicateUsers.class);
	private final JsonArray searchSources;
	private final List<String> notDeduplicateSource = Arrays.asList("AAF");
	private final Map<String, Integer> sourcePriority = new HashMap<>();

	public DuplicateUsers(JsonArray searchSources) {
		this(searchSources, null);
	}

	public DuplicateUsers(JsonArray searchSources, JsonArray sourcesPriority) {
		this.searchSources = (searchSources != null) ? searchSources : new JsonArray().add(ManualFeeder.SOURCE).add("CSV");
		if (sourcesPriority == null) {
			sourcesPriority = new JsonArray().add("AAF").add("BE1D").add("CSV").add("MANUAL");
		}
		final int size = sourcesPriority.size();
		for (int i = 0; i < size; i++) {
			sourcePriority.put(sourcesPriority.<String>get(i), size - i);
		}
	}

	public void markDuplicates(Handler<JsonObject> handler) {
		markDuplicates(null, handler);
	}

	public void markDuplicates(final Message<JsonObject> message) {
		markDuplicates(message, null);
	}

	public void markDuplicates(final Message<JsonObject> message, final Handler<JsonObject> handler) {
		final String[] profiles = ManualFeeder.profiles.keySet().toArray(new String[ManualFeeder.profiles.keySet().size()]);
		final VoidHandler[] handlers = new VoidHandler[profiles.length + 1];
		final long start = System.currentTimeMillis();
		handlers[handlers.length - 1] = new VoidHandler() {
			@Override
			protected void handle() {
				log.info("Mark duplicates users finished - elapsed time " + (System.currentTimeMillis() - start) + " ms.");
				if (message != null) {
					message.reply(new JsonObject().putString("status", "ok"));
				}
				if (handler != null) {
					handler.handle(new JsonObject().putString("status", "ok"));
				}
			}
		};
		for (int i = profiles.length - 1; i >= 0; i--) {
			final int j = i;
			handlers[i] = new VoidHandler() {
				@Override
				protected void handle() {
					searchDuplicatesByProfile(profiles[j], handlers[j + 1]);
				}
			};
		}
		handlers[0].handle(null);
	}

	public void ignoreDuplicate(final Message<JsonObject> message) {
		String userId1 = message.body().getString("userId1");
		String userId2 = message.body().getString("userId2");
		if (userId1 == null || userId2 == null || userId1.trim().isEmpty() || userId2.trim().isEmpty()) {
			message.reply(new JsonObject().putString("status", "error").putString("message", "invalid.id"));
			return;
		}
		String query =
				"MATCH (u1:User {id: {userId1}})-[r:DUPLICATE]-(u2:User {id: {userId2}}) " +
				"SET u1.ignoreDuplicates = coalesce(u1.ignoreDuplicates, []) + u2.id, " +
				"u2.ignoreDuplicates = coalesce(u2.ignoreDuplicates, []) + u1.id " +
				"DELETE r";
		JsonObject params = new JsonObject().putString("userId1", userId1).putString("userId2", userId2);
		TransactionManager.getNeo4jHelper().execute(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				message.reply(event.body());
			}
		});
	}

	public void listDuplicates(final Message<JsonObject> message) {
		JsonArray structures = message.body().getArray("structures");
		boolean inherit = message.body().getBoolean("inherit");

		String query;
		if (structures != null && structures.size() > 0) {
			if (inherit) {
				query = "MATCH (s:Structure)<-[:HAS_ATTACHMENT*0..]-(so:Structure)<-[:DEPENDS]-(pg:ProfileGroup) ";
			} else {
				query = "MATCH (s:Structure)<-[:DEPENDS]-(pg:ProfileGroup) ";
			}
			query +="WHERE s.id IN {structures} " +
					"WITH COLLECT(pg.id) as groupIds " +
					"MATCH (g1:ProfileGroup)<-[:IN]-(u1:User)-[r:DUPLICATE]->(u2:User)-[:IN]->(g2:ProfileGroup) " +
					"WHERE g1.id IN groupIds AND g2.id IN groupIds ";
		} else {
			query = "MATCH (u1:User)-[r:DUPLICATE]->(u2:User) ";
		}
		query +="RETURN r.score as score, " +
				"{id: u1.id, firstName: u1.firstName, lastName: u1.lastName, birthDate: u1.birthDate, email: u1.email, profiles: u1.profiles} as user1, " +
				"{id: u2.id, firstName: u2.firstName, lastName: u2.lastName, birthDate: u2.birthDate, email: u2.email, profiles: u2.profiles} as user2 " +
				"ORDER BY score DESC";
		JsonObject params = new JsonObject().putArray("structures", structures);
		TransactionManager.getNeo4jHelper().execute(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				message.reply(event.body());
			}
		});
	}

	public void mergeDuplicate(final Message<JsonObject> message) {
		String userId1 = message.body().getString("userId1");
		String userId2 = message.body().getString("userId2");
		if (userId1 == null || userId2 == null || userId1.trim().isEmpty() || userId2.trim().isEmpty()) {
			message.reply(new JsonObject().putString("status", "error").putString("message", "invalid.id"));
			return;
		}
		String query =
				"MATCH (u1:User {id: {userId1}})-[r:DUPLICATE]-(u2:User {id: {userId2}}) " +
				"RETURN DISTINCT u1.id as userId1, u1.source as source1, NOT(HAS(u1.activationCode)) as activatedU1, " +
				"u2.id as userId2, u2.source as source2, NOT(HAS(u2.activationCode)) as activatedU2";
		JsonObject params = new JsonObject().putString("userId1", userId1).putString("userId2", userId2);
		TransactionManager.getNeo4jHelper().execute(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonArray res = event.body().getArray("result");
				JsonObject error = new JsonObject().putString("status", "error");
				if ("ok".equals(event.body().getString("status")) && res != null && res.size() == 1) {
					JsonObject r = res.get(0);
					if (r.getBoolean("activatedU1", true) && r.getBoolean("activatedU2", true)) {
						message.reply(error.putString("message", "two.users.activated"));
					} else {
						mergeDuplicate(r, message);
					}
				} else if ("ok".equals(event.body().getString("status"))) {
					message.reply(error.putString("message", "not.found.duplicate"));
				} else {
					message.reply(event.body());
				}
			}
		});
	}

	private void mergeDuplicate(JsonObject r, final Message<JsonObject> message) {
		final String source1 = r.getString("source1");
		final String source2 = r.getString("source2");
		final boolean activatedU1 = r.getBoolean("activatedU1", false);
		final boolean activatedU2 = r.getBoolean("activatedU2", false);
		final String userId1 = r.getString("userId1");
		final String userId2 = r.getString("userId2");
		final JsonObject error = new JsonObject().putString("status", "error");
		if (source1 != null && source1.equals(source2) && notDeduplicateSource.contains(source1)) {
			message.reply(error.putString("message", "two.users.in.same.source"));
			return;
		}
		String query;
		JsonObject params = new JsonObject();
		if ((activatedU1 && prioritySource(source1) >= prioritySource(source2)) ||
				(activatedU2 && prioritySource(source1) <= prioritySource(source2)) ||
				(!activatedU1 && !activatedU2)) {
			query = "MATCH (u1:User {id: {userId1}})-[r:DUPLICATE]-(u2:User {id: {userId2}})-[r2]-() " +
					"SET u1.ignoreDuplicates = FILTER(uId IN u1.ignoreDuplicates WHERE uId <> {userId2}) " +
					"DELETE r, r2, u2";
			if (activatedU1) {
				params.putString("userId1", userId1).putString("userId2", userId2);
			} else if (activatedU2) {
				params.putString("userId1", userId2).putString("userId2", userId1);
			} else {
				if (prioritySource(source1) > prioritySource(source2)) {
					params.putString("userId1", userId1).putString("userId2", userId2);
				} else {
					params.putString("userId1", userId2).putString("userId2", userId1);
				}
			}
		} else if ((activatedU1 && prioritySource(source1) < prioritySource(source2)) ||
				(activatedU2 && prioritySource(source1) > prioritySource(source2))) {
			query = "MATCH (u1:User {id: {userId1}})-[r:DUPLICATE]-(u2:User {id: {userId2}})-[r2]-() " +
					"WITH u1, u2, r, r2, u2.source as source, u2.externalId as externalId " +
					"DELETE r, r2, u2 " +
					"WITH u1, source, externalId " +
					"SET u1.ignoreDuplicates = FILTER(uId IN u1.ignoreDuplicates WHERE uId <> {userId2}), " +
					"u1.externalId = externalId, u1.source = source ";
			if (activatedU1) {
				params.putString("userId1", userId1).putString("userId2", userId2);
			} else {
				params.putString("userId1", userId2).putString("userId2", userId1);
			}
		} else {
			message.reply(error.putString("message", "invalid.merge.case"));
			return;
		}
		TransactionManager.getNeo4jHelper().execute(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				message.reply(event.body());
			}
		});
	}

	private int prioritySource(String source) {
		Integer priority = sourcePriority.get(source);
		return (priority != null) ? priority : 0;
	}

	private void searchDuplicatesByProfile(final String profile, final VoidHandler handler) {
		String query =
				"MATCH (u:User) WHERE u.source IN {searchSources} AND HEAD(u.profiles) = {profile} AND NOT(HAS(u.deleteDate)) " +
				"RETURN u.id as id, u.firstName as firstName, u.lastName as lastName, " +
						"u.birthDate as birthDate, u.email as email";
		JsonObject params = new JsonObject().putString("profile", profile).putArray("searchSources", searchSources);
		TransactionManager.getNeo4jHelper().execute(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonArray result = event.body().getArray("result");
				if ("ok".equals(event.body().getString("status")) && result != null && result.size() > 0) {
					scoreDuplicates(profile, result, handler);
				} else {
					if ("ok".equals(event.body().getString("status"))) {
						log.info("No users findings for search duplicates");
					} else {
						log.error("Error finding users for search duplicates : " + event.body().getString("message"));
					}
					handler.handle(null);
				}
			}
		});
	}

	private void scoreDuplicates(final String profile, final JsonArray search, final VoidHandler handler) {
		final String query =
				"START u=node:node_auto_index({luceneQuery}) " +
				"WHERE HEAD(u.profiles) = {profile} AND u.id <> {id} AND NOT(HAS(u.deleteDate)) " +
				"RETURN u.id as id, u.firstName as firstName, u.lastName as lastName, " +
						"u.birthDate as birthDate, u.email as email";
		final JsonObject params = new JsonObject().putString("profile", profile);
		TransactionHelper tx;
		try {
			tx = TransactionManager.getTransaction(false);
		} catch (TransactionException e) {
			log.error("Error when find duplicate users.", e);
			return;
		}
		final JsonArray result = new JsonArray();
		for (int i = 0; i < search.size(); i++) {
			final JsonObject json = search.get(i);
			final String firstNameAttr = luceneAttribute("firstName", json.getString("firstName"), 0.6);
			final String lastNameAttr = luceneAttribute("lastName", json.getString("lastName"), 0.6);
			String luceneQuery;
			if (firstNameAttr != null && lastNameAttr != null &&
					!firstNameAttr.trim().isEmpty() && !lastNameAttr.trim().isEmpty()) {
				luceneQuery = firstNameAttr + " AND " + lastNameAttr;
				result.add(json);
				tx.add(query, params.copy().putString("luceneQuery", luceneQuery).putString("id", json.getString("id")));
			}
		}
		tx.commit(new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonArray results = event.body().getArray("results");
				if ("ok".equals(event.body().getString("status")) && results != null && results.size() > 0) {
					TransactionHelper tx;
					try {
						tx = TransactionManager.getTransaction();
					} catch (TransactionException e) {
						log.error("Error when score duplicate users.", e);
						return;
					}
					for (int i = 0; i < results.size(); i++) {
						JsonArray findUsers = results.get(i);
						if (findUsers == null || findUsers.size() == 0) continue;
						JsonObject searchUser = result.get(i);
						calculateAndStoreScore(searchUser, findUsers, tx);
					}
					tx.commit(new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							if ("ok".equals(event.body().getString("status"))) {
								log.info("Mark duplicates " + profile + " finished.");
							} else {
								log.error("Error marking duplicates : " + event.body().getString("message"));
							}
							handler.handle(null);
						}
					});
				} else {
					if ("ok".equals(event.body().getString("status"))) {
						log.info("No duplicate user found in profile " + profile);
					} else {
						log.error("Error finding users for search duplicates : " + event.body().getString("message"));
					}
					handler.handle(null);
				}
			}
		});
	}

	private String luceneAttribute(String attributeName, String value, double distance) {
		if (value == null || value.trim().isEmpty() || attributeName == null || attributeName.trim().isEmpty()) {
			return "";
		}
		String d = (distance > 0.9 || distance < 0.1) ? "" : ("~" + distance);
		StringBuilder sb = new StringBuilder().append("(");
		String[] values = value.split("\\s+");
		for (String v : values) {
			if (v.startsWith("-")) {
				v = v.replaceFirst("-+", "");
			}
			v = v.replaceAll("\\W+", "");
			if (v.isEmpty() || (v.length() < 4 && values.length > 1)) continue;
			sb.append(attributeName).append(":").append(v).append(d).append(" OR ");
		}
		int len = sb.length();
		if (len == 1) {
			return "";
		}
		sb.delete(len - 4, len);
		return sb.append(")").toString();
	}

	private void calculateAndStoreScore(JsonObject searchUser, JsonArray findUsers, TransactionHelper tx) {
		String query =
				"MATCH (u:User {id : {sId}}), (d:User {id : {dId}}) " +
				"WHERE NOT({dId} IN u.ignoreDuplicates) AND NOT({sId} IN d.ignoreDuplicates) " +
				"MERGE u-[:DUPLICATE {score:{score}}]-d ";
		JsonObject params = new JsonObject().putString("sId", searchUser.getString("id"));

		final String lastName = cleanAttribute(searchUser.getString("lastName"));
		final String firstName = cleanAttribute(searchUser.getString("firstName"));
		final String birthDate = cleanAttribute(searchUser.getString("birthDate"));
		final String email = cleanAttribute(searchUser.getString("email"));

		for (int i = 0; i < findUsers.size(); i++) {
			int score = 2;
			JsonObject fu = findUsers.get(i);
			score += exactMatch(lastName, cleanAttribute(fu.getString("lastName")));
			score += exactMatch(firstName, cleanAttribute(fu.getString("firstName")));
			score += exactMatch(birthDate, cleanAttribute(fu.getString("birthDate")));
			score += exactMatch(email, cleanAttribute(fu.getString("email")));
			tx.add(query, params.copy().putString("dId", fu.getString("id")).putNumber("score", score));
		}
	}

	private int exactMatch(String attribute0, String attribute1) {
		return (attribute0 == null || attribute1 == null || !attribute0.equals(attribute1)) ? 0 : 1;
	}

	private String cleanAttribute(String attribute) {
		if (attribute == null || attribute.trim().isEmpty()) {
			return null;
		}
		return Validator.removeAccents(attribute).replaceAll("\\s+", "").toLowerCase();
	}

}
