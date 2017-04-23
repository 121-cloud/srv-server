/*
 * Copyright (C) 2015 121Cloud Project Group  All rights reserved.
 */
package otocloud.service.container;

import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.VerticleFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import otocloud.common.CommandCodec;
import otocloud.common.CommandResultCodec;
import otocloud.common.OtoCloudDirectoryHelper;
import otocloud.common.OtoConfiguration;
import otocloud.common.util.JsonUtil;
import otocloud.framework.app.common.AppConfiguration;
import otocloud.framework.app.engine.AppServiceEngine;
import otocloud.framework.common.OtoCloudServiceLifeCycleImpl;
import otocloud.framework.common.OtoCloudServiceState;
import otocloud.framework.core.AppVertxInstancePool;
import otocloud.framework.core.OtoCloudEventHandlerRegistry;
import otocloud.framework.core.OtoCloudService;
import otocloud.framework.core.OtoCloudServiceContainer;
import otocloud.framework.core.OtoCloudServiceDepOptions;
import otocloud.framework.core.OtoCloudServiceImpl;
import otocloud.framework.core.VertxInstancePool;
import otocloud.framework.core.factory.OtoCloudHttpSecureServiceFactory;
import otocloud.framework.core.factory.OtoCloudHttpServiceFactory;
import otocloud.framework.core.factory.OtoCloudMavenServiceFactory;
import otocloud.framework.core.factory.OtoCloudServiceFactory;
import otocloud.service.container.handler.ComponentDeploymentHandler;
import otocloud.service.container.handler.ComponentUndeploymentHandler;
import otocloud.service.container.handler.GetContainerSummaryInfoHandler;
import otocloud.service.container.handler.ServiceDeploymentHandler;
import otocloud.service.container.handler.ServiceUndeploymentHandler;



/**
 * TODO: DOCUMENT ME!
 * @date 2015骞�9鏈�27鏃�
 * @author lijing@yonyou.com
 */
public class OtoCloudServiceContainerImpl extends OtoCloudServiceLifeCycleImpl implements OtoCloudServiceContainer {
	private Logger logger = LoggerFactory.getLogger(this.getClass().getName());
	
	public static final String REST_URI_REG = "platform.register.rest.to.webserver";
	public static final String REST_URI_UNREG = "platform.unregister.rest.to.webserver";
	
	public static final String MODULE_DEPLOYMENT_KEY = "module_deployment";
	public static final String MODULE_CONFIG_KEY = "module_config";
	public static final String CONTAINER_COMPS_KEY = "container_components";	
	
	private Vertx vertx;
	private AppVertxInstancePool appVertxInstPool;
	private Map<String, Deployment> services;	
	private Map<String, Vertx> srvVertxs;	
	private String containerName = "";
	private JsonObject containerCfg;
	private JsonObject vertxOptionsCfg;
	private JsonObject mavenCfg;
	private JsonArray serviceDeploymentList;
	private JsonObject clusterCfg;
	private boolean clusterEnabled = false;
	private boolean hasContainerComps = true;	
	private JsonArray manageComponentList;
	private Map<String, Deployment> manageComponents;
	
/*	private boolean webserverEnabled = true;
	private boolean monitorEnabled = true;*/
	
	private List<String> restApiRegisterIds;
	
	private List<OtoCloudEventHandlerRegistry> handlers;
	

	
	public OtoCloudServiceContainerImpl(boolean needCluster){
		this.clusterEnabled = needCluster;		
		
		appVertxInstPool = new AppVertxInstancePool();
		
		Map<String, Deployment> srvSet = new HashMap<String, Deployment>();
		services = Collections.synchronizedMap(srvSet);
		
		Map<String, Deployment> mangCompSet = new HashMap<String, Deployment>();
		manageComponents = Collections.synchronizedMap(mangCompSet);
		
		Map<String, Vertx> srvVertxSet = new HashMap<String, Vertx>();
		srvVertxs = Collections.synchronizedMap(srvVertxSet);
		
		List<String> restApiRegIds = new ArrayList<String>();
		restApiRegisterIds = Collections.synchronizedList(restApiRegIds);
		
		List<OtoCloudEventHandlerRegistry> containerHandlers = new ArrayList<OtoCloudEventHandlerRegistry>();
		handlers = Collections.synchronizedList(containerHandlers);

	}
	
	public Vertx getVertx() {
		return vertx;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getContainerName() {
		
		return containerName;
	}
	
	@Override
	public List<Deployment> getDeployments(){
		List<Deployment> ret = new ArrayList<Deployment>();
		vertx.deploymentIDs().forEach(deploymentID->{
			ret.add(((VertxImpl)vertx).getDeployment(deploymentID));
		});
		return ret;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<OtoCloudService> getSystemServices() {
		List<OtoCloudService> ret = new ArrayList<OtoCloudService>();
		appVertxInstPool.getAppVertxPool().forEach(item->{
			Set<String> deps = item.deploymentIDs();
			if(!deps.isEmpty()){
				for(String deploymentID: deps) {
					Set<Verticle> depVerticles = ((VertxImpl)item).getDeployment(deploymentID).getVerticles();
					Iterator<Verticle> it = depVerticles.iterator();
					Verticle vertObj = it.next();
					if(vertObj instanceof AppServiceEngine){
						continue;
					}
					if(vertObj instanceof OtoCloudService){
						ret.add((OtoCloudService)vertObj);
					}
				}
			}
		});
		return ret;
	}	


	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AppServiceEngine> getAppServices() {
		List<AppServiceEngine> ret = new ArrayList<AppServiceEngine>();
		appVertxInstPool.getAppVertxPool().forEach(item->{
			Set<String> deploymentIDs = item.deploymentIDs();
			if(!deploymentIDs.isEmpty()){
				deploymentIDs.forEach(deploymentID->{
					Set<Verticle> deps = ((VertxImpl)item).getDeployment(deploymentID).getVerticles();
					Iterator<Verticle> it = deps.iterator();
					Verticle vertObj = it.next();
					if(vertObj instanceof AppServiceEngine){
						ret.add((AppServiceEngine)vertObj);
					}
				});
			}		
		});
		return ret;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(Future<Void> initFuture) {
		statusReset();
		setFutureStatus(OtoCloudServiceState.INITIALIZED);	
		
		createHandlers();
		
		Future<Void> loadFuture = Future.future();
		loadConfig(loadFuture);
		loadFuture.setHandler(ret -> {
    		if(ret.failed()){    			
    			futureStatusRollback();
    			initFuture.fail(ret.cause());	
    		}else{  
    			configMavenSrvFactory();
    			configHttpSrvFactory();	
    			
    	    	Future<Void> vertxInstFuture = Future.future();
    	    	createManagementVertx(vertxInstFuture);
    	    	vertxInstFuture.setHandler(instRet -> {
    	    		if (instRet.succeeded()) {
    	    			Future<Void> appVertxInstFuture = Future.future();
    	    			createAppVertxPool(appVertxInstFuture);
    	    			appVertxInstFuture.setHandler(appInstRet -> {
    	    	    		if (appInstRet.succeeded()) {
    	    		    		futureStatusComplete();
    	    		    		initFuture.complete();    	    	    			

    	    	    		}else{    	    			
    	    	    			futureStatusRollback();
    	    	               	Throwable err = appInstRet.cause();   
    	    	               	//err.printStackTrace();
    	    	               	logger.error(err.getMessage(), err);
    	    	               	
    	    	               	initFuture.fail(err);
    	    	    		}
    	    	    	}); 			

    	    		}else{    	    			
    	    			futureStatusRollback();
    	               	Throwable err = instRet.cause();   
    	               	//err.printStackTrace();
    	               	logger.error(err.getMessage(), err);
    	               	
    	               	initFuture.fail(err);
    	    		}
    	    	}); 
    		}
		});		    				

	}
	
	
	private void createManagementVertx(Future<Void> initFuture){
		if(!clusterEnabled){			
			vertx = Vertx.vertx();
			registerFactory();
		}else{				
			//loadClusterConfig();			
			String zkCfgFilePath = OtoCloudDirectoryHelper.getConfigDirectory() + "zookeeper.json";	
			
			Vertx.vertx().fileSystem().readFile(zkCfgFilePath, zkResult -> {
	    	    if (zkResult.succeeded()) {
	    	    	
	    	    	String zfFileContent = zkResult.result().toString(); 
	    	        System.out.println(zfFileContent);
	    	        clusterCfg = new JsonObject(zfFileContent);			
			
					VertxOptions options = OtoCloudServiceImpl.createVertxOptions(containerCfg, clusterCfg, null);				
					// 鍒涘缓缇ら泦Vertx杩愯鏃剁幆澧�
					Vertx.clusteredVertx(options, res -> {
						// 杩愯鏃跺垱寤哄畬鍚庡垵濮嬪寲
						if (res.succeeded()) {
							// 鍒涘缓vertx瀹炰緥
							vertx = res.result();	
							
							vertx.eventBus().registerCodec(new CommandCodec());
							vertx.eventBus().registerCodec(new CommandResultCodec());
							
							registerFactory();
							initFuture.complete();
						} else {
							futureStatusRollback();
							Throwable err = res.cause();
							initFuture.fail(err);
			    	    }
					});		
	    	    }else{
	    	    	System.err.println("zookeeper.json not found" + zkResult.cause());    	     
	    	    	initFuture.fail(zkResult.cause());
	    	    }
			});
			
		}	
	}
	
	private void registerFactory(){
		OtoCloudServiceFactory serviceFactory = new OtoCloudServiceFactory();	
		vertx.registerVerticleFactory(serviceFactory);
		
		OtoCloudMavenServiceFactory mavenServiceFactory = new OtoCloudMavenServiceFactory();	
		vertx.registerVerticleFactory(mavenServiceFactory);
		
		OtoCloudHttpServiceFactory httpServiceFactory = new OtoCloudHttpServiceFactory();	
		vertx.registerVerticleFactory(httpServiceFactory);
		
		OtoCloudHttpSecureServiceFactory httpSecureServiceFactory = new OtoCloudHttpSecureServiceFactory();	
		vertx.registerVerticleFactory(httpSecureServiceFactory);
	}	
	
	private void unRegisterFactory(){
		Set<VerticleFactory> verticleFactories = vertx.verticleFactories();
		if(verticleFactories != null && verticleFactories.size() > 0){
			for(VerticleFactory verticleFactory : verticleFactories){
				vertx.unregisterVerticleFactory(verticleFactory);
			}			
		}
	}
	
	private void createAppVertxPool(Future<Void> initFuture){
		int appVertxPoolSize = containerCfg.getInteger(AppConfiguration.APP_VERTX_NUMBER_KEY, 1);	
		
    	Future<Void> vertxInstFuture = Future.future();
    	appVertxInstPool.init(containerCfg, getClusterConfig(), appVertxPoolSize, this.vertxOptionsCfg, vertxInstFuture);
    	vertxInstFuture.setHandler(run -> {
    		if (run.succeeded()) {   			
				initFuture.complete();
    		}else{  			
               	Throwable err = run.cause();  
               	//err.printStackTrace();
               	logger.error(err.getMessage(), err);
               	initFuture.fail(err);            	
    		}
    	}); 			
	}
	
	private String buildEventAddress(String address) {
		return getContainerName() + "." + address;
	}
	
	
	private void registerURI(Future<Void> regFuture) {
		if(!hasContainerComps){
			regFuture.complete();
			return;
		}
		
		JsonArray uriMappingInfos = new JsonArray();
		
		JsonObject srvRegInfo = new JsonObject()
				.put("address", buildEventAddress(ServiceDeploymentHandler.SERVICE_DEPLOYMENT))
				//.put("decoratingAddress", buildEventAddress(ContainerRestUrlResolver.ADDRESS))
				.put("uri", "/module-deployments")
				.put("method", "post");
		uriMappingInfos.add(srvRegInfo);
		
		JsonObject srvUnRegInfo = new JsonObject()
				.put("address", buildEventAddress(ServiceUndeploymentHandler.SERVICE_UNDEPLOYMENT))
				//.put("decoratingAddress", buildEventAddress(ContainerRestUrlResolver.ADDRESS))
				.put("uri", "/module-deployments/:moduleCode/:moduleType")
				.put("method", "delete");
		uriMappingInfos.add(srvUnRegInfo);
		
		JsonObject compRegInfo = new JsonObject()
			.put("address", buildEventAddress(ComponentDeploymentHandler.CONTAINER_COMP_DEPLOYMENT))
			//.put("decoratingAddress", buildEventAddress(ContainerRestUrlResolver.ADDRESS))
			.put("uri", "/:moduleName/component-deployments")
			.put("method", "post");
		uriMappingInfos.add(compRegInfo);
		JsonObject compRegInfo2 = new JsonObject()
			.put("address", buildEventAddress(ComponentDeploymentHandler.CONTAINER_COMP_DEPLOYMENT))
			//.put("decoratingAddress", buildEventAddress(ContainerRestUrlResolver.ADDRESS))
			.put("uri", "/:moduleName/:account/component-deployments")
			.put("method", "post");
		uriMappingInfos.add(compRegInfo2);

		JsonObject compUnRegInfo = new JsonObject()
			.put("address", buildEventAddress(ComponentUndeploymentHandler.CONTAINER_COMP_UNDEPLOYMENT))
			//.put("decoratingAddress", buildEventAddress(ContainerRestUrlResolver.ADDRESS))
			.put("uri", "/:moduleName/component-deployments/:componentName")
			.put("method", "delete");
		uriMappingInfos.add(compUnRegInfo);
		JsonObject compUnRegInfo2 = new JsonObject()
			.put("address", buildEventAddress(ComponentUndeploymentHandler.CONTAINER_COMP_UNDEPLOYMENT))
			//.put("decoratingAddress", buildEventAddress(ContainerRestUrlResolver.ADDRESS))
			.put("uri", "/:moduleName/:account/component-deployments/:componentName")
			.put("method", "delete");
		uriMappingInfos.add(compUnRegInfo2);
		
		JsonObject getSummaryInfo = new JsonObject()
			.put("address", buildEventAddress(GetContainerSummaryInfoHandler.SUMMARY_INFO_GET))
			//.put("decoratingAddress", buildEventAddress(ContainerRestUrlResolver.ADDRESS))
			.put("uri", "/summary-info")
			.put("method", "get");
		uriMappingInfos.add(getSummaryInfo);
		
		vertx.eventBus().send(this.containerName + "." + REST_URI_REG,
				uriMappingInfos, ret->{
					if(ret.succeeded()){
						JsonArray retObj = (JsonArray)ret.result().body(); 
						retObj.forEach(item->{
							restApiRegisterIds.add(((JsonObject)item).getString("result"));
						});						
						
						regFuture.complete();
					}else{
						Throwable err = ret.cause();
						//err.printStackTrace();
						logger.error(err.getMessage(), err);
						regFuture.fail(err);
					}
					
		});		
		
	}
	
	private void unRegisterURI(Future<Void> unregFuture){
		try{
			if(!hasContainerComps){
				unregFuture.complete();
				return;
			}
			
			if(restApiRegisterIds.size() > 0){		
				unregFuture.complete();
				return;			
			}
			
			JsonArray uriMappingInfos = new JsonArray();
			restApiRegisterIds.forEach(value->{
				JsonObject srvRegInfo = new JsonObject().put("registerId", value);
				uriMappingInfos.add(srvRegInfo);
			});
			
			vertx.eventBus().send(containerCfg.getString("webserver_name","") + "." + REST_URI_UNREG,
					uriMappingInfos, ret->{
						if(ret.succeeded()){
							unregFuture.complete();
						}else{
							Throwable err = ret.cause();
							//err.printStackTrace();
							logger.error(err.getMessage(), err);
							unregFuture.fail(err);
						}
						
			});		
		}catch(Exception ex){
			//Throwable err = ret.cause();
			//err.printStackTrace();
			logger.error(ex.getMessage(), ex);
			unregFuture.fail(ex.getCause());
		}
		
	}
	
	private void configHttpSrvFactory(){
		JsonObject httpRepoCfg = containerCfg.getJsonObject("otocloud_http_factory", null);	
		if(httpRepoCfg != null){
			httpRepoCfg.forEach(cfgItem->{
				String keyString = cfgItem.getKey();			
				System.setProperty(keyString, (String)cfgItem.getValue());					
			});	
		}
	}
	
	private void configMavenSrvFactory(){
		mavenCfg = containerCfg.getJsonObject("otocloud_maven_factory", null);	
		if(mavenCfg != null){
			mavenCfg.forEach(cfgItem->{
				String keyString = cfgItem.getKey();			
				System.setProperty(keyString, (String)cfgItem.getValue());					
			});	
		}
	}
	
/*	private void loadClusterConfig() {
		clusterCfg = OtoCloudServiceImpl.loadClusterConfig();
	}*/
	
	public void loadConfig(Future<Void> loadFuture) {	

		String cfgFilePath = OtoCloudDirectoryHelper.getConfigDirectory() + "otocloud-container.json";			
		
		Vertx.vertx().fileSystem().readFile(cfgFilePath, result -> {
    	    if (result.succeeded()) {
    	    	String fileContent = result.result().toString(); 
    	    	logger.info(fileContent);
    	        containerCfg = new JsonObject(fileContent);
    	        containerName = containerCfg.getString("container_name", "");
    	        
    	        if(containerCfg.containsKey(MODULE_DEPLOYMENT_KEY)){    	        
    	        	serviceDeploymentList = containerCfg.getJsonArray(MODULE_DEPLOYMENT_KEY);
    	        }else{
    	        	serviceDeploymentList = new JsonArray();
    	        	containerCfg.put(MODULE_DEPLOYMENT_KEY, serviceDeploymentList);
    	        }
    	        
    	        vertxOptionsCfg = containerCfg.getJsonObject(OtoConfiguration.VERTX_OPTIONS_KEY, null);
    	        
    	        loadFuture.complete();
    	        
    	    }else{
    	    	loadFuture.fail(result.cause());
    	    }
		});

	}	
	


	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run(Future<Void> runFuture) {
		
		if(stateChangeIsReady() == false){
			runFuture.fail("瀹瑰櫒:[" + getContainerName() + "]鐘舵�佸彉鍖栬繕鏈畬鎴愶紝涓嶅厑璁竢un");
			return;
		}
			
		if(currentState == OtoCloudServiceState.INITIALIZED
				|| currentState == OtoCloudServiceState.STOPPED){ 	
			
			setFutureStatus(OtoCloudServiceState.RUNNING);			
			
			internalRun(runFuture);

		}else{
			runFuture.fail("瀹瑰櫒:[" + getContainerName() + "]鐘舵�佷负:" + currentState.toString() + ",涓嶈兘run");
		}	

	}

	public void internalRun(Future<Void> runFuture) {		
		
		registerHandlers();
		
		Future<Void> containerCompsFuture = Future.future();
		deployManagerComponents(containerCompsFuture);
		containerCompsFuture.setHandler(ret -> {
    		if(ret.failed()){    			
    			futureStatusRollback();
    			runFuture.fail(ret.cause());	
    		}else{  
    			Future<Void> regFuture = Future.future();
    			registerURI(regFuture);   		
    			regFuture.setHandler(regRet->{
    				if(regRet.succeeded()){
    					
    	    			if(serviceDeploymentList != null && serviceDeploymentList.size() > 0){		    				
    	    				Integer startIndex = 0;
    	    				Future<Void> depFuture = Future.future();
    	    				doDeployService(serviceDeploymentList, startIndex, serviceDeploymentList.size(), depFuture);
    	    				depFuture.setHandler(depRet -> {
    	    					//设置mavenfactory更新策略
    	    					System.setProperty(OtoCloudMavenServiceFactory.REMOTE_SNAPSHOT_POLICY_SYS_PROP, "always");
    	    					
    	    		    		if(depRet.failed()){
    	    		    			futureStatusRollback();
    	    		    			runFuture.fail(depRet.cause());	
    	    		    		}else{		    			
    	    			    		futureStatusComplete();
    	    		    			runFuture.complete();
    	    		    		}
    	    				});		    				
    	    			}else{   			    			
    	    	    		futureStatusComplete();
    	    				runFuture.complete();
    	    			}
    		    		
    		    		
    				}else{
						Throwable err = regRet.cause();
						//err.printStackTrace();
						logger.error(err.getMessage(), err);
						futureStatusRollback();
						runFuture.fail(err);	
    				}
    			});
    		}
		});		    				
    	        

	}	
	
	private void deployManagerComponents(Future<Void> containerCompsFuture){
		manageComponentList = containerCfg.getJsonArray(CONTAINER_COMPS_KEY, null);
		if(manageComponentList == null || manageComponentList.size() == 0){
			hasContainerComps = false;
			containerCompsFuture.complete();
			return;
		}
		
		doDeployManagerComponent(manageComponentList, 0, manageComponentList.size(), containerCompsFuture);
	}
	
	private void doDeployManagerComponent(JsonArray autoDeplSrvListCfg, Integer startIndex, Integer size, Future<Void> depFuture) {
		JsonObject depConfig = autoDeplSrvListCfg.getJsonObject(startIndex);
		
		String verticleName = depConfig.getString("name");				

		String srvName = OtoCloudServiceFactory.getServiceName(verticleName);
	    String descriptorFile = srvName + ".json";
	      
	      String cfgFilePath = OtoCloudDirectoryHelper.getConfigDirectory() + descriptorFile;			
	      vertx.fileSystem().readFile(cfgFilePath, result -> {
	    	    if (result.succeeded()) {
	    	    	try{
		    	    	String fileContent = result.result().toString(); 	    	        
		    	    	JsonObject descriptor = new JsonObject(fileContent);	    	
	
		    	    	String main = descriptor.getString("main");
		    			OtoCloudServiceDepOptions deploymentOptions = new OtoCloudServiceDepOptions(this, main); 
		    			deploymentOptions.fromJson(descriptor.getJsonObject("options"));
		    	    	
		    			vertx.deployVerticle(verticleName, deploymentOptions,
		    				res -> {				
		    						if (res.succeeded()) {
		    							String deploymentID = res.result();		    							
		    							this.manageComponents.put(srvName, ((VertxImpl)vertx).getDeployment(deploymentID));		    											

		    							logger.info("Service:[" + verticleName + "] deploy successed!");
		    						} else {
		    							Throwable err = res.cause();
		    							//err.printStackTrace();
		    							logger.error(err.getMessage(), err);
		    							//logger.error(err.getMessage(), err);
		    							depFuture.fail(err);
		    							return;
		    						}
		    						Integer nextIdx = startIndex + 1;
		    						if (nextIdx < size)
		    							doDeployManagerComponent(autoDeplSrvListCfg, nextIdx, size, depFuture);
		    						else if (nextIdx >= size) {
		    							depFuture.complete();
		    						}
		    				});
		    			
	    	    	}catch(Exception e){
	    	    		depFuture.fail(e);
	    	    	}
	    	    }else{
					Throwable err = result.cause();
					//err.printStackTrace();
					logger.error(err.getMessage(), err);
					
					depFuture.fail(err);
	    	    }
	      });
		
	}

	
	@Override
	public void deployManagerComponent(JsonObject deploymentDesc, JsonObject srvConfig,
			Future<Void> depFuture) {
		
		String verticleName = deploymentDesc.getString("name");
		
		if(!verticleName.startsWith("otocloud_http:") && !verticleName.startsWith("otocloud_maven:") ){
			depFuture.fail(new Exception("module_name[" + verticleName + "] not found prefix: [otocloud_http] or [otocloud_maven]"));
			return;
		}

	    String  identifier = OtoCloudServiceFactory.getServiceName(verticleName);
		if(manageComponents.containsKey(identifier)){
			depFuture.fail(new Exception("module:[" + identifier + "] has existed!"));	
			return;
		}
		
		String srvName = OtoCloudServiceFactory.getServiceName(verticleName);
		
    	JsonObject descriptor = srvConfig;	    	

    	String main = descriptor.getString("main");
		OtoCloudServiceDepOptions deploymentOptions = new OtoCloudServiceDepOptions(this, main); 
		deploymentOptions.fromJson(descriptor.getJsonObject("options"));    	
		
		vertx.deployVerticle(verticleName, deploymentOptions,
			res -> {				
				if (res.succeeded()) {
					String deploymentID = res.result();
					manageComponents.put(srvName, ((VertxImpl)vertx).getDeployment(deploymentID));
					
				    String descriptorFile = identifier + ".json";	      
				    String cfgFilePath = OtoCloudDirectoryHelper.getConfigDirectory() + descriptorFile;
					Buffer buffer = JsonUtil.writeToBuffer(srvConfig);					
					//鎸佷箙鍖栨湇鍔￠厤缃�
					vertx.fileSystem().writeFile(cfgFilePath, buffer, handler->{
						if(handler.succeeded()){							
							//鎸佷箙鍖栧鍣ㄩ厤缃枃浠�
							manageComponentList.add(deploymentDesc);				
							String containerCfgFile = OtoCloudDirectoryHelper.getConfigDirectory() + "otocloud-container.json";	
							Buffer containerCfgFileBuf = JsonUtil.writeToBuffer(containerCfg);
							vertx.fileSystem().writeFile(containerCfgFile, containerCfgFileBuf, saveRet->{
								if(saveRet.succeeded()){
									logger.info("Service:[" + verticleName + "] deploy successed!");
									depFuture.complete();
								}else{
									Throwable err = saveRet.cause();
									logger.error(err.getMessage(), err);
									//err.printStackTrace();
									//logger.error(err.getMessage(), err);
									depFuture.fail(err);
								}
							});
						}else{
							Throwable err = handler.cause();
							logger.error(err.getMessage(), err);
							//err.printStackTrace();
							//logger.error(err.getMessage(), err);
							depFuture.fail(err);
						}			
					});
					
				} else {
					Throwable err = res.cause();
					logger.error(err.getMessage(), err);
					//err.printStackTrace();
					//logger.error(err.getMessage(), err);
					depFuture.fail(err);
				}				
			});

	}
	
	
	private void doDeployService(JsonArray autoDeplSrvListCfg, Integer startIndex, Integer size, Future<Void> depFuture) {
		JsonObject depConfig = autoDeplSrvListCfg.getJsonObject(startIndex);
		
		String verticleName = depConfig.getString("name");				

		String srvName = OtoCloudServiceFactory.getServiceName(verticleName);
	    String descriptorFile = srvName + ".json";
	      
	      String cfgFilePath = OtoCloudDirectoryHelper.getConfigDirectory() + descriptorFile;			
	      vertx.fileSystem().readFile(cfgFilePath, result -> {
	    	    if (result.succeeded()) {
	    	    	try{
		    	    	String fileContent = result.result().toString(); 	    	        
		    	    	JsonObject descriptor = new JsonObject(fileContent);	    	
	
		    	    	String main = descriptor.getString("main");
		    			OtoCloudServiceDepOptions deploymentOptions = new OtoCloudServiceDepOptions(this, main); 
		    			deploymentOptions.fromJson(descriptor.getJsonObject("options"));
		    	    	
		    			Vertx appVertx = appVertxInstPool.getVertx();		
		    			appVertx.deployVerticle(verticleName, deploymentOptions,
		    				res -> {				
		    						if (res.succeeded()) {
		    							String deploymentID = res.result();		    							
		    							services.put(srvName, ((VertxImpl)appVertx).getDeployment(deploymentID));
		    							srvVertxs.put(srvName, appVertx);    							

		    							logger.info("Service:[" + verticleName + "] deploy successed!");
		    						} else {
		    							Throwable err = res.cause();
		    							//err.printStackTrace();
		    							logger.error(err.getMessage(), err);
		    							//logger.error(err.getMessage(), err);
		    						}
		    						Integer nextIdx = startIndex + 1;
		    						if (nextIdx < size)
		    							doDeployService(autoDeplSrvListCfg, nextIdx, size, depFuture);
		    						else if (nextIdx >= size) {
		    							depFuture.complete();
		    						}
		    				});
		    			
	    	    	}catch(Exception e){
	    	    		depFuture.fail(e);
	    	    	}
	    	    }else{
					Throwable err = result.cause();
					//err.printStackTrace();
					logger.error(err.getMessage(), err);
					
					depFuture.fail(err);
	    	    }
	      });
		
		
		
	}
	

	
	private void createHandlers(){
		ServiceDeploymentHandler serviceDeploymentHandler = new ServiceDeploymentHandler(this);
		handlers.add(serviceDeploymentHandler);
		
		ServiceUndeploymentHandler serviceUndeploymentHandler = new ServiceUndeploymentHandler(this);
		handlers.add(serviceUndeploymentHandler);	

		GetContainerSummaryInfoHandler containerSummaryInfoHandler = new GetContainerSummaryInfoHandler(this);
		handlers.add(containerSummaryInfoHandler);
		
		ComponentDeploymentHandler componentDeploymentHandler = new ComponentDeploymentHandler(this);
		handlers.add(componentDeploymentHandler);
		
		ComponentUndeploymentHandler componentUndeploymentHandler = new ComponentUndeploymentHandler(this);
		handlers.add(componentUndeploymentHandler);
	}
	
	private void registerHandlers(){
		if(handlers != null && handlers.size() > 0){
			EventBus bus = vertx.eventBus();
			handlers.forEach(value -> {
				value.register(bus);
			});
		}	
	}
	
/*	private void unregisterHandlers(Future<Void> unregFuture) {
		if(handlers != null && handlers.size() > 0){
			Integer size = handlers.size();
			AtomicInteger stoppedCount = new AtomicInteger(0);			
			handlers.forEach(value -> {
				Future<Void> singleUnregFuture = Future.future();
				value.unRegister(singleUnregFuture);				
				singleUnregFuture.setHandler(unregRet -> {
            		if(unregRet.succeeded()){   

            		}else{
            			Throwable err = unregRet.cause();
            			//err.printStackTrace(); 
            			logger.error(err.getMessage(), err);
            			
            			unregFuture.fail(err);
            			return;
            		}
            		
               		if (stoppedCount.incrementAndGet() >= size) {         			
               			unregFuture.complete();
                    }         		
            		
               	});	               	
			});
		}else{
			unregFuture.complete();
		}

	}
*/
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void stop(Future<Void> stopFuture) {
		if(stateChangeIsReady() == false){
			stopFuture.fail("瀹瑰櫒:[" + getContainerName() + "]鐘舵�佸彉鍖栬繕鏈畬鎴愶紝涓嶅厑璁歌繘琛宻top");
			return;
		}
		if(currentState != OtoCloudServiceState.RUNNING){
			stopFuture.fail("瀹瑰櫒:[" + getContainerName() + "]鐘舵�佸繀椤讳负:RUNNING,鎵嶈兘stop");
			return;
		}	
		
		setFutureStatus(OtoCloudServiceState.STOPPED);
		
		Future<Void> mangeCompsStopFuture = Future.future();
		destroyManagementComponents(mangeCompsStopFuture);   		
		mangeCompsStopFuture.setHandler(agentStopRet->{
			if(agentStopRet.succeeded()){    
				Future<Void> appSrvStopFuture = Future.future();
				destroyAppServices(appSrvStopFuture);   		
				appSrvStopFuture.setHandler(appStopRet->{
					if(appStopRet.succeeded()){    
						futureStatusComplete();
						stopFuture.complete();
					}else{
						futureStatusRollback();
						Throwable err = appStopRet.cause();
						//err.printStackTrace(); 
						logger.error(err.getMessage(), err);
						stopFuture.fail(err);							
					}
				});
				
			}else{
				futureStatusRollback();
				Throwable err = agentStopRet.cause();
				//err.printStackTrace();   
				logger.error(err.getMessage(), err);
				stopFuture.fail(err);	
			}
		});                    			
		

	}
	
	private void destroyManagementComponents(Future<Void> stopFuture){
		unRegisterFactory();
/*		Future<Void> unregHandlerfuture = Future.future();
		unregisterHandlers(unregHandlerfuture);
		unregHandlerfuture.setHandler(unregHandlerRet -> {
    		if(unregHandlerRet.succeeded()){   */
		
    			Future<Void> unregFuture = Future.future();
    			unRegisterURI(unregFuture);   		
    			unregFuture.setHandler(unregRet->{
    				if(unregRet.succeeded()){
						vertx.close(vertxCloseRet -> {
							if(vertxCloseRet.succeeded()){				
								stopFuture.complete();
							}else{					
		            			Throwable err = vertxCloseRet.cause();
		            			//err.printStackTrace();  
		            			logger.error(err.getMessage(), err);
		            			stopFuture.fail(err);
		            		}

						});

    				}else{
            			Throwable err = unregRet.cause();
            			//err.printStackTrace();   
            			logger.error(err.getMessage(), err);
            			stopFuture.fail(err);
    				}
    			});
    			
 /*   		}else{
    			Throwable err = unregHandlerRet.cause();
    			//err.printStackTrace();   
    			logger.error(err.getMessage(), err);
    			stopFuture.fail(err);
    		}
		});*/
	}
	
	
	private void destroyAppServices(Future<Void> stopFuture){
		
		if(serviceDeploymentList != null && serviceDeploymentList.size() > 0){		 
			int count = serviceDeploymentList.size();
			int startIndex = count - 1;
			Future<Void> undepFuture = Future.future();
			doUnDeployService(serviceDeploymentList, startIndex, undepFuture);
			undepFuture.setHandler(depRet -> {
	    		if(depRet.failed()){
					Throwable err = depRet.cause();
					//err.printStackTrace();
					logger.error(err.getMessage(), err);
					stopFuture.fail(err);	
	    		}else{		
	    			services.clear();
	    			Integer size = srvVertxs.size();
	    			AtomicInteger stoppedCount = new AtomicInteger(0);	    			
	    			srvVertxs.forEach((key, appVertx) -> {
						appVertx.close(vertxCloseRet -> {
							if(vertxCloseRet.succeeded()){	
								
							}else{					
		            			Throwable err = vertxCloseRet.cause();
		            			//err.printStackTrace(); 
		            			logger.error(err.getMessage(), err);
		            		}
		            		if (stoppedCount.incrementAndGet() >= size) {
		            			srvVertxs.clear();
		            			stopFuture.complete();
		                    }            		
						});
					});

	    		}
			});		    				
		}else{   			    			
			services.clear();
			srvVertxs.clear();
			stopFuture.complete();
		}
			
/*			srvVertxs.forEach((key, appVertx) -> {
				appVertx.close(vertxCloseRet -> {
					if(vertxCloseRet.succeeded()){	
						
					}else{					
            			Throwable err = vertxCloseRet.cause();
            			err.printStackTrace();   
            		}
            		if (stoppedCount.incrementAndGet() >= size) {
            			srvVertxs.clear();
            			services.clear();
            			stopFuture.complete();
                    }            		
				});
			});*/

	}
	
	private void doUnDeployService(JsonArray autoDeplSrvListCfg, Integer startIndex, Future<Void> depFuture) {
		JsonObject depConfig = autoDeplSrvListCfg.getJsonObject(startIndex);
		
		String verticleName = depConfig.getString("name");
		String srvName = OtoCloudServiceFactory.getServiceName(verticleName);
		
		Future<Void> undepSrvFuture = Future.future();
		innerUndeployService(srvName, true, undepSrvFuture);
		undepSrvFuture.setHandler(handler->{
			if (handler.succeeded()) {
				
			} else {
				Throwable err = handler.cause();
				//err.printStackTrace();
				logger.error(err.getMessage(), err);
				//logger.error(err.getMessage(), err);
			}
			//从后往前关闭服务
			Integer nextIdx = startIndex - 1;
			if (nextIdx >= 0)
				doUnDeployService(autoDeplSrvListCfg, nextIdx, depFuture);
			else{
				depFuture.complete();
			}
		});

	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deployService(JsonObject deploymentDesc, JsonObject srvConfig,
			Future<Void> depFuture) {
		
		String verticleName = deploymentDesc.getString("name");
		
		if(!verticleName.startsWith("otocloud_http:") && !verticleName.startsWith("otocloud_maven:") ){
			depFuture.fail(new Exception("module_name[" + verticleName + "] not found prefix: [otocloud_http] or [otocloud_maven]"));
			return;
		}

	    String  identifier = OtoCloudServiceFactory.getServiceName(verticleName);
		if(services.containsKey(identifier)){
			depFuture.fail(new Exception("module:[" + identifier + "] has existed!"));	
			return;
		}
		
		String srvName = OtoCloudServiceFactory.getServiceName(verticleName);
		
    	JsonObject descriptor = srvConfig;	    	

    	String main = descriptor.getString("main");
		OtoCloudServiceDepOptions deploymentOptions = new OtoCloudServiceDepOptions(this, main); 
		deploymentOptions.fromJson(descriptor.getJsonObject("options"));
    	
		Vertx appVertx = appVertxInstPool.getVertx();
		appVertx.deployVerticle(verticleName, deploymentOptions,
			res -> {				
				if (res.succeeded()) {
					String deploymentID = res.result();
					services.put(srvName, ((VertxImpl)appVertx).getDeployment(deploymentID));
					srvVertxs.put(srvName, appVertx);
					
				    String descriptorFile = identifier + ".json";	      
				    String cfgFilePath = OtoCloudDirectoryHelper.getConfigDirectory() + descriptorFile;
					Buffer buffer = JsonUtil.writeToBuffer(srvConfig);					
					//鎸佷箙鍖栨湇鍔￠厤缃�
					vertx.fileSystem().writeFile(cfgFilePath, buffer, handler->{
						if(handler.succeeded()){							
							//鎸佷箙鍖栧鍣ㄩ厤缃枃浠�
							serviceDeploymentList.add(deploymentDesc);				
							String containerCfgFile = OtoCloudDirectoryHelper.getConfigDirectory() + "otocloud-container.json";	
							Buffer containerCfgFileBuf = JsonUtil.writeToBuffer(containerCfg);
							vertx.fileSystem().writeFile(containerCfgFile, containerCfgFileBuf, saveRet->{
								if(saveRet.succeeded()){
									logger.info("Service:[" + verticleName + "] deploy successed!");
									depFuture.complete();
								}else{
									Throwable err = saveRet.cause();
									logger.error(err.getMessage(), err);
									//err.printStackTrace();
									//logger.error(err.getMessage(), err);
									depFuture.fail(err);
								}
							});
						}else{
							Throwable err = handler.cause();
							logger.error(err.getMessage(), err);
							//err.printStackTrace();
							//logger.error(err.getMessage(), err);
							depFuture.fail(err);
						}			
					});
					
				} else {
					Throwable err = res.cause();
					logger.error(err.getMessage(), err);
					//err.printStackTrace();
					//logger.error(err.getMessage(), err);
					depFuture.fail(err);
				}				
			});

	}
	
	public Vertx getVertxOfApp(String serviceName){
		return srvVertxs.get(serviceName);	
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void undeployService(String serviceName, Future<Void> undepFuture) {
		innerUndeployService(serviceName, false, undepFuture);
	}


	private void innerUndeployService(String serviceName, boolean containerClosing, Future<Void> undepFuture) {
		if(!services.containsKey(serviceName)){
			undepFuture.complete();
			return;
		}
			
		Deployment deployment = services.get(serviceName);
		String deploymentId = deployment.deploymentID();
		
		Vertx appVertx = srvVertxs.get(serviceName);		
		appVertx.undeploy(deploymentId,res -> {
			if(res.succeeded()){
				if(containerClosing){
					undepFuture.complete();	
				}else{
				
					boolean isolationJar = false;
					int pos = -1;
					for(int i=0;i<serviceDeploymentList.size();i++){
						String name = OtoCloudServiceFactory.getServiceName(serviceDeploymentList.getJsonObject(i).getString("name"));
						if(serviceName.equals(name)){
							isolationJar = true;
							pos = i;
							break;
						}			
					}
					if(pos >= 0){
						serviceDeploymentList.remove(pos);
					}
					
					if(isolationJar){
						Future<Void> unLoadFuture = Future.future();
						OtoCloudServiceImpl.unLoadComponentJar(appVertx, deployment, unLoadFuture);
						unLoadFuture.setHandler(unLoadRet -> {
				    		if(unLoadRet.failed()){
				    			Throwable err = unLoadRet.cause();
				    			logger.error(err.getMessage(), err);
				    			//err.printStackTrace();    
				    		}
				       	});			
					}
					
					services.remove(serviceName);
					srvVertxs.remove(serviceName);
					logger.info("service: [" + serviceName + "] undeploy!");
					
					
					String containerCfgFile = OtoCloudDirectoryHelper.getConfigDirectory() + "otocloud-container.json";	
					Buffer containerCfgFileBuf = JsonUtil.writeToBuffer(containerCfg);
	
					vertx.fileSystem().writeFile(containerCfgFile, containerCfgFileBuf, saveRet->{
						if(saveRet.succeeded()){
							
						}else{
							saveRet.cause().printStackTrace();
						}
					});
					
					undepFuture.complete();			
				}
			}else{					
               	Throwable err = res.cause();
               	logger.error(err.getMessage(), err);
               	//err.printStackTrace();
            	//logger.error(err.getMessage(), err);
               	undepFuture.fail(err);
    		}
		});
	}
	
	
	//卸载管理组件
	@Override
	public void undeployManageComponent(String serviceName, Future<Void> undepFuture) {
		if(!manageComponents.containsKey(serviceName)){
			undepFuture.complete();
			return;
		}
			
		Deployment deployment = manageComponents.get(serviceName);
		String deploymentId = deployment.deploymentID();		
		
		vertx.undeploy(deploymentId,res -> {
			if(res.succeeded()){
				
				boolean isolationJar = false;
				int pos = -1;
				for(int i=0;i<manageComponentList.size();i++){
					String name = OtoCloudServiceFactory.getServiceName(manageComponentList.getJsonObject(i).getString("name"));
					if(serviceName.equals(name)){
						isolationJar = true;
						pos = i;
						break;
					}			
				}
				if(pos >= 0){
					manageComponentList.remove(pos);
				}
				
				if(isolationJar){
					Future<Void> unLoadFuture = Future.future();
					OtoCloudServiceImpl.unLoadComponentJar(vertx, deployment, unLoadFuture);
					unLoadFuture.setHandler(unLoadRet -> {
			    		if(unLoadRet.failed()){
			    			Throwable err = unLoadRet.cause();
			    			logger.error(err.getMessage(), err);
			    			//err.printStackTrace();    
			    		}
			       	});			
				}
				
				manageComponents.remove(serviceName);
				logger.info("service: [" + serviceName + "] undeploy!");				
				
				String containerCfgFile = OtoCloudDirectoryHelper.getConfigDirectory() + "otocloud-container.json";	
				Buffer containerCfgFileBuf = JsonUtil.writeToBuffer(containerCfg);

				vertx.fileSystem().writeFile(containerCfgFile, containerCfgFileBuf, saveRet->{
					if(saveRet.succeeded()){
						
					}else{
						saveRet.cause().printStackTrace();
					}
				});
				
				undepFuture.complete();				
			}else{					
               	Throwable err = res.cause();
               	logger.error(err.getMessage(), err);
               	//err.printStackTrace();
            	//logger.error(err.getMessage(), err);
               	undepFuture.fail(err);
    		}
		});
	}
	
	//在JAVA虚拟机外面配置
/*	public static void config(String logConfigFile) {
		//logging閰嶇疆
		String logCfgFilePath = "file:/" + OtoCloudServiceImpl.getConfigDirectory() + logConfigFile; //log4j2.xml"; 
		logger.info(logCfgFilePath);
		System.setProperty("log4j.configurationFile", logCfgFilePath);		
		System.setProperty(LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.SLF4JLogDelegateFactory");	

	}*/
	
	
    public static OtoCloudServiceContainer internalMain(String logConfigFile, boolean needCluster)
    {   	
    	//config("log4j2.xml");
    	//config(logConfigFile);
    	
    	OtoCloudServiceContainerImpl serviceContainer = new OtoCloudServiceContainerImpl(needCluster);
    	
    	//响应进程退出
/*    	Runtime runtime = Runtime.getRuntime();  
    	Thread thread = new Thread(new ContainerShutDownListener(serviceContainer));  
    	runtime.addShutdownHook(thread);  */
    	
    	Future<Void> initFuture = Future.future();
    	serviceContainer.init(initFuture);
    	initFuture.setHandler(initRet -> {
    		if(initRet.succeeded()){   
    			Future<Void> runFuture = Future.future();
    			//杩愯
    			serviceContainer.run(runFuture);
    			runFuture.setHandler(runRet -> {
    	    		if(runRet.succeeded()){   
    	    			System.out.println("running...");	            			
    	    		}else{
    	    			Throwable err = runRet.cause();
    	    			System.err.println("run failed" + err);
    	    		}
    	       	});
    			          			
    		}else{
    			Throwable err = initRet.cause();
    			System.err.println("initialize failed" + err);
    		}
       	});
    	
    	return serviceContainer;
    }

    public static OtoCloudServiceContainer internalMain( boolean needCluster )
    {
    	//boolean needCluster = false;
/*    	if(args != null && args.length > 0){
    		needCluster = Boolean.parseBoolean(args[0]);
    	}*/
    	return internalMain("log4j2.xml", needCluster);
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean needCluster() {
		// TODO Auto-generated method stub
		return clusterEnabled;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public JsonObject getClusterConfig() {		
		return clusterCfg;
	}

	@Override
	public VertxInstancePool getVertxInstancePool() {		
		return appVertxInstPool;
	}

	@Override
	public JsonObject getVertxOptins() {		
		return vertxOptionsCfg;
	}

	@Override
	public JsonObject getMavenConfig() {
		return mavenCfg;
	}

/*	@Override
	public ClassLoader getServiceClassLoader(String serviceName) {
		Deployment deployment = services.get(serviceName);
		Vertx appVertx = srvVertxs.get(serviceName);		
		return OtoCloudServiceImpl.getClassLoader(appVertx, deployment);
	}*/

/*	@Override
	public ClassLoader getComponentClassLoader(String serviceName, String compName) {		
		Deployment deployment = services.get(serviceName);
		Vertx appVertx = srvVertxs.get(serviceName);	
		Set<Verticle> apps = ((VertxImpl)appVertx).getDeployment(deployment.deploymentID()).getVerticles();
		Iterator<Verticle> it = apps.iterator();
		Verticle vertObj = it.next();
		if(vertObj instanceof AppServiceEngine){
			AppServiceEngine appEngine = (AppServiceEngine)vertObj;
			
		}else{
			
		}		

	}*/


}
