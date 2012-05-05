/**
 * Copyright 2012 Hannu Kankaanpää
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 * @author Hannu Kankaanpää <hannu.kp@gmail.com>
 */
package org.kivawiki.site;


import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.kivawiki.repo.RepoUser;

public abstract class WikiUtils {
	public static String getPathAfterPrefix(String prefix, HttpServletRequest req) {
		String path = req.getServletPath();
		int i = path.indexOf(prefix);
		if (i < 0) {
			throw new IllegalStateException();
		}
		String rv = path.substring(i + prefix.length());
		if (!rv.startsWith("/")) {
			rv = '/' + rv;
		}
		return rv;
	}

	public static RepoUser getUser(HttpServletRequest req) {
		String username = getUsername(req);
		String password = getCookieValue(req, "password");
		if (username == null || password == null) {
			return null;
		}
		return new RepoUser(username, password);
	}
	
	public static String getUsername(HttpServletRequest req) {
		return getCookieValue(req, "username");
	}

	private static String getCookieValue(HttpServletRequest req, String name) {
		Cookie[] cookies = req.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie c : cookies) {
			if (c.getName().equals(name)) {
				return c.getValue();
			}
		}
		return null;
	}

	public static void setCachingSeconds(HttpServletResponse res, int durationSeconds) {
		long now = System.currentTimeMillis();
		res.addHeader("Cache-Control", "max-age=" + durationSeconds);
		//res.addHeader("Cache-Control", "must-revalidate");//optional
		res.setDateHeader("Last-Modified", now);
		res.setDateHeader("Expires", now + durationSeconds * 1000L);
	}
	
	public static String encodeUri(String path, String query, String fragment) {
		try {
			return new URI(null, null, path, query, fragment).toString();
		} catch (URISyntaxException ex) {
			throw new RuntimeException(ex);
		}
	}
}
