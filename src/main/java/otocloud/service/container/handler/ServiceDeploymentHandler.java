/*
 * Copyright (C) 2015 121Cloud Project Group  All rights reserved.
 */
package otocloud.service.container.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import otocloud.framework.core.CommandMessage;
import otocloud.framework.core.OtoCloudEventHandlerBase;
import otocloud.framework.core.OtoCloudServiceContainer;
import otocloud.service.container.OtoCloudServiceContainerImpl;


/**
 * TODO: DOCUMENT ME! 
 * @date 2015年7月1日
 * @author lijing@yonyou.com
 */
public class ServiceDeploymentHandler extends OtoCloudEventHandlerBase<JsonArray> {
	
	//{container}.platform.component.deploy
	public static final String SERVICE_DEPLOYMENT = "container.service.deploy";
	
	private OtoCloudServiceContainer container;
	/**
	 * Constructor.
	 *
	 * @param appServiceEngine
	 */
	public ServiceDeploymentHandler(OtoCloudServiceContainer container) {
		this.container = container;
	}

	@Override
	public void handle(CommandMessage<JsonArray> msg) {
/*		JsonObject body = msg.body();
		System.out.println(body.toString());*/
		
		try{

			JsonArray content = msg.getContent(); //.getJsonArray("content");
			JsonArray retContent = new JsonArray();
	
			Future<Void> depFuture = Future.future();
			doRecursionDeploy(content, retContent, 0, content.size(), depFuture);			
			depFuture.setHandler(depRet -> {    		
	    		if (depRet.succeeded()) {	    			
	    			msg.reply(retContent);
	    		}else{
	               	Throwable err = depRet.cause();
	               	//getLogger().error(err.getMessage(), err);
	               	msg.fail(400, err.getMessage());
	    		}
	    	});
		
		}catch(Exception e){
			msg.fail(400, e.getMessage());
		}

    }
	
	
	private void  doRecursionDeploy(JsonArray srvDepInfos, JsonArray completionResults, Integer index, Integer size, Future<Void> retFuture){
		JsonObject srvDepInfo = srvDepInfos.getJsonObject(index);
		
		JsonObject deploymentDesc = srvDepInfo.getJsonObject(OtoCloudServiceContainerImpl.MODULE_DEPLOYMENT_KEY);
		JsonObject srvConfig = srvDepInfo.getJsonObject(OtoCloudServiceContainerImpl.MODULE_CONFIG_KEY);		
		
		String moduleid = deploymentDesc.getString("module_id");
		String moduleType = deploymentDesc.getString("module_type", "");
		
		JsonObject completionResult = new JsonObject();
		completionResult.put("module_id", moduleid);
		completionResults.add(completionResult);

		Future<Void> depFuture = Future.future();
		if(moduleType == "CONTAINER"){
			container.deployManagerComponent(deploymentDesc, srvConfig, depFuture);			
		
		}else{
			container.deployService(deploymentDesc, srvConfig, depFuture);	
		}
		
		depFuture.setHandler(depRet -> {    		
    		if (depRet.succeeded()) {
    			completionResult.put("deployment_status", "completed");
    		}else{    			
               	Throwable err = depRet.cause();   
               	completionResult.put("deployment_status", "failed");
               	completionResult.put("err_cause", err.getMessage());
               	//getLogger().error(err.getMessage(), err);
               	err.printStackTrace();
    		}
			Integer nextIdx = index + 1;
			if (nextIdx < size)
				doRecursionDeploy(srvDepInfos, completionResults, nextIdx, size, retFuture);
			else if (nextIdx >= size) {
				retFuture.complete();
			}
    		
    	});	

	}

	public String getEventAddress() {
		return container.getContainerName() + "." + SERVICE_DEPLOYMENT;
	}
	
}
