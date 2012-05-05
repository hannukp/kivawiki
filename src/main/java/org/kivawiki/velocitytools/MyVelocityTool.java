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
package org.kivawiki.velocitytools;


import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.context.Context;
import org.kivawiki.site.WikiUtils;
import org.springframework.web.servlet.support.RequestContext;

public class MyVelocityTool {
	private Context context;
	
	public void setVelocityContext(Context context) {
		this.context = context;
	}
	
	public String url(String text) {
		String path = text;
		String q = null;
		
		int qindex = text.indexOf("?");
		if (qindex >= 0) {
			path = text.substring(0, qindex);
			q = text.substring(qindex + 1, text.length());
		}
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		RequestContext requestContext = (RequestContext)context.get("springMacroRequestContext");
		String fullPath = requestContext != null ? requestContext.getContextUrl(path) : path;
		String url = WikiUtils.encodeUri(fullPath, q, null);
		return StringEscapeUtils.escapeXml(url);
	}
	
	public String error(String message) {
		if (StringUtils.isBlank(message)) {
			return "";
		}
		return "<div class=\"errorbox\">ERROR: " + StringEscapeUtils.escapeXml(message) + "</div>";
	}
	
	public String warning(String message) {
		if (StringUtils.isBlank(message)) {
			return "";
		}
		return "<div class=\"warningbox\">WARNING: " + StringEscapeUtils.escapeXml(message) + "</div>";
	}
	
	public String info(String message) {
		if (StringUtils.isBlank(message)) {
			return "";
		}
		return "<div class=\"infobox\">INFO: " + StringEscapeUtils.escapeXml(message) + "</div>";
	}
}
