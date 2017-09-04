/*
 * jvmtop - java monitoring for the command-line
 *
 * Copyright (C) 2013 by Patric Rufflar. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.jvmtop.view;

import com.jvmtop.Config;

import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Base class for all console views, providing some helper methods for formatting.
 */
public abstract class AbstractConsoleView implements ConsoleView {
    protected final Config config;
    private boolean shouldExit = false;

    protected AbstractConsoleView(Config p_config) {
        config = p_config;
    }

    /**
     * Formats a long value containing "number of bytes" to its megabyte representation.
     * If the value is negative, "n/a" will be returned.
     *
     * TODO: implement automatic scale to bigger units if this makes sense
     * (e.g. output 4.3g instead of 4324m)
     */
    public String toMB(long bytes) {
        if(bytes<0) {
            return "n/a";
        }
        return "" + (bytes / 1024 / 1024) + "m";
    }

    /**
     * Formats number of milliseconds to a HH:MM representation
     *
     * TODO: implement automatic scale (e.g. 1d 7h instead of 31:13m)
     */
    public String toHHMM(long millis) {
        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb);
        formatter.format("%02d:%02dm", millis / 1000 / 3600, (millis / 1000 / 60) % 60);
        return sb.toString();
    }

    /**
     * Returns a substring of the given string, representing the 'length' most-right characters
     */
    public String rightStr(String str, int length) {
        return str.substring(Math.max(0, str.length() - length));
    }

    /**
     * Returns a substring of the given string, representing the 'length' most-left characters
     */
    public String leftStr(String str, int length) {
        return str.substring(0, Math.min(str.length(), length));
    }

    /**
     * Joins the given list of strings using the given delimiter delim
     */
    public String join(List<String> list, String delim) {
        StringBuilder sb = new StringBuilder();
        String loopDelim = "";

        for (String s : list) {
            sb.append(loopDelim);
            sb.append(s);
            loopDelim = delim;
        }
        return sb.toString();
    }

    public boolean shouldExit() { return shouldExit; }
    public boolean isTopBarRequired() { return true; }
    public boolean isClearingRequired() { return true; }
    protected void exit() { shouldExit = true; }

    /**
     * Requests the disposal of this view - it should be called again.
     * TODO: refactor / remove this functional, use proper exception handling instead.
     */

    /**
     * Sorts a Map by its values, using natural ordering.
     */
    public <K,V extends Comparable> Map<K,V> sortByValue(Map<K,V> map, boolean reverse) {
        List<Map.Entry<K,V>> list = new LinkedList<>(map.entrySet());
        list.sort(Comparator.comparing(o -> (o.getValue())));

        if (reverse) {
            Collections.reverse(list);
        }

        Map<K,V> result = new LinkedHashMap<>();
        for (Map.Entry<K,V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public void sleep(long millis) throws Exception {
        Thread.sleep(millis);
    }

    public void last() throws Exception { }
}
