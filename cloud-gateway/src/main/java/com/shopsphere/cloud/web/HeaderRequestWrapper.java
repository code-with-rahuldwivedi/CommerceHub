package com.shopsphere.cloud.web;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** Lets the gateway add or hide request headers before the request is proxied downstream. */
final class HeaderRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> added = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Set<String> removed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    HeaderRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    void set(String name, String value) {
        removed.remove(name);
        added.put(name, value);
    }

    void remove(String name) {
        added.remove(name);
        removed.add(name);
    }

    @Override
    public String getHeader(String name) {
        if (removed.contains(name)) {
            return null;
        }
        String value = added.get(name);
        return value != null ? value : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (removed.contains(name)) {
            return Collections.emptyEnumeration();
        }
        String value = added.get(name);
        return value != null ? Collections.enumeration(Collections.singletonList(value)) : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<String> original = super.getHeaderNames();
        while (original != null && original.hasMoreElements()) {
            names.add(original.nextElement());
        }
        names.removeAll(removed);
        names.addAll(added.keySet());
        return Collections.enumeration(names);
    }
}
