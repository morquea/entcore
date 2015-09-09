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

package org.entcore.common.storage.impl;

import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.DefaultAsyncResult;
import fr.wseduc.webutils.FileUtils;
import fr.wseduc.webutils.http.ETag;
import org.entcore.common.storage.Storage;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.http.HttpServerFileUpload;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

//import static fr.wseduc.webutils.FileUtils.*;

public class GridfsStorage implements Storage {

	public static final long BUFFER_SIZE = 261120l;
	private final EventBus eb;
	private final String gridfsAddress;
	private final String bucket;
	private final Vertx vertx;
	private static final Logger log = LoggerFactory.getLogger(GridfsStorage.class);

	public GridfsStorage(Vertx vertx, EventBus eb, String gridfsAddress) {
		this(vertx, eb, gridfsAddress, "fs");
	}

	public GridfsStorage(Vertx vertx, EventBus eb, String gridfsAddress, String bucket) {
		this.eb = eb;
		String node = (String) vertx.sharedData().getMap("server").get("node");
		if (node == null) {
			node = "";
		}
		this.gridfsAddress = node + gridfsAddress;
		this.bucket = bucket;
		this.vertx = vertx;
	}

	@Override
	public void writeUploadFile(HttpServerRequest request, Handler<JsonObject> handler) {
		FileUtils.gridfsWriteUploadFile(request, eb, gridfsAddress, handler);
	}

	@Override
	public void writeUploadFile(HttpServerRequest request, Long maxSize, Handler<JsonObject> handler) {
		FileUtils.gridfsWriteUploadFile(request, eb, gridfsAddress, maxSize, handler);
	}

	@Override
	public void writeBuffer(Buffer buff, String contentType, String filename, Handler<JsonObject> handler) {
		FileUtils.gridfsWriteBuffer(buff, contentType, filename, eb, handler, gridfsAddress);
	}

	@Override
	public void writeBuffer(String id, Buffer buff, String contentType, String filename, Handler<JsonObject> handler) {
		FileUtils.gridfsWriteBuffer(id, buff, contentType, filename, eb, handler, gridfsAddress);
	}

	@Override
	public void writeFsFile(final String id, final String filePath, final Handler<JsonObject> handler) {
		final String contentType = "application/zip";
		// TODO defensive programming
		final String filename = filePath.substring(filePath.lastIndexOf(File.separator) + 1);
		vertx.fileSystem().props(filePath, new Handler<AsyncResult<FileProps>>() {
			@Override
			public void handle(AsyncResult<FileProps> event) {
				if (event.succeeded()) {
					final long fileSize = event.result().size();
					vertx.fileSystem().open(filePath, new Handler<AsyncResult<AsyncFile>>() {
						@Override
						public void handle(AsyncResult<AsyncFile> event) {
							if (event.succeeded()) {
								final AsyncFile asyncFile = event.result();
								int nbChunks = (int) Math.ceil(fileSize/BUFFER_SIZE);
								final Handler[] handlers = new Handler[nbChunks +1];

								handlers[handlers.length - 1] = new Handler<AsyncResult<Buffer>>() {
									@Override
									public void handle(AsyncResult<Buffer> asyncResult) {
										if (asyncResult.failed()) {
											handler.handle(new JsonObject().putString("status", "error")
													.putString("message", asyncResult.cause().getMessage()));
											return;
										}
										Buffer buff = asyncResult.result();
										JsonObject save = new JsonObject();
										save.putString("action", "saveChunk");
										save.putString("content-type", contentType);
										save.putString("filename", filename);
										save.putString("_id", id);
										save.putNumber("n", handlers.length - 1);
										save.putNumber("length", fileSize);

										byte [] header = null;
										try {
											header = save.toString().getBytes("UTF-8");
										} catch (UnsupportedEncodingException e) {
											JsonObject json = new JsonObject().putString("status", "error")
													.putString("message", e.getMessage());
											handler.handle(json);
										}
										if (header != null) {
											buff.appendBytes(header).appendInt(header.length);
											eb.send(gridfsAddress, buff, new Handler<Message<JsonObject>>() {
												@Override
												public void handle(Message<JsonObject> message) {
													handler.handle(message.body());
													asyncFile.close();
												}
											});
										}
										//handler.handle(new JsonObject().putString("status", "ok"));
									}
								};

								for (int i = nbChunks - 1; i >= 0; i--) {
									final int j = i;
									handlers[i] = new Handler<AsyncResult<Buffer>>() {
										@Override
										public void handle(AsyncResult<Buffer> asyncResult) {
											if (asyncResult.failed()) {
												handler.handle(new JsonObject().putString("status", "error")
														.putString("message", asyncResult.cause().getMessage()));
												return;
											}
											Buffer buff = asyncResult.result();
											JsonObject save = new JsonObject();
											save.putString("action", "saveChunk");
											save.putString("content-type", contentType);
											save.putString("filename", filename);
											save.putString("_id", id);
											save.putNumber("n", j);
											save.putNumber("length", fileSize);

											byte [] header = null;
											try {
												header = save.toString().getBytes("UTF-8");
											} catch (UnsupportedEncodingException e) {
												JsonObject json = new JsonObject().putString("status", "error")
														.putString("message", e.getMessage());
												handler.handle(json);
											}
											if (header != null) {
												buff.appendBytes(header).appendInt(header.length);
												eb.send(gridfsAddress, buff, new Handler<Message<JsonObject>>() {
													@Override
													public void handle(Message<JsonObject> message) {
														if ("ok".equals(message.body().getString("status"))) {
															asyncFile.read(new Buffer((int) BUFFER_SIZE), 0,
																	(j + 1) * BUFFER_SIZE, (int) BUFFER_SIZE, handlers[j + 1]);
														}
													}
												});
											}

										}
									};
								}

								asyncFile.read(new Buffer((int) BUFFER_SIZE), 0, 0, (int) BUFFER_SIZE, handlers[0]);
								//handlers[0].handle(null);
//								Buffer buffer = new Buffer(BUFFER_SIZE);
//								event.result().read(buffer, 0, )
							} else {
								handler.handle(new JsonObject().putString("status", "error")
										.putString("message", event.cause().getMessage()));
							}
						}
					});
				} else {
					handler.handle(new JsonObject().putString("status", "error")
							.putString("message", event.cause().getMessage()));
				}
			}
		});
	}

//	private static void gridfsWriteUploadChunksFile(final HttpServerRequest request, final EventBus eb,
//											 final String gridfsAddress, final Handler<JsonObject> handler) {
//		gridfsWriteUploadChunksFile(request, eb, gridfsAddress, null, handler);
//	}
//
//	private static void gridfsWriteUploadChunksFile(final HttpServerRequest request, final EventBus eb,
//											 final String gridfsAddress, final Long maxSize, final Handler<JsonObject> handler) {
//		request.expectMultiPart(true);
//		request.uploadHandler(new Handler<HttpServerFileUpload>() {
//			@Override
//			public void handle(final HttpServerFileUpload event) {
//				final Buffer buff = new Buffer();
//				event.dataHandler(new Handler<Buffer>() {
//					@Override
//					public void handle(Buffer event) {
//						buff.appendBuffer(event);
//					}
//				});
//				event.endHandler(new Handler<Void>() {
//					@Override
//					public void handle(Void end) {
//						gridfsWriteChunksBuffer(null, buff, maxSize, handler, eb,
//								event.contentType(), event.filename(), metadata(event), gridfsAddress);
//					}
//				});
//			}
//		});
//	}
//
//
//
//	private static void gridfsWriteChunksBuffer(String id, Buffer buff, Long maxSize,
//										  final Handler<JsonObject> handler, EventBus eb, String contentType,
//										  String filename, final JsonObject m, final String gridfsAddress) {
//		JsonObject save = new JsonObject();
//		save.putString("action", "save");
//		save.putString("content-type", contentType);
//		save.putString("filename", filename);
//		if (id != null && !id.trim().isEmpty()) {
//			save.putString("_id", id);
//		}
//		final JsonObject metadata = (m != null) ? m : new JsonObject()
//				.putString("content-type", contentType)
//				.putString("filename", filename);
//		if (metadata.getLong("size", 0l).equals(0l)) {
//			metadata.putNumber("size", buff.length());
//		}
//		if (maxSize != null && maxSize < metadata.getLong("size", 0l)) {
//			handler.handle(new JsonObject().putString("status", "error")
//					.putString("message", "file.too.large"));
//			return;
//		}
//		byte [] header = null;
//		try {
//			header = save.toString().getBytes("UTF-8");
//		} catch (UnsupportedEncodingException e) {
//			JsonObject json = new JsonObject().putString("status", "error")
//					.putString("message", e.getMessage());
//			handler.handle(json);
//		}
//		if (header != null) {
//			buff.appendBytes(header).appendInt(header.length);
//			eb.send(gridfsAddress, buff, new Handler<Message<JsonObject>>() {
//				@Override
//				public void handle(Message<JsonObject> message) {
//					handler.handle(message.body()
//							.putObject("metadata", metadata));
//				}
//			});
//		}
//	}

	@Override
	public void readFile(String id, Handler<Buffer> handler) {
		FileUtils.gridfsReadFile(id, eb, gridfsAddress, handler);
	}

	@Override
	public void sendFile(String id, String downloadName, HttpServerRequest request, boolean inline, JsonObject metadata) {
		gridfsSendChunkFile(id, downloadName, eb, gridfsAddress, request.response(), inline, metadata);
	}

	@Override
	public void sendFile(String id, String downloadName, HttpServerRequest request, boolean inline, JsonObject metadata,
			Handler<AsyncResult<Void>> resultHandler) {
		gridfsSendChunkFile(id, downloadName, eb, gridfsAddress, request.response(), inline, metadata, resultHandler);
	}

	private static void gridfsReadChunkFile(final String id, final EventBus eb,
									  final String gridfsAddress, final Handler<Buffer> handler) {
		JsonObject find = new JsonObject();
		find.putString("action", "countChunks");
		find.putString("files_id", id);
		byte [] header = null;
		try {
			header = find.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			handler.handle(new Buffer());
		}
		if (header != null) {
			Buffer buf = new Buffer(header);
			buf.appendInt(header.length);
			eb.send(gridfsAddress, buf, new  Handler<Message>() {
				@Override
				public void handle(Message res) {
					if (res.body() instanceof Long) {
						Long number = (Long) res.body();
						if (number == null || number == 0l) {
							handler.handle(null);
						} else {
							final AtomicLong count = new AtomicLong(number);
							for (long i = 0; i < number.longValue(); i++) {
								JsonObject find = new JsonObject();
								find.putString("action", "getChunk");
								find.putString("files_id", id);
								find.putNumber("n", i);
								byte [] header = null;
								try {
									header = find.toString().getBytes("UTF-8");
								} catch (UnsupportedEncodingException e) {
									handler.handle(new Buffer());
								}
								Buffer buf = new Buffer(header);
								buf.appendInt(header.length);
								eb.send(gridfsAddress, buf, new  Handler<Message>() {
									@Override
									public void handle(Message res) {
										if (res.body() instanceof Buffer) {
											handler.handle((Buffer) res.body());
											if (count.decrementAndGet() <= 0) {
												// TODO refactor to reply class with state (EOF in this case)
												handler.handle(null);
											}
										} else {
											handler.handle(null);

										}
									}
								});
							}
						}
					} else {
						handler.handle(null);
					}
				}
			});
		}
	}

	private static void gridfsSendChunkFile(final String id, final String downloadName, final EventBus eb,
									  final String gridfsAddress, final HttpServerResponse response, final boolean inline,
									  final JsonObject metadata) {
		gridfsSendChunkFile(id, downloadName, eb, gridfsAddress, response, inline, metadata, null);
	}

	private static void gridfsSendChunkFile(final String id, final String downloadName, final EventBus eb,
									  final String gridfsAddress, final HttpServerResponse response, final boolean inline,
									  final JsonObject metadata, final Handler<AsyncResult<Void>> resultHandler) {
		final AtomicInteger n = new AtomicInteger(-1);
		response.setChunked(true);
		gridfsReadChunkFile(id, eb, gridfsAddress, new Handler<Buffer>() {
			@Override
			public void handle(Buffer chunk) {
				if (chunk == null) {
					if (n.get() == 0) {
						response.setStatusCode(404).setStatusMessage("Not Found").end();
						if (resultHandler != null) {
							resultHandler.handle(new DefaultAsyncResult<>((Void) null));
						}
					} else {
						response.end();
						if (resultHandler != null) {
							resultHandler.handle(new DefaultAsyncResult<>((Void) null));
						}
					}
					return;
				}
				if (n.get() == 0) {
					if (!inline) {
						String name = downloadName;
						if (metadata != null && metadata.getString("filename") != null) {
							String filename = metadata.getString("filename");
							int fIdx = filename.lastIndexOf('.');
							String fExt = null;
							if (fIdx >= 0) {
								fExt = filename.substring(fIdx);
							}
							int dIdx = downloadName.lastIndexOf('.');
							String dExt = null;
							if (dIdx >= 0) {
								dExt = downloadName.substring(dIdx);
							}
							if (fExt != null && !fExt.equals(dExt)) {
								name += fExt;
							}
						}
						response.putHeader("Content-Disposition",
								"attachment; filename=\"" + name + "\"");
					} else {
						ETag.addHeader(response, id);
					}
					if (metadata != null && metadata.getString("content-type") != null) {
						response.putHeader("Content-Type", metadata.getString("content-type"));
					}
				}
				response.write(chunk);
			}
		});
	}

	@Override
	public void removeFile(String id, Handler<JsonObject> handler) {
		FileUtils.gridfsRemoveFile(id, eb, gridfsAddress, handler);
	}

	@Override
	public void removeFiles(JsonArray ids, Handler<JsonObject> handler) {
		FileUtils.gridfsRemoveFiles(ids, eb, gridfsAddress, handler);
	}

	@Override
	public void copyFile(String id, Handler<JsonObject> handler) {
		FileUtils.gridfsCopyFile(id, eb, gridfsAddress, handler);
	}

	@Override
	public void writeToFileSystem(String [] ids, String destinationPath, JsonObject alias,
			final Handler<JsonObject> handler) {
		QueryBuilder q = QueryBuilder.start("_id").in(ids);
		JsonObject e = new JsonObject()
				.putString("action", "write")
				.putString("path", destinationPath)
				.putObject("alias", alias)
				.putObject("query", MongoQueryBuilder.build(q));
		eb.send(gridfsAddress + ".json", e, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				handler.handle(event.body());
			}
		});
	}

	@Override
	public String getProtocol() {
		return "gridfs";
	}

	@Override
	public String getBucket() {
		return bucket;
	}

}
