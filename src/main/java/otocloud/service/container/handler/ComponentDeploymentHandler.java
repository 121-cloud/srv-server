/*
 * Copyright (C) 2015 121Cloud Project Group  All rights reserved.
 */
package otocloud.service.container.handler;

import io.vertx.core.json.JsonObject;
import otocloud.framework.core.CommandMessage;
import otocloud.framework.core.CompDeploymentHandler;
import otocloud.framework.core.OtoCloudEventHandlerBase;
import otocloud.framework.core.OtoCloudServiceContainer;


/**
 * TODO: DOCUMENT ME! 
 * @date 2015年7月1日
 * @author lijing@yonyou.com
 */
public class ComponentDeploymentHandler extends OtoCloudEventHandlerBase<JsonObject> {
	
	public static final String CONTAINER_COMP_DEPLOYMENT = "container.component.deploy";	
	
	private OtoCloudServiceContainer container;
	/**
	 * Constructor.
	 *
	 * @param appServiceEngine
	 */
	public ComponentDeploymentHandler(OtoCloudServiceContainer container) {
		this.container = container;
	}

	@Override
	public void handle(CommandMessage<JsonObject> msg) {
		//JsonObject body = msg.body();
		//System.out.println(body.toString());

		JsonObject content = msg.getContent(); //body.getJsonObject("content");		

		JsonObject compDepMsg = new JsonObject();		
		compDepMsg.put("component_deployment", content.getJsonObject("component_deployment"));
		compDepMsg.put("component_config", content.getJsonObject("component_config"));
		
		JsonObject params = msg.getQueryParams(); //body.getJsonObject("queryParams");
		String serviceName = params.getString("moduleName");
		compDepMsg.put("service_name", serviceName);		

		if(params.containsKey("account")){
			compDepMsg.put("account", params.getString("account"));
		}
		
		container.getVertxOfApp(serviceName).eventBus().send(serviceName + "." + CompDeploymentHandler.COMPONENT_DEPLOYMENT,
				compDepMsg, depRet->{
		    		if (depRet.succeeded()) {	    			
		    			msg.reply(new JsonObject().put("deployment_status", "completed"));
		    		}else{    			
		               	Throwable err = depRet.cause();               	
		               	//getLogger().error(err.getMessage(), err);
		               	msg.fail(400, err.getMessage());
		    		}
				});	

    }

	public String getEventAddress() {
		return container.getContainerName() + "." + CONTAINER_COMP_DEPLOYMENT;
	}
	
}
