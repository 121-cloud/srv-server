package otocloud.service.container;

/**
 * TODO: DOCUMENT ME!
 * @date 2015骞�9鏈�27鏃�
 * @author lijing@yonyou.com
 */
public class App 
{
    public static void main( String[] args )
    {  	
/*    	try{
    		System.out.println("enter key:");
    		int c;
    		c = System.in.read();
    	}catch(Exception ex){
    		
    	}    	*/
    	
    	//System.setProperty("jaxp.debug","TRUE");
    	
    	System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
   	
    	OtoCloudServiceContainerImpl.internalMain(true);    	
    	
    }   

    
}
