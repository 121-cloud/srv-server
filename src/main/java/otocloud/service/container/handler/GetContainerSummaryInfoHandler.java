/*
 * Copyright (C) 2015 121Cloud Project Group  All rights reserved.
 */
package otocloud.service.container.handler;

import io.vertx.core.json.JsonObject;

import java.util.List;

import otocloud.framework.app.engine.AppServiceEngine;
import otocloud.framework.core.CommandMessage;
import otocloud.framework.core.OtoCloudEventHandlerBase;
import otocloud.framework.core.OtoCloudService;
import otocloud.framework.core.OtoCloudServiceContainer;


/**
 * TODO: DOCUMENT ME! 
 * @date 2015年7月1日
 * @author lijing@yonyou.com
 */
public class GetContainerSummaryInfoHandler extends OtoCloudEventHandlerBase<JsonObject> {
	
	
	public static final String SUMMARY_INFO_GET = "container.summary_info.get";
	
	private OtoCloudServiceContainer container;
	/**
	 * Constructor.
	 *
	 * @param appServiceEngine
	 */
	public GetContainerSummaryInfoHandler(OtoCloudServiceContainer container) {
		this.container = container;
	}

	@Override
	public void handle(CommandMessage<JsonObject> msg) {

		List<OtoCloudService> sysModuleList = container.getSystemServices();
		Integer sysModuleSize = 0;
		if(sysModuleList != null)
			sysModuleSize = sysModuleList.size();
		
		List<AppServiceEngine> appModuleList = container.getAppServices();
		Integer appModuleSize = 0;
		if(appModuleList != null)
			appModuleSize = appModuleList.size();
		
		JsonObject retMsg = new JsonObject();
		retMsg.put("systemModules", sysModuleSize);
		retMsg.put("appModules", appModuleSize);
		
		msg.reply(retMsg);

    }

	public String getEventAddress() {
		return container.getContainerName() + "." + SUMMARY_INFO_GET;
	}
	
}
