package edu.one.core.blog.services.impl;

import com.mongodb.QueryBuilder;
import edu.one.core.blog.services.BlogService;
import edu.one.core.infra.*;
import edu.one.core.infra.security.resources.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.Collections;

public class DefaultBlogService implements BlogService{

	private static final String BLOG_COLLECTION = "blogs";

	private final MongoDb mongo;

	public DefaultBlogService(MongoDb mongo) {
		this.mongo = mongo;
	}

	@Override
	public void create(JsonObject blog, UserInfos author, final Handler<Either<String, JsonObject>> result) {
		JsonObject now = MongoDb.now();
		JsonObject owner = new JsonObject()
				.putString("userId", author.getUserId())
				.putString("username", author.getUsername())
				.putString("login", author.getLogin());
		JsonObject manager = new JsonObject()
				.putString("userId", author.getUserId())
				.putBoolean("manager", true);
		blog.putObject("created", now)
				.putObject("modified", now)
				.putObject("author", owner)
				.putArray("shared", new JsonArray().addObject(manager));
		JsonObject b = Utils.validAndGet(blog, FIELDS, FIELDS);
		if (validationError(result, b)) return;
		mongo.save(BLOG_COLLECTION, b, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				result.handle(Utils.validResult(res));
			}
		});
	}

	@Override
	public void update(String blogId, JsonObject blog, final Handler<Either<String, JsonObject>> result) {
		blog.putObject("modified", MongoDb.now());
		JsonObject b = Utils.validAndGet(blog, UPDATABLE_FIELDS, Collections.<String>emptyList());
		if (validationError(result, b)) return;
		QueryBuilder query = QueryBuilder.start("_id").is(blogId);
		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		for (String attr: b.getFieldNames()) {
			modifier.set(attr, b.getValue(attr));
		}
		mongo.update(BLOG_COLLECTION, MongoQueryBuilder.build(query), modifier.build(),
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				result.handle(Utils.validResult(event));
			}
		});
	}

	@Override
	public void delete(final String blogId, final Handler<Either<String, JsonObject>> result) {
		QueryBuilder q = QueryBuilder.start("blog.$id").is(blogId);
		mongo.delete("posts", MongoQueryBuilder.build(q), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				if ("ok".equals(res.body().getString("status"))) {
					QueryBuilder query = QueryBuilder.start("_id").is(blogId);
					mongo.delete(BLOG_COLLECTION, MongoQueryBuilder.build(query),
							new Handler<Message<JsonObject>>() {
								@Override
								public void handle(Message<JsonObject> event) {
									result.handle(Utils.validResult(event));
								}
							});
				} else {
					result.handle(Utils.validResult(res));
				}
			}
		});
	}

	@Override
	public void get(String blogId, final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query = QueryBuilder.start("_id").is(blogId);
		mongo.findOne(BLOG_COLLECTION, MongoQueryBuilder.build(query),
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				result.handle(Utils.validResult(event));
			}
		});
	}

	@Override
	public void list(UserInfos user, final Handler<Either<String, JsonArray>> result) {
		QueryBuilder query = QueryBuilder.start("shared").elemMatch(
				QueryBuilder.start("userId").is(user.getUserId()).get());
		JsonObject sort = new JsonObject().putNumber("modified", -1);
		mongo.find(BLOG_COLLECTION, MongoQueryBuilder.build(query), sort, null,
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				result.handle(Utils.validResults(event));
			}
		});
	}

	private boolean validationError(Handler<Either<String, JsonObject>> result, JsonObject b) {
		if (b == null) {
			result.handle(new Either.Left<String, JsonObject>("Validation error : invalids fields."));
			return true;
		}
		return false;
	}

}