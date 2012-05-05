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

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.context.Context;
import org.apache.velocity.tools.Scope;
import org.apache.velocity.tools.ToolboxFactory;
import org.apache.velocity.tools.config.XmlFactoryConfiguration;
import org.apache.velocity.tools.view.ViewToolContext;
import org.springframework.web.servlet.view.velocity.VelocityToolboxView;

/**
 * Configures Velocity Toolbox View to use new XML Configuration in Spring
 *
 * @author Gregor "hrax" Magdolen
 */
public class VelocityToolboxView2 extends VelocityToolboxView {

	public VelocityToolboxView2() {
		setEncoding("utf-8");
		setContentType("text/html; charset=utf-8");
	}

	@Override
	protected Context createVelocityContext(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Create a ViewToolContext instance since ChainedContext is deprecated in Velocity Tools 2.0.
		ViewToolContext velocityContext = new ViewToolContext(
				getVelocityEngine(), request, response, getServletContext());
		velocityContext.putAll(model);

		// Load a Configuration and publish toolboxes to the context when necessary
		if (getToolboxConfigLocation() != null) {
			XmlFactoryConfiguration cfg = new XmlFactoryConfiguration();
			cfg.read(getClass().getResourceAsStream(getToolboxConfigLocation()));
			ToolboxFactory factory = cfg.createFactory();

			velocityContext.addToolbox(factory.createToolbox(Scope.APPLICATION));
			velocityContext.addToolbox(factory.createToolbox(Scope.REQUEST));
			velocityContext.addToolbox(factory.createToolbox(Scope.SESSION));
		}

		return velocityContext;
	}
}
