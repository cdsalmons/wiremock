/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tomakehurst.wiremock.servlet;

import static com.google.common.io.ByteStreams.toByteArray;
import static com.tomakehurst.wiremock.http.RequestMethod.POST;
import static com.tomakehurst.wiremock.http.RequestMethod.PUT;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.google.common.base.Optional;
import com.tomakehurst.wiremock.http.ContentTypeHeader;
import com.tomakehurst.wiremock.http.RequestMethod;
import com.tomakehurst.wiremock.mapping.Request;
import com.tomakehurst.wiremock.mapping.Response;
import com.tomakehurst.wiremock.mapping.ResponseDefinition;

public class ProxyResponseRenderer implements ResponseRenderer {
	
	private static final int MINUTES = 1000 * 60;

	@Override
	public Response render(ResponseDefinition responseDefinition) {
		HttpClient client = new DefaultHttpClient();
		HttpParams params = client.getParams();
		HttpConnectionParams.setConnectionTimeout(params, 5 * MINUTES);
		HttpConnectionParams.setSoTimeout(params, 5 * MINUTES);
		HttpUriRequest httpRequest = getHttpRequestFor(responseDefinition);
		addRequestHeaders(httpRequest, responseDefinition);
		
		try {
			addBodyIfPostOrPut(httpRequest, responseDefinition);
			HttpResponse httpResponse = client.execute(httpRequest);
			Response response = new Response(httpResponse.getStatusLine().getStatusCode());
			for (Header header: httpResponse.getAllHeaders()) {
				response.addHeader(header.getName(), header.getValue());
			}
			
			HttpEntity entity = httpResponse.getEntity();
			if (entity != null) {
				response.setBody(toByteArray(entity.getContent()));
			}
			
			return response;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static HttpUriRequest getHttpRequestFor(ResponseDefinition response) {
		RequestMethod method = response.getOriginalRequest().getMethod();
		String url = response.getProxyBaseUrl() + response.getOriginalRequest().getUrl();
		
		switch (method) {
		case GET:
			return new HttpGet(url);
		case POST:
			return new HttpPost(url);
		case PUT:
			return new HttpPut(url);
		case DELETE:
			return new HttpDelete(url);
		case HEAD:
			return new HttpHead(url);
		case OPTIONS:
			return new HttpOptions(url);
		case TRACE:
			return new HttpTrace(url);
		default:
			throw new RuntimeException("Cannot create HttpMethod for " + method);
		}
	}
	
	private static void addRequestHeaders(HttpRequest httpRequest, ResponseDefinition response) {
		Request originalRequest = response.getOriginalRequest(); 
		for (String key: originalRequest.getAllHeaderKeys()) {
			if (!key.equals("Content-Length")) {
				String value = originalRequest.getHeader(key);
				httpRequest.addHeader(key, value);
			}
		}
	}
	
	private static void addBodyIfPostOrPut(HttpRequest httpRequest, ResponseDefinition response) throws UnsupportedEncodingException {
		Request originalRequest = response.getOriginalRequest();
		if (originalRequest.getMethod() == POST || originalRequest.getMethod() == PUT) {
			HttpEntityEnclosingRequest requestWithEntity = (HttpEntityEnclosingRequest) httpRequest;
			Optional<ContentTypeHeader> optionalContentType = ContentTypeHeader.getFrom(originalRequest);
			String body = originalRequest.getBodyAsString();
			
			if (optionalContentType.isPresent()) {
				ContentTypeHeader header = optionalContentType.get();
				requestWithEntity.setEntity(new StringEntity(body,
						header.mimeTypePart(),
						header.encodingPart().isPresent() ? header.encodingPart().get() : "utf-8"));
			} else {
				requestWithEntity.setEntity(new StringEntity(body,
						"text/plain",
						"utf-8"));
			}
		}
	}

}
