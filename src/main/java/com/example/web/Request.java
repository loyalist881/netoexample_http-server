package com.example.web;

import org.apache.http.NameValuePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final byte[] body;
    private final List<NameValuePair> queryParams;
    private final List<NameValuePair> postParams;

    public Request(String method, String path, Map<String, String> headers, byte[] body, List<NameValuePair> queryParams, List<NameValuePair> postParams) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
        this.queryParams = queryParams;
        this.postParams = postParams;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() { return body; }

    public List<NameValuePair> getQueryParams() {
        return queryParams;
    }

    public List<String> getQueryParam(String name) {
        List<String> values = new ArrayList<>();
        for (NameValuePair pair : queryParams) {
            if (pair.getName().equalsIgnoreCase(name)) { 
                values.add(pair.getValue());
            }
        }
        return values;
    }

    public List<NameValuePair> getPostParams() {
        return postParams;
    }

    public List<String> getPostParam(String name) {
        List<String> values = new ArrayList<>();
        for (NameValuePair pair : postParams) {
            if (pair.getName().equalsIgnoreCase(name)) { 
                values.add(pair.getValue());
            }
        }
        return values;
    }
}
