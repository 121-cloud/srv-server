/*
 * Copyright (C) 2015 121Cloud Project Group  All rights reserved.
 */
package otocloud.service.container.handler;

import otocloud.framework.core.OtoCloudBusMessage;
import otocloud.framework.core.OtoCloudEventHandlerBase;
import otocloud.framework.core.OtoCloudServiceContainer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


/**
 * TODO: DOCUMENT ME! 
 * @date 2015年7月1日
 * @author lijing@yonyou.com
 */
public class ServiceUndeploymentHandler extends OtoCloudEventHandlerBase<JsonObject> {
	
	//{container}.platform.component.undeploy
	public static final String SERVICE_UNDEPLOYMENT = "container.service.undeploy";
	
	private OtoCloudServiceContainer container;
	/**
	 * Constructor.
	 *
	 * @param appServiceEngine
	 */
	public ServiceUndeploymentHandler(OtoCloudServiceContainer container) {
		this.container = container;
	}

	@Override
	public void handle(OtoCloudBusMessage<JsonObject> msg) {
		JsonObject body = msg.body();
		System.out.println(body.toString());
		
		JsonObject params = body.getJsonObject("queryParams");
		
		String serviceName = params.getString("moduleCode");
		String moduleType = params.getString("moduleType", "");

		Future<Void> undepFuture = Future.future();
		if(moduleType == "CONTAINER"){
			container.undeployManageComponent(serviceName, undepFuture);
		}else{
			container.undeployService(serviceName, undepFuture);
		}		
			
		undepFuture.setHandler(undepRet -> {    		
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
		return container.getContainerName() + "." + SERVICE_UNDEPLOYMENT;
	}
	
}
