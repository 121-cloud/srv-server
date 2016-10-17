package otocloud.service.container;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ContainerShutDownListener implements Runnable {
	private Logger logger = LoggerFactory.getLogger(this.getClass().getName());
	
	private OtoCloudServiceContainerImpl serviceContainer;

	public ContainerShutDownListener(
			OtoCloudServiceContainerImpl serviceContainer) {
		this.serviceContainer = serviceContainer;
	}

	@Override
	public void run() {
		
		logger.info("container ShutDown!");
		
		Future<Void> stopFuture = Future.future();
		serviceContainer.stop(stopFuture);
		stopFuture.setHandler(initRet -> {
			if (stopFuture.succeeded()) {				
			} else {
				Throwable err = initRet.cause();
				logger.error(err.getMessage(), err);				
			}
		});
		

	}
}
