/*
 * Copyright (C) 2015 121Cloud Project Group  All rights reserved.
 */
package otocloud.service.container.handler;

import otocloud.framework.core.CompUndeploymentHandler;
import otocloud.framework.core.OtoCloudBusMessage;
import otocloud.framework.core.OtoCloudEventHandlerBase;
import otocloud.framework.core.OtoCloudServiceContainer;
import io.vertx.core.json.JsonObject;


/**
 * TODO: DOCUMENT ME! 
 * @date 2015年7月1日
 * @author lijing@yonyou.com
 */
public class ComponentUndeploymentHandler extends OtoCloudEventHandlerBase<JsonObject> {
	
	//{container}.platform.component.deploy
	public static final String CONTAINER_COMP_UNDEPLOYMENT = "container.component.undeploy";
	
	private OtoCloudServiceContainer container;
	/**
	 * Constructor.
	 *
	 * @param appServiceEngine
	 */
	public ComponentUndeploymentHandler(OtoCloudServiceContainer container) {
		this.container = container;
	}

	@Override
	public void handle(OtoCloudBusMessage<JsonObject> msg) {
		JsonObject body = msg.body();
		System.out.println(body.toString());

		JsonObject compUnDepMsg = new JsonObject();
		
		JsonObject params = body.getJsonObject("queryParams");
		
		String serviceName = params.getString("moduleName");
		
		compUnDepMsg.put("comp_name", params.getString("componentName"));

		if(params.containsKey("account")){
			compUnDepMsg.put("account", params.getString("account"));
		}

		container.getVertxOfApp(serviceName).eventBus().send(serviceName + "." + CompUndeploymentHandler.COMPONENT_UNDEPLOYMENT,
				compUnDepMsg, undepRet -> {    		
		    		if (undepRet.succeeded()) {	
		    			msg.reply(new JsonObject().put("deployment_status", "undeployed"));
		    		}else{    			
		               	Throwable err = undepRet.cause();               	
		               	//getLogger().error(err.getMessage(), err);
		               	msg.fail(400, err.getMessage());
		    		}
		    	});
    }

	public String getEventAddress() {
		return container.getContainerName() + "." + CONTAINER_COMP_UNDEPLOYMENT;
	}
	
}
